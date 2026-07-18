package com.alien.common.util;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.entity.living.alien.parasite.HuggerImmunity;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.HostCaptureTask;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem;
import com.alien.common.model.alien.Host;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.registry.tag.AlienItemTags;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AlienPredicates {

    private static final float LOW_BIOMASS_TARGET_THRESHOLD = 0.25F;

    /** How long an alien holds a retaliation grudge against something that hurt it (10s). */
    private static final int RETALIATION_GRUDGE_TICKS = 200;

    public static boolean canTarget(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        return canContinueTargeting(alien, potentialTarget)
            && isTargetThreatAllowed(alien, potentialTarget);
    }

    public static boolean canAcquireTarget(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        return canTarget(alien, potentialTarget) && alien.getSensing().hasLineOfSight(potentialTarget);
    }

    public static boolean canContinueTargeting(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        // A fully-bound queen (4+ capture chains) is subdued: she can neither acquire nor keep a combat target.
        if (alien instanceof Queen boundQueen && boundQueen.getBindManager().isFullyBound()) {
            return false;
        }
        // A host on a drone's back is CARGO, not prey. The hive spent a whole party fetching it and is carrying it
        // home to a chamber to be implanted - killing it on the way is pure self-sabotage. A tester watched a spitter
        // shoot the wolf a drone was hauling, purely because a wolf reads as a low-tier threat.
        //
        // This gates CONTINUATION as well as acquisition (canAcquireTarget runs through here), so a xenomorph that was
        // already hunting the wolf DROPS it the moment a drone picks it up. It also sits ABOVE the retaliation
        // override in isTargetThreatAllowed, which is ANDed with this - so even a captive that lashes out on the way
        // home does not get itself shot by the escort. The carrier itself stays a valid target: hitting IT is how a
        // rescue works.
        if (isCapturedHost(potentialTarget)) {
            return false;
        }
        // Target must still be valid...
        return isValidTarget(alien.getVariant(), potentialTarget)
            // AND is not an alien OR if it is an alien, is an enemy alien.
            // We add this check here because the target alien might change strain or hive membership mid-targeting.
            && (!(potentialTarget instanceof Alien targetedAlien) || areAliensEnemies(alien, targetedAlien))
            // AND, for a royal-change cocoon (a plain Mob with its strain encoded in its entity type), only a rival
            // strain may attack it -- a xenomorph never strikes its own strain's forming royal.
            && (!(potentialTarget instanceof RoyalCocoon cocoon) || alien.getVariant() != cocoon.getVariant())
            // AND, for a royal's eggsack - queen OR empress (a plain Mob riding her, so no ally shield applies) - only
            // an
            // ENEMY of that queen may attack it. Untagged, the eggsack fell through every threat tier to the
            // "low-danger prey" fallback - harmless around a rich hive, but a FRESHLY FOUNDED hive is biomass-starved,
            // so the second queen's own newly spawned workers entered prey mode and harvested her brand-new eggsack
            // (reading as "the hive attacking her"). Ally rules sit above the retaliation override, so her own side
            // can never attack it even after friendly fire; rival strains/lineages still can - hive war intact.
            // [Flag for teammate review: aggression/threat targeting flow.]
            && (!(potentialTarget instanceof Ovipositor eggsack) || isEnemyEggsack(alien, eggsack));
    }

    /**
     * An eggsack's hive identity is the ROYAL it rides: attacking it is allowed exactly when attacking HER would be.
     * The carrier is matched as any {@link Alien} (not just {@link Queen}) because the EMPRESS carries the same
     * Ovipositor entity but extends Xenomorph, not Queen - a Queen-only match would have made her eggsack untouchable
     * even for rival lineages. An orphaned eggsack (royal dead/gone) is never a target - it self-discards within a tick
     * anyway.
     */
    private static boolean isEnemyEggsack(@NotNull Alien alien, @NotNull Ovipositor eggsack) {
        return eggsack.getVehicle() instanceof Alien carrier && areAliensEnemies(alien, carrier);
    }

    /**
     * True if this entity is currently being carried off by a xenomorph as a captured host.
     * <p>
     * Deliberately routed through {@code HostCaptureTask.carriedHost}, which only ever returns a NON-alien passenger,
     * so this covers captives without also shielding an ovomorph riding an egg-hauler from a rival hive's attention.
     */
    private static boolean isCapturedHost(@NotNull LivingEntity potentialTarget) {
        // On a drone's back, on its way home.
        if (
            potentialTarget.getVehicle() instanceof Alien captor
                && HostCaptureTask.carriedHost(captor) == potentialTarget
        ) {
            return true;
        }

        // Or webbed to the wall of a host chamber, waiting for its egg. A captive is MEAT IN STORAGE, not an enemy on
        // the field - and that holds however much the hive hates what it is. A marine the hive went to the trouble of
        // dragging home got executed in its own chamber, purely because marines are a hated faction. Killing the thing
        // you captured is the same self-sabotage as the spitter shooting the wolf a drone was carrying.
        return HostParking.isParked(potentialTarget);
    }

    private static boolean isTargetThreatAllowed(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        // RETALIATION OVERRIDE: whatever the threat tiers say, something that recently hurt this alien is a
        // valid target - an iron golem beating on the queen dies, ignore-list or not. The validity/ally rules
        // in canContinueTargeting still apply on top of this, so friendly fire from the alien's own side never
        // escalates into a civil war; only the tier gating below is bypassed.
        if (
            potentialTarget == alien.getLastHurtByMob()
                && alien.tickCount - alien.getLastHurtByMobTimestamp() < RETALIATION_GRUDGE_TICKS
        ) {
            return true;
        }

        if (isAlienTarget(alien, potentialTarget)) {
            return true;
        }

        // VERMIN RULE: non-alien monsters INSIDE a hive's slab are always huntable - the hive keeps its own
        // halls clean (and turns intruding vermin into biomass) regardless of the prey tiers below. Covers
        // mobs that bypass natural-spawn suppression (event/horde mods, pre-construction cave survivors).
        if (
            potentialTarget instanceof net.minecraft.world.entity.monster.Monster
                && !com.alien.Alien.MOD_ID.equals(
                    net.minecraft.world.entity.EntityType.getKey(potentialTarget.getType()).getNamespace()
                )
        ) {
            var verminLocation = HiveLocationRegistry.INSTANCE.getByChunk(
                potentialTarget.level().dimension(),
                potentialTarget.chunkPosition()
            );
            if (verminLocation != null && verminLocation.withinSlab(potentialTarget.blockPosition().getY())) {
                return true;
            }
        }

        if (isHated(alien, potentialTarget)) {
            return true;
        }

        if (potentialTarget.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_3_HIGH_DANGER)) {
            return true;
        }

        if (potentialTarget.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_2_LOW_DANGER)) {
            return isHiveLowOnBiomass(alien) || isActiveBiomassHuntingPartyMember(alien);
        }

        if (potentialTarget.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_1_PASSIVE)) {
            return false;
        }

        // Match the old 1.21.1 aggro baseline: anything valid and not explicitly ignored/passive/high-danger is
        // treated as low danger, so modded hostile mobs still enter the biomass-gated prey pool without a data tag.
        return isHiveLowOnBiomass(alien) || isActiveBiomassHuntingPartyMember(alien);
    }

    /**
     * True when {@code alien} is a currently-materialized member of an active {@code HiveParty.BiomassHunting} party.
     * Unlike the passive hive-wide biomass gate above, this party's whole purpose is proactive THREAT_2 hunting, so its
     * members bypass {@link #isHiveLowOnBiomass} entirely rather than only engaging once the hive is already
     * struggling.
     */
    private static boolean isActiveBiomassHuntingPartyMember(@NotNull Alien alien) {
        var membership = alien.partyMembership();
        if (membership == null) {
            return false;
        }
        var location = HiveLocationRegistry.INSTANCE.get(membership.sourceLocationId());
        if (location == null) {
            return false;
        }
        for (var party : location.parties()) {
            if (party.id().equals(membership.partyId())) {
                return party instanceof com.alien.common.gameplay.hive.party.HiveParty.BiomassHunting;
            }
        }
        return false;
    }

    private static boolean isHiveLowOnBiomass(@NotNull Alien alien) {
        var location = findHomeLocation(alien);
        return location != null && isLocationLowOnBiomass(location);
    }

    /**
     * True when {@code location}'s current biomass is at or below {@link #LOW_BIOMASS_TARGET_THRESHOLD} (25%) of its
     * cap. Public so location-level callers that don't have a live {@link Alien} entity — e.g.
     * {@code SurfacePartyLifecycleTask}'s opportunistic-claim gating — can use the exact same "low biomass" definition
     * as the hive-wide threat-tier gate above, rather than maintaining a second, differently-shaped threshold.
     */
    public static boolean isLocationLowOnBiomass(@NotNull HiveLocation location) {
        var cap = BiomassIncome.biomassCap(location, HiveLocationRegistry.INSTANCE.config());
        return cap > 0 && location.biomass() <= Math.ceil(cap * LOW_BIOMASS_TARGET_THRESHOLD);
    }

    private static HiveLocation findHomeLocation(@NotNull Alien alien) {
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(alien.getUUID())) {
            if (!HiveLocationIds.isHiveLocationId(factionId)) {
                continue;
            }

            var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));

            if (location != null && location.isAlive()) {
                return location;
            }
        }

        return null;
    }

    public static boolean isAlienTarget(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        // If a target is tagged as an alien...
        return potentialTarget.getType().is(AlienEntityTypeTags.ALIENS)
            // AND target is a typed alien...
            && potentialTarget instanceof Alien potentialAlienTarget
            // AND aliens are enemies (either opposing hives or opposing strains).
            && areAliensEnemies(alien, potentialAlienTarget);
    }

    // This function is here for semantics reasons.
    public static boolean areAliensEnemies(Alien first, Alien second) {
        // Different strains always fight. Same-strain hives also fight when their lineages are not unified under the
        // same empress authority.
        return areAliensDifferentStrains(first, second)
            || AlienTerritoryWarSystem.areAlienLineagesEnemies(first, second);
    }

    private static boolean areAliensDifferentStrains(Alien first, Alien second) {
        return !Objects.equals(first.isAberrant(), second.isAberrant())
            || !Objects.equals(first.isIrradiated(), second.isIrradiated())
            || !Objects.equals(first.isNetherAfflicted(), second.isNetherAfflicted());
    }

    public static boolean isValidTarget(AlienVariant selfVariant, @NotNull LivingEntity potentialTarget) {
        // Pacifist list (bats, creepers, anything modpacks add) is data-driven via the tag.
        return !potentialTarget.getType().is(AlienEntityTypeTags.IGNORED_BY_XENOMORPHS)
            // AND the target must be alive in order for it to be killed (duh).
            && potentialTarget.isAlive()
            // AND can't attack what can't be attacked (duh).
            && potentialTarget.attackable()
            // AND can't attack immortal players.
            && (!(potentialTarget instanceof Player) || !BLibEntityPredicates.isInvulnerable(potentialTarget))
            // AND *shouldn't* attack entities with an embryo inside them.
            && (!AlienPredicates.hasEmbryo(potentialTarget) || doesTargetHaveEnemyVariantEmbryo(selfVariant, potentialTarget))
            // AND *shouldn't* attack entities with a parasite attached.
            && !AlienPredicates.isParasiteAttached(potentialTarget);
    }

    private static boolean doesTargetHaveEnemyVariantEmbryo(AlienVariant selfVariant, @NotNull LivingEntity potentialTarget) {
        return potentialTarget instanceof Host host && host.getEmbryoType()
            .isSomeAnd(
                embryoType -> AlienVariantTypes.getFor(embryoType)
                    .isSomeAnd(alienVariantType -> !Objects.equals(selfVariant, alienVariantType.variant()))
            );
    }

    public static boolean isTargetingHiveMember(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        // Mobs hold targeting behavior...
        return potentialTarget instanceof Mob mob
            // AND the target must not be null.
            && mob.getTarget() != null
            // AND the mob's target is an alien.
            && mob.getTarget() instanceof Alien targetedAlien
            // AND the mob's targeted alien is the same hive as this alien.
            && areAliensSameHive(alien, targetedAlien);
    }

    public static boolean areAliensSameHive(@NotNull Alien alien, @NotNull Alien otherAlien) {
        var hiveSignatureOption = alien.getHiveManager().signature();
        var otherHiveSignatureOption = otherAlien.getHiveManager().signature();

        // Two aliens with null/missing hives are not considered part of the same hive.
        return hiveSignatureOption.isSome()
            && otherHiveSignatureOption.isSome()
            && Objects.equals(hiveSignatureOption, otherHiveSignatureOption);
    }

    public static boolean isStandingOnResin(@NotNull LivingEntity potentialTarget) {
        var basePos = potentialTarget.blockPosition();
        var belowPos = basePos.below();
        var baseBlockState = potentialTarget.level().getBlockState(basePos);
        var belowBlockState = potentialTarget.level().getBlockState(belowPos);

        return baseBlockState.is(AlienBlockTags.RESIN) || belowBlockState.is(AlienBlockTags.RESIN);
    }

    private static boolean isHated(@NotNull Alien alien, @NotNull LivingEntity potentialTarget) {
        if (BLibEntityPredicates.isInvulnerable(potentialTarget)) {
            // If the target is immortal, then alien can't "hate" them.
            return false;
        }

        return potentialTarget.getType().is(AlienEntityTypeTags.HATED_BY_XENOMORPHS)
            || isTargetingHiveMember(alien, potentialTarget);
    }

    public static boolean hasEmbryo(Entity target) {
        return target instanceof Host host && host.getEmbryoType().isSome();
    }

    public static boolean isFreeHost(Entity parasite, Entity hostTarget) {
        return BLibEntityPredicates.isAlive(hostTarget) &&
            isHost(hostTarget) &&
            !hasEmbryo(hostTarget) &&
            !isSelfOrOtherParasiteAttached(parasite, hostTarget)
            && !hasFacehuggerResistantHelmet((LivingEntity) hostTarget)
            // A host that has just torn a hugger off its face gets 30 seconds before the next one may try. This is the
            // one choke point every route onto a face passes through - GOAP targeting, an ovomorph's hatch desire, and
            // Parasite's attach-on-touch / attach-on-hit - so gating it here covers all of them at once.
            && !HuggerImmunity.isImmune(hostTarget);
    }

    public static boolean isHost(Entity target) {
        return target.getType().is(AlienEntityTypeTags.HOSTS) &&
            BLibEntityPredicates.isAlive(target) &&
            !BLibEntityPredicates.isBaby(target) &&
            !BLibEntityPredicates.isInvulnerable(target);
    }

    public static boolean isParasiteAttached(Entity target) {
        return target.hasPassenger(passenger -> passenger.getType().is(AlienEntityTypeTags.PARASITES));
    }

    public static boolean isSelfOrOtherParasiteAttached(Entity parasite, Entity target) {
        return target.hasPassenger(
            passenger -> passenger.equals(parasite) || passenger.getType().is(AlienEntityTypeTags.PARASITES)
        );
    }

    public static boolean hasFacehuggerResistantHelmet(LivingEntity livingEntity) {
        return livingEntity.getItemBySlot(EquipmentSlot.HEAD)
            .is(AlienItemTags.FACEHUGGER_RESISTANT_HELMETS);
    }
}
