package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.block.jelly.JellyType;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.faction.FactionAesthetics;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveFootprint;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * What a nuke does to a NORMAL or NETHER hive: it converts rather than kills.
 * <h2>The shape of it</h2> The hive survives as a place and loses everything that made it a hive of its old strain. Its
 * people die, its eggs become ordnance, its two jelly pools fuse into one that will never be topped up again, and it
 * walks out of its lineage into one of its own. What is left is a fortress that cannot grow, cannot breed and burns
 * down to nothing - the white dwarf.
 * <h2>Order is load-bearing</h2> The reserve snapshot MUST be taken before the lineage changes.
 * {@code removeVariantMismatches} PURGES entries that do not match the location's variant rather than converting them,
 * so the instant this location reads as irradiated every normal or nether reserve entry is one tick away from deletion.
 * Snapshot, re-home, purge the husk, re-add the converted equivalents.
 */
public final class NukeConversion {

    private NukeConversion() {
        throw new UnsupportedOperationException();
    }

    /** Converts a normal or nether hive in place. Returns false if there was nothing to convert. */
    public static boolean convert(ServerLevel level, HiveLocation location, LineageFactionData oldLineage) {
        // Gathered BEFORE anything is torn down - the ledgers this reads live on the location and the territory
        // empties out over the next few lines.
        var culprits = NukeStrike.resolveAllCulprits(level, location);

        var reserveSnapshot = snapshotReserves(location);

        killEveryoneHome(level, location);
        convertEggs(level, location);
        mergeJellyPools(location);
        convertVats(level, location);

        if (!rehomeToFreshIrradiatedLineage(level, location, oldLineage)) {
            return false;
        }

        restoreReservesAsIrradiated(location, reserveSnapshot);
        evictOutsiders(level, location);
        IrradiatedRepopulation.repopulate(level, location);

        announceBirth(level, location);

        // [stated] this line plays EVERY time a hive converts, not once per world like the other three strains -
        // a hive being made is a repeatable event, and each one is worth hearing about. The warden's emergence is
        // its signature.
        com.alien.common.gameplay.entity.living.alien.Alien.announceStrainArrival(
            level,
            AlienVariant.IRRADIATED,
            "Your actions have had unintended and deadly consequences for your world..."
        );

        // Two days from now, the thing the blast made comes looking for everyone who made it.
        IrradiatedBirthRaid.schedule(level, location, culprits);

        Alien.LOGGER.info(
            "Nuke: hive {} converted to irradiated; {} reserve stacks carried over, jelly pool {}",
            location.id().value(),
            reserveSnapshot.size(),
            location.royalJelly()
        );
        return true;
    }

    /**
     * Anything of this hive's left standing OUTSIDE the territory stops being its member.
     * <p>
     * [stated] "That is no longer their hive." Unlike the aberrant case this is NOT free: there the location DIED and
     * LocationDeathHandler dropped the per-location faction for us. A converting location survives, so a specimen
     * penned two hundred blocks away would otherwise stay on the roster of a hive that is now a different strain
     * entirely - and would count against its caps.
     * <p>
     * They keep LINEAGE membership of the hive they came from, exactly as the aberrant survivors do, so they re-home or
     * drift as free agents rather than vanishing.
     */
    private static void evictOutsiders(ServerLevel level, HiveLocation location) {
        var locationFaction = Alien.MOD.factions().get(location.id().value());

        if (locationFaction == null) {
            return;
        }

        var evicted = 0;
        for (var members : location.loadedMembersByType().values()) {
            for (var memberId : new ArrayList<>(members)) {
                var entity = level.getEntity(memberId);
                if (entity == null) {
                    continue;
                }

                if (HiveFootprint.contains(location, entity.getX(), entity.getZ())) {
                    continue;
                }

                locationFaction.membership()
                    .removeMember(
                        com.blib.api.common.faction.v1.FactionMember.entity(entity)
                    );
                evicted++;
            }
        }

        if (evicted > 0) {
            Alien.LOGGER.info("Nuke: {} member(s) orphaned outside {}", evicted, location.id().value());
        }
    }

    /**
     * Tells the world what was just made, every single time.
     * <p>
     * The three genuine strain leaks announce themselves ONCE PER WORLD from
     * {@code Alien.getStrainLeakMessageForVariant} - the first nether xenomorph ever seen is news, the hundredth is
     * not. A conversion is different: it is an EVENT with a cause, and it can happen again tomorrow to a different
     * hive, so it is announced here on every occurrence.
     * <p>
     * Styled to match those three - the strain's own colour, italic, server-wide - but with the queen's scream over it.
     * Worth knowing the other three play NO sound at all; this is the only strain message that does.
     */
    private static void announceBirth(ServerLevel level, HiveLocation location) {
        var line = net.minecraft.network.chat.Component
            .literal("Your actions have had unintended and deadly consequences for your world...")
            .withStyle(
                com.alien.common.data.AlienVariantTypes.IRRADIATED.chatColor(),
                net.minecraft.ChatFormatting.ITALIC
            );

        for (var player : level.players()) {
            player.sendSystemMessage(line);
        }

        level.playSound(
            null,
            location.centerPos(),
            com.alien.common.registry.init.AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(),
            net.minecraft.sounds.SoundSource.HOSTILE,
            16.0F,
            0.7F
        );
    }

    /** Counts per stored type, taken BEFORE the variant flips. See the class note on ordering. */
    private static LinkedHashMap<EntityType<?>, Integer> snapshotReserves(HiveLocation location) {
        var snapshot = new LinkedHashMap<EntityType<?>, Integer>();
        var reserves = location.localReserves();

        for (var type : reserves.getAvailableEntityTypes()) {
            var count = reserves.getCount(type);
            if (count > 0) {
                snapshot.put(type, count);
            }
        }

        return snapshot;
    }

    /**
     * Everyone standing in the territory dies, queen included.
     * <p>
     * Only those HOME: anything of this hive's outside the square is orphaned rather than killed, which is what
     * protects a specimen someone captured and dragged off. Killed rather than discarded so loot tables fire.
     */
    private static void killEveryoneHome(ServerLevel level, HiveLocation location) {
        for (var members : location.loadedMembersByType().values()) {
            for (var memberId : new ArrayList<>(members)) {
                if (!(level.getEntity(memberId) instanceof LivingEntity member) || !member.isAlive()) {
                    continue;
                }

                if (!HiveFootprint.contains(location, member.getX(), member.getZ())) {
                    continue;
                }

                member.hurt(member.damageSources().explosion(null, null), Float.MAX_VALUE);
            }
        }
    }

    /** Every egg still standing becomes ordnance. Royal eggs lose the crown - there is no royal irradiated line. */
    private static void convertEggs(ServerLevel level, HiveLocation location) {
        var box = HiveFootprint.column(location, level.getMinBuildHeight(), level.getMaxBuildHeight());

        for (var egg : level.getEntitiesOfClass(Ovomorph.class, box)) {
            egg.convertToIrradiated(level);
        }
    }

    /**
     * [stated] "if there is 3000 royal and 2000 scourge jelly stored then you now have 5000 irradiated."
     * <p>
     * IMPLEMENTATION NOTE: there is no separate irradiated pool field. The merged total lives in {@code royalJelly} and
     * {@code scourgeJelly} is zeroed, so for an IRRADIATED location {@code royalJelly()} IS the irradiated pool. That
     * matches the stated rule that anything which used royal or scourge simply uses irradiated instead - one pool, one
     * currency - and avoids a new persisted field. Every cost check for an irradiated hive reads royalJelly.
     */
    private static void mergeJellyPools(HiveLocation location) {
        location.setRoyalJelly(location.royalJelly() + location.scourgeJelly());
        location.setScourgeJelly(0);
    }

    /**
     * Vats become irradiated and lose half, then are left alone forever - they are loot, not infrastructure. [stated]
     * "they dont refill from the reserve and the hive doesnt consume the vats."
     */
    private static void convertVats(ServerLevel level, HiveLocation location) {
        var built = new java.util.HashSet<>(location.structureRoleByChunk().keySet());
        built.addAll(location.structurePieceByChunk().keySet());

        for (var chunkPos : built) {
            if (!level.isLoaded(chunkPos.getWorldPosition())) {
                continue;
            }

            for (var blockEntity : level.getChunk(chunkPos.x, chunkPos.z).getBlockEntities().values()) {
                if (!(blockEntity instanceof JellyVatBlockEntity vat)) {
                    continue;
                }

                if (vat.getFillLevel() > 0) {
                    vat.setFillLevel(vat.getFillLevel() / 2);
                }

                vat.commitType(JellyType.IRRADIATED);
            }
        }
    }

    /**
     * The island. A fresh lineage of exactly one location, variant IRRADIATED.
     * <p>
     * [stated] "its basically its own island in a sea of hives" - it leaves its old lineage, stops counting against an
     * empress's cap, and its former kin will kill it on sight. Mirrors the mint sequence in HiveDebugCommands.
     */
    private static boolean rehomeToFreshIrradiatedLineage(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData oldLineage
    ) {
        var variantFaction = VariantFactionRegistry.getOrCreate(AlienVariant.IRRADIATED);
        var lineageId = LineageIds.create();
        var lineageFaction = Alien.MOD.factions().getOrCreate(lineageId, AlienFactionDataTypes.LINEAGE);

        if (!(lineageFaction.data() instanceof LineageFactionData lineage)) {
            Alien.LOGGER.error("Nuke: could not mint an irradiated lineage for {}", location.id().value());
            return false;
        }

        FactionAesthetics.applyDefaults(lineageFaction, AlienVariant.IRRADIATED, FactionAesthetics.Tier.LINEAGE);
        lineage.setFactionId(lineageId);

        var variantData = variantFaction.data();
        var lineageNumber = variantData != null ? variantData.allocateLineageNumber() : 0L;
        lineage.setLineageNumber(lineageNumber);
        lineageFaction.setName(
            com.alien.common.gameplay.hive.faction.FactionNaming.forLineage(AlienVariant.IRRADIATED, lineageNumber)
        );

        lineage.setVariant(AlienVariant.IRRADIATED);
        lineage.setParentVariantFactionId(variantFaction.id());
        lineage.setDimension(level.dimension());

        oldLineage.removeLocation(location.id());
        location.setLineageFactionId(lineageId);
        lineage.addLocation(location);

        return true;
    }

    /**
     * Re-adds the snapshot as irradiated equivalents, dropping anything with no irradiated form.
     * <p>
     * WHAT GETS DROPPED IS EXACTLY THE BREEDING PIPELINE: adolescents, chestbursters, their predalien versions, the
     * ovipositor, and boilers. [stated] a boiler cannot exist irradiated at all - both routes to one run through
     * breeding, and the second needs radiation sickness on a burster, which this strain is immune to. So none of these
     * are a loss; they are castes the strain has no way to possess.
     */
    private static void restoreReservesAsIrradiated(
        HiveLocation location,
        LinkedHashMap<EntityType<?>, Integer> snapshot
    ) {
        var reserves = location.localReserves();
        reserves.removeVariantMismatches(AlienVariant.IRRADIATED);

        for (var entry : snapshot.entrySet()) {
            var converted = irradiatedEquivalent(entry.getKey());
            if (converted == null) {
                continue;
            }

            reserves.tryAdd(converted, entry.getValue());
        }
    }

    /**
     * Finds the caste a type belongs to, then that caste's irradiated member. Null when the strain has no such form.
     */
    private static @org.jetbrains.annotations.Nullable EntityType<?> irradiatedEquivalent(EntityType<?> type) {
        for (var casteTag : CASTE_TAGS) {
            if (!type.builtInRegistryHolder().is(casteTag)) {
                continue;
            }

            return CasteResolver.entityTypeForCaste(AlienVariant.IRRADIATED, casteTag);
        }

        return null;
    }

    /**
     * The caste tags a reserve entry can belong to, in resolution order. Every fighting caste has an irradiated form;
     * the breeding line deliberately does not, and falls out as null.
     */
    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> CASTE_TAGS = List.of(
        AlienEntityTypeTags.DRONES,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.RAZOR_CLAWS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.RAVAGERS,
        AlienEntityTypeTags.CARRIERS,
        AlienEntityTypeTags.BURSTERS,
        AlienEntityTypeTags.HARBINGERS,
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.CHRYSALISES,
        AlienEntityTypeTags.QUEENS,
        AlienEntityTypeTags.EMPRESSES,
        AlienEntityTypeTags.OVOMORPHS,
        AlienEntityTypeTags.FACEHUGGERS
    );
}
