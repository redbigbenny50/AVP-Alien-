package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.economy.IrradiatedHiveRules;
import com.alien.common.gameplay.hive.faction.FactionAesthetics;
import com.alien.common.gameplay.hive.faction.FactionNaming;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Production-path founding for the new hive system. Two entry points:
 * <ul>
 * <li>{@link #foundNewLineage} — the queen is a forager on fresh ground. Mints a new lineage faction (parented to her
 * variant faction) and her first location.</li>
 * <li>{@link #foundNewLocation} — the queen is already in a lineage and has settled within its spread zone. Just adds
 * another location to that existing lineage.</li>
 * </ul>
 * <p>
 * Either path: claims the queen's chunk, registers the location with {@link HiveLocationRegistry}, sets
 * {@code founderId = queen.uuid}, and adds the queen to the lineage's BLib membership.
 * <p>
 * Implements the rules from {@code HIVE_REDESIGN_02_FACTION_LIFECYCLES.md} § 2 and
 * {@code HIVE_REDESIGN_03_LOCATIONS.md} § 9. Empress emergence (if the lineage now hits 2+ locations) is parked for
 * Phase 10; this phase only sets the {@code pendingEmpressEmergence} flag so Phase 10's listener can pick it up.
 */
public final class HiveLocationFoundingService {

    private HiveLocationFoundingService() {}

    /**
     * Mints a new {@link LineageFactionData} (parented to the queen's variant faction) and her first
     * {@link HiveLocation} at {@code position}. Returns the new location id.
     */
    /**
     * Whether this position is too close to an existing hive to start a SEPARATE lineage here.
     * <p>
     * ⚠ Measured in chunks, Chebyshev, against every live location in the dimension - the same distance metric the
     * territory radius uses, so the two settings are directly comparable. 0 disables the rule.
     * </p>
     */
    private static boolean isTooCloseToAnotherLineage(net.minecraft.world.level.Level level, BlockPos position) {
        var spacing = HiveLocationRegistry.INSTANCE.config().minLineageSpacingChunks();

        if (spacing <= 0) {
            return false;
        }

        var here = new net.minecraft.world.level.ChunkPos(position);

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.isAlive() || !location.dimension().equals(level.dimension())) {
                continue;
            }

            var other = new net.minecraft.world.level.ChunkPos(location.centerPos());
            var distance = Math.max(Math.abs(here.x - other.x), Math.abs(here.z - other.z));

            if (distance < spacing) {
                return true;
            }
        }

        return false;
    }

    public static @org.jetbrains.annotations.Nullable HiveLocationId foundNewLineage(Xenomorph queen, BlockPos position) {
        // Default: a founding queen raises her physical chamber. (The inhibitor used to pass false here to mint a
        // captive
        // claim with no built hive; since Oct 3 captives hold no claim, so nothing passes false today.)
        return foundNewLineage(queen, position, true);
    }

    public static @org.jetbrains.annotations.Nullable HiveLocationId foundNewLineage(
        Xenomorph queen,
        BlockPos position,
        boolean buildStructure
    ) {
        // AN IRRADIATED QUEEN NEVER FOUNDS. [stated] "she doesnt try to build a hive like how normal queens do, she
        // just exists... shes just a roaming weapon of radioactive teeth and claws." Her strain's territory belongs to
        // the LOCATION, not to her, so a homeless irradiated queen has nothing to found WITH and nothing to found FOR.
        //
        // [stated] she may still JOIN an existing irradiated hive if she finds one - that is ordinary membership, not
        // founding, and goes nowhere near this method.
        // \u2b50\u2b50 Oct 3 - A CAPTIVE NEVER FOUNDS, AND NEITHER DOES A QUEEN IN HER RELEASE GRACE OUTSIDE A SLAB.
        // [stated] "she cant start a hive, create territory, try to carve anything." Asked HERE, at the choke point
        // every founding route ends in (settlement, the royal-jelly block, forced settlement, rescue relocation), so a
        // route added later cannot forget it. See QueenCaptivity.mayFound.
        if (!QueenCaptivity.mayFound(queen)) {
            Alien.LOGGER.debug("Founding refused for queen {} - captive, downed, or in her release grace", queen.getUUID());
            return null;
        }

        if (IrradiatedHiveRules.isIrradiated(queen)) {
            // DEBUG, not INFO: this is a PERMANENT property of the strain, not an event, and her lifecycle keeps
            // re-attempting to found for as long as she is alive - a live log showed 63 identical lines in ten
            // minutes from a single queen. The reason is documented directly above; it does not need repeating
            // into the server console every attempt.
            Alien.LOGGER.debug("Founding refused for irradiated queen {} - the strain does not found", queen.getUUID());
            return null;
        }

        var level = queen.level();
        var dimension = level.dimension();
        var variant = queen.getVariant();

        // 🚨 A BRAND-NEW LINEAGE KEEPS ITS DISTANCE FROM EXISTING HIVE NETWORKS.
        //
        // ⚠⚠ EACH LINEAGE IS BOUNDED; THE NUMBER OF LINEAGES WAS NOT. Every wild queen reaching unclaimed ground mints
        // a fresh lineage with its own eight-hive allowance, so hive networks accumulated indefinitely on a
        // long-running world - a dedicated server reported 8,281 loaded chunks and 849 vents with one player online,
        // with no single hive over its limits.
        //
        // ⭐ SPACING, NOT A COUNT CAP. A hard cap makes queens silently stop founding, which reads as a bug; spacing
        // just sends her further out. This is checked BEFORE anything is minted, so a refusal costs nothing and she
        // simply keeps looking.
        //
        // ⚠ APPLIES ONLY TO NEW LINEAGES. A lineage spreading its OWN hives goes through the daughter path and is
        // unaffected, so this can never stop an established network from growing normally.
        if (isTooCloseToAnotherLineage(level, position)) {
            return null;
        }

        var variantFaction = VariantFactionRegistry.getOrCreate(variant);
        var lineageId = LineageIds.create();
        var lineageFaction = Alien.MOD.factions().getOrCreate(lineageId, AlienFactionDataTypes.LINEAGE);
        var lineageData = lineageFaction.data();

        if (lineageData == null) {
            throw new IllegalStateException("LineageFactionData was null after getOrCreate for " + lineageId);
        }

        FactionAesthetics.applyDefaults(lineageFaction, variant, FactionAesthetics.Tier.LINEAGE);
        lineageData.setFactionId(lineageId);

        // Allocate a monotonic per-variant index and name the lineage faction xenos/{variant}/lin{N}.
        var lineageNumber = variantFaction.data() != null
            ? variantFaction.data().allocateLineageNumber()
            : 0L;
        lineageData.setLineageNumber(lineageNumber);
        lineageFaction.setName(FactionNaming.forLineage(variant, lineageNumber));

        lineageData.setVariant(variant);
        lineageData.setParentVariantFactionId(variantFaction.id());
        lineageData.setDimension(dimension);
        lineageData.setFounderId(queen.getUUID());

        var location = mintLocation(queen, lineageId, position, level.getGameTime(), lineageData, buildStructure);

        // A founder heads exactly ONE lineage. A forager queen who emigrates from her birth hive to found her
        // own lineage keeps her OLD lineage membership unless we shed it here - and then lineageFor() resolves
        // her and her freshly-spawned workers to different lineages by faction-set order, so her own hive reads
        // her as a rival-lineage queen and attacks her (and her eggsack). Shed every prior lineage before the
        // join below makes her a member of the new one. Skip the lineage just minted (nothing to shed there yet).
        // ⭐ REMEMBER WHERE SHE CAME FROM, BEFORE shedPriorLineages throws it away. [stated] "i would say theres a
        // period of rememberance where they are nuetral to allow the daughter to leave and found". The prior lineage
        // is only knowable here - one line later the queen has been stripped of it.
        var parentLineageId = firstPriorLineageOrNull(queen, lineageId);
        if (parentLineageId != null) {
            lineageData.setParentLineage(parentLineageId, level.getGameTime());
        }

        shedPriorLineages(queen, lineageId);

        // Adds the queen to both the lineage faction (idempotent) and the new location faction.
        LocationMembership.join(location, queen);

        // ⭐ ARRIVAL — but ONLY for a queen a hive raised. parentLineageId is non-null exactly when she came out of
        // another lineage, which is the same test the remembrance truce keys on. A WILD queen founding her first
        // lineage is not a daughter and already has her own "Something ancient screams towards the heavens..." line;
        // announcing here too would double up on her and mislabel her as somebody's daughter.
        if (parentLineageId != null && level instanceof net.minecraft.server.level.ServerLevel arrivalLevel) {
            com.alien.common.gameplay.hive.growth.DaughterQueenAnnouncements.announceArrival(arrivalLevel, queen);
        }

        Alien.LOGGER.info(
            "Hive: queen {} founded new lineage {} at {} (variant {}) with first location {}",
            queen.getUUID(),
            lineageId,
            position,
            variant,
            location.id()
        );

        return location.id();
    }

    /**
     * Adds a new {@link HiveLocation} to an existing lineage at {@code position}. Returns the new location id. If the
     * lineage now has 2+ locations, sets the {@code pendingEmpressEmergence} flag for Phase 10.
     */
    public static @Nullable HiveLocationId foundNewLocation(
        Xenomorph queen,
        ResourceLocation lineageFactionId,
        BlockPos position
    ) {
        // Oct 3 - same gate as foundNewLineage. Its only caller (foundFromResult) already returns null for a block.
        if (!QueenCaptivity.mayFound(queen)) {
            Alien.LOGGER.debug("Founding refused for queen {} - captive, downed, or in her release grace", queen.getUUID());
            return null;
        }
        var level = queen.level();
        var faction = Alien.MOD.factions().get(lineageFactionId);

        if (faction == null || !(faction.data() instanceof LineageFactionData lineageData)) {
            throw new IllegalStateException("Lineage " + lineageFactionId + " missing or wrong type at founding time");
        }

        // ⭐⭐ IS THIS HER OWN LINEAGE, OR IS SHE BEING TAKEN IN? Asked BEFORE shedPriorLineages, because one line
        // later the answer is gone. SpreadZoneCheck rule 3 routes a queen who settled inside ANOTHER same-strain
        // lineage's spread zone through this exact method, so "founded a daughter hive" and "was adopted by a
        // neighbour" arrive here indistinguishable unless the question is asked here. They deserve different lines:
        // one is an empire growing its own, the other is an empire absorbing a stranger.
        var wasAlreadyAMember = faction.membership()
            .hasMember(com.blib.api.common.faction.v1.FactionMember.entity(queen));

        // ⭐⭐ IF SHE LEFT AS A RIVAL, HER GRACE PERIOD STARTS HERE - not when she walked out.
        //
        // ⚠ The lineage she is protected in did not exist until this moment, so the intent had to be carried across
        // the walk. Spending it here is what turns "she broke away" into three days of immunity and free member
        // production; without it the schism is a lineage of one hive standing next to an empire of eight.
        com.alien.common.gameplay.hive.empress.EmpressSchism.onFounded(level, queen.getUUID(), lineageData);

        var location = mintLocation(queen, lineageFactionId, position, level.getGameTime(), lineageData, true);

        // Shed any OTHER lineage she still belongs to, keeping only the one she is founding into. For a normal
        // daughter founding in her OWN lineage this is a no-op (she is only in that lineage). For an ADOPTION -
        // a queen who left lineage L's spread zone and settled inside M's - this sheds L so she is a clean
        // single-lineage member of M, exactly like the founder-shed on the new-lineage path. Without it she'd
        // hold both L and M and read as her own hive's enemy (the double-membership infighting bug).
        shedPriorLineages(queen, lineageFactionId);

        // Adds the queen to both the lineage faction (idempotent) and the new location faction.
        LocationMembership.join(location, queen);

        if (lineageData.activeLocationCount() >= 4 && lineageData.empressId() == null) {
            // Arm the empress-emergence hint at the SAME threshold EmpressEmergenceTask actually fires at (4+
            // locations, per the updated leadership design). This flag also pauses QueenlessMaturationTask for the
            // lineage; arming it at the old 2+ suppressed queenless maturation two hives before an empress could
            // possibly emerge.
            lineageData.setPendingEmpressEmergence(true);
        }

        // ⭐⭐ ARRIVAL, THE ORDINARY DAUGHTER PATH — and the line that actually answers "did my hive make a
        // daughter". It fires for BOTH halves of hive spread: the visible promotion AND the abstract one, because a
        // convoy-minted daughter still founds through here when she gets where she is going. announceRaised can be
        // missed by walking away; this cannot.
        if (level instanceof net.minecraft.server.level.ServerLevel arrivalLevel) {
            if (wasAlreadyAMember) {
                com.alien.common.gameplay.hive.growth.DaughterQueenAnnouncements.announceArrival(arrivalLevel, queen);
            } else {
                com.alien.common.gameplay.hive.growth.DaughterQueenAnnouncements.announceAdopted(arrivalLevel, queen);
            }
        }

        Alien.LOGGER.info(
            "Hive: queen {} founded location {} in existing lineage {} at {} (lineage now has {} locations)",
            queen.getUUID(),
            location.id(),
            lineageFactionId,
            position,
            lineageData.locationsById().size()
        );

        return location.id();
    }

    /**
     * Removes {@code queen} from every lineage faction she currently belongs to EXCEPT {@code keepLineageId}, along
     * with the matching location factions (preserving the location-subset-lineage invariant). Unlike inhibition's
     * sever, this does NOT null founder links or open rescue campaigns on the departed hive: an emigrating forager was
     * a member, not that hive's founder, so it loses a worker, not its queen.
     */
    /** The lineage this founder is walking away from, or null if she had none (a wild queen founding from scratch). */
    private static @Nullable ResourceLocation firstPriorLineageOrNull(Xenomorph queen, ResourceLocation keepLineageId) {
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (LineageIds.isLineageId(factionId) && !factionId.equals(keepLineageId)) {
                return factionId;
            }
        }
        return null;
    }

    private static void shedPriorLineages(Xenomorph queen, ResourceLocation keepLineageId) {
        var member = FactionMember.entity(queen);
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getFactionIds(queen.getUUID()))) {
            if (!LineageIds.isLineageId(factionId) || factionId.equals(keepLineageId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                var locationFaction = Alien.MOD.factions().get(location.id().value());
                if (locationFaction != null) {
                    locationFaction.membership().removeMember(member);
                }
            }
            faction.membership().removeMember(member);
            Alien.LOGGER.info(
                "Hive: founder {} shed prior lineage {} on founding new lineage {}",
                queen.getUUID(),
                factionId,
                keepLineageId
            );
        }
    }

    private static HiveLocation mintLocation(
        Xenomorph queen,
        ResourceLocation lineageFactionId,
        BlockPos position,
        long currentGameTime,
        LineageFactionData lineageData,
        boolean buildStructure
    ) {
        var locationId = HiveLocationIds.create();
        // Ceiled dimensions: pull a too-high anchor down so the full 16-block slab fits under the bedrock roof -
        // the queen may have wandered above the spawn band before founding, and stamping eats bedrock.
        if (queen.level() instanceof net.minecraft.server.level.ServerLevel foundingLevel) {
            position = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.roofSafeAnchor(foundingLevel, position);
        }
        var centerChunk = new ChunkPos(position);
        var location = new HiveLocation(
            locationId,
            lineageFactionId,
            queen.level().dimension(),
            position,
            queen.getUUID()
        );

        // Allocate the location's per-lineage index before adding so the path name reflects it.
        var locationNumber = lineageData.allocateLocationNumber();
        location.setLocationNumber(locationNumber);
        location.setLastGrowthTick(currentGameTime);
        location.setLastPassiveClaimTick(currentGameTime);

        lineageData.addLocation(location);
        HiveLocationRegistry.INSTANCE.register(location);

        HiveLocationFactionProvisioner.ensure(location, lineageData);

        // Claim the initial core through HiveLocationClaims so all three sources of truth (location set,
        // registry byChunk index, BLib territory map) stay synchronized. Direct claimedChunks().add(...)
        // would miss the BLib territory addClaim and leave the core chunks unclaimed in the UI.
        if (queen.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel)) {
                location.markEndStyleHive();
            }
            claimInitialCore(serverLevel, location, centerChunk, currentGameTime, buildStructure);
        } else {
            addInitialCoreOffline(location, centerChunk, currentGameTime);
        }

        return location;
    }

    private static void claimInitialCore(
        net.minecraft.server.level.ServerLevel level,
        HiveLocation location,
        ChunkPos centerChunk,
        long currentGameTime,
        boolean buildStructure
    ) {
        // END-STYLE: the FULL footprint is claimed at placement ([stated] "it might be best to have the hive area
        // stay the same 19x19") - there are no surface parties to grow it and no decay to shrink it, so the
        // territory the fortress will ever hold is granted whole on founding day. Other lineages' chunks are still
        // respected. No structure is stamped: an End hive builds nothing but its worker vents.
        var endStyle = location.isEndStyleHive();
        var radius = endStyle
            ? com.alien.common.gameplay.hive.structure.HiveRouter.BASE_EXTENT
            : HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk) != null) {
                    continue;
                }
                com.alien.common.gameplay.hive.growth.HiveLocationClaims.claim(
                    level,
                    location,
                    chunk,
                    currentGameTime
                );
            }
        }

        // Structure system: stamp queen-chamber roles onto the claimed core and register royal exits as frontier
        // sockets for the planner. Skipped for logical-only claims (an inhibited captive queen never builds a hive)
        // and for END-STYLE hives (no construction of any kind).
        // ⚠⚠ BUILD-FREE JOINS END-STYLE HERE, and for the same reason: no construction of any kind. This is the path
        // that logged "Queen chamber established ... royal hallways stamped" on a build-free world - the mode gated
        // the ROUTER but nothing gated FOUNDING, so every new hive still stamped its chamber and hallways before the
        // router ever got a turn.
        if (buildStructure && !endStyle && !com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.establishQueenChamber(
                level.getServer(),
                location,
                centerChunk
            );
        }
    }

    private static void addInitialCoreOffline(HiveLocation location, ChunkPos centerChunk, long currentGameTime) {
        var radius = HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                location.claimedChunks().add(chunk);
                location.chunkClaimTicks().put(chunk, currentGameTime);
            }
        }
    }

    /**
     * Convenience wrapper: looks at the {@link SpreadZoneResult} and runs the matching founding action. Returns the new
     * location id when something was minted, or null when blocked.
     */
    public static @Nullable HiveLocationId foundFromResult(Xenomorph queen, BlockPos position, SpreadZoneResult result) {
        if (result instanceof SpreadZoneResult.NewLineage) {
            return foundNewLineage(queen, position);
        }

        if (result instanceof SpreadZoneResult.NewLocation newLocation) {
            return foundNewLocation(queen, newLocation.lineageFactionId(), position);
        }

        // Blocked — caller already knows why; nothing to do.
        return null;
    }
}
