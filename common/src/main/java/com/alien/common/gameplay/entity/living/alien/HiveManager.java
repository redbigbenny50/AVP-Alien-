package com.alien.common.gameplay.entity.living.alien;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.lifecycle.HiveLocationFoundingService;
import com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneResult;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.FactionMember;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.just.core.functional.option.Option;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3f;

/**
 * Per-alien hive manager. Drives:
 * <ul>
 * <li>Variant-faction membership idempotency (event-driven via {@code Alien.finalizeSpawn} + the BLib
 * {@code onEntityLoad} listener — this class only exposes the entry point).</li>
 * <li>Queen settlement detection — every tick, drives {@link QueenSettlementDetector} for queens, runs
 * {@link SpreadZoneCheck}, and hands off to {@link HiveLocationFoundingService} on success.</li>
 * <li>Lineage shed timer — periodic chunk-presence refresh + shed eligibility check per
 * {@code HIVE_REDESIGN_05_RESERVES.md} § 5.</li>
 * </ul>
 * <p>
 * Persists {@code lastInsideLineageChunkTick}. The legacy per-faction hive id has been removed in Phase 12 — there is
 * no longer a single "this alien's hive id"; alien membership is tracked entirely through BLib's faction system with
 * the variant + lineage memberships.
 */
public class HiveManager implements NBTSerializable {

    private static final String LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY = "LastInsideLineageChunkTick";

    /** Sentinel meaning "never set" — initialized lazily to the current game time on first tick. */
    private static final long UNSET_TIMESTAMP = 0L;

    private final com.alien.common.gameplay.entity.living.alien.Alien alien;

    /**
     * Most recent game tick at which this alien was observed standing in any of its lineages' claimed chunks. Used by
     * the shed timer in {@link #tryShedFromLineages(long)}. Persisted across restarts.
     */
    private long lastInsideLineageChunkTick;

    public HiveManager(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        this.alien = alien;
        this.lastInsideLineageChunkTick = UNSET_TIMESTAMP;
    }

    public void tick() {
        var level = alien.level();

        if (level.isClientSide) {
            return;
        }

        // Settlement detection: every tick. Cheap (Map lookup + counter increment) and gives responsive UX —
        // the queen settles exactly SETTLEMENT_TICKS after she stops moving.
        if (alien instanceof Queen queen) {
            tryQueenSettlement(queen, level.getGameTime());
        }

        if (alien.tickCount % (20 * 10) == 0) {
            // Variant-faction join is event-driven (Alien.finalizeSpawn + onEntityLoad listener), not tick-driven.
            // The lineage-presence refresh and shed check do still need to run periodically — they're observations
            // of the alien's chunk position over time.
            refreshLineageChunkPresence(level.getGameTime());
            tryShedFromLineages(level.getGameTime());
        }
    }

    /**
     * Drives the per-tick queen-settlement check. When {@link QueenSettlementDetector} reports the queen has stood
     * still in a chunk long enough, evaluates {@link SpreadZoneCheck} and (if permitted) hands off to
     * {@link HiveLocationFoundingService} to mint a new lineage or location.
     */
    private void tryQueenSettlement(Queen queen, long currentGameTime) {
        // Capture gate: a queen who is being chained, is fully bound, is inhibited, or is incapacitated is a captive -
        // she must NOT found or raise a queen chamber. While restrained she only grows her chained eggsack (a separate
        // containment/render state keyed off isContained(), untouched here). Capture states are independent of the
        // lifecycle phase and can apply in any phase, and the front-end phase machine is off by default (so its own
        // chained-queen freeze never runs) - hence the gate lives here on the settlement path. forget() also cancels
        // any
        // in-progress settlement timer so a queen captured mid-ritual doesn't instantly found the moment she's freed.
        // Oct 3: also during her release/arrival grace outside a hive's slab - see QueenCaptivity.
        if (!com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.mayFound(queen)) {
            QueenSettlementDetector.forget(queen.getUUID());
            return;
        }

        // Front-end life-cycle gate: a queen must finish developing -> location -> hibernation before she may settle.
        // When the phase machine is disabled this is always true, so the legacy settlement path runs unchanged.
        if (!queen.getLifecyclePhaseManager().isReadyToFound()) {
            return;
        }

        // Already founded (a registered location names her as FOUNDER) - stop settling and drop her founding aura.
        // Without this, a founder whose isReadyToFound() stays true perpetually re-banks settlement after founding,
        // re-firing the ritual and trailing particles nonstop.
        //
        // FOUNDER-SHIP, not lineage membership: a hive-born daughter queen is a member of her mother's lineage from
        // birth, and founding a NEW LOCATION inside that lineage is exactly her job - SpreadZoneCheck rule 2 and
        // HiveLocationFoundingService.foundNewLocation exist for her. The old lineage-membership test made that whole
        // expansion path unreachable: a lineage-member queen in FOUNDING_HANDOFF was forgotten every tick, stood at
        // readyToFound=true forever, and never founded ("-1 biomass" - the no-hive sentinel - was the visible symptom).
        if (hasFoundedLocation(queen)) {
            QueenSettlementDetector.forget(queen.getUUID());
            return;
        }

        // ⭐⭐ A QUEEN WHO WAS REFUSED IN PLACE STANDS DOWN - SHE DOES NOT RE-RUN THE RITUAL EVERY SETTLEMENT CYCLE.
        //
        // [stated, relayed] "i keep getting 'this hive still lives... the brood returns to its search.' every 5
        // seconds in my freebuild hive and the queen is only making black particles and no eggsack." The halt below
        // was set, but nothing on this path ever read it, so the timer banked, the refusal fired, the chat line went
        // out and the black founding dust kept trailing her - indefinitely.
        //
        // ⚠ The ONE thing still worth checking while she waits is whether the hive she stands in has an empty throne
        // she may take (build-free only) - so a hive that loses its queen mid-wait is picked up without a re-try.
        if (queen.getLifecyclePhaseManager().isHoldingHaltedFounding()) {
            QueenSettlementDetector.forget(queen.getUUID());
            if (queen.tickCount % HALTED_THRONE_CHECK_TICKS == 0) {
                com.alien.common.gameplay.hive.lifecycle.BuildFreeVacantThrone.tryTake(
                    queen,
                    HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), new ChunkPos(queen.blockPosition()))
                );
            }
            return;
        }

        // One queen per location. If she is standing in a location that already has a DIFFERENT living queen (a
        // hand-summoned second queen, or a daughter/adopted queen lingering in the parent's core), she must not
        // settle and found HERE. forget() every tick keeps her from banking a founding on the occupied claim; her
        // idle wander then drifts her off it, and the moment she steps onto free ground the gate stops firing and
        // she settles and founds her own daughter hive normally. No teleport - she simply can't found on an
        // occupied spot. Keeps every countCaste(QUEENS) check across the codebase honest (exactly one per hive).
        if (standingInQueenOccupiedLocation(queen)) {
            QueenSettlementDetector.forget(queen.getUUID());
            return;
        }

        var settlementPos = QueenSettlementDetector.observe(queen, currentGameTime);
        if (settlementPos == null) {
            // Still counting down the out-of-combat settlement timer — trail purple "founding" particles so the act of
            // settling is visible in-world. (The actual found() below is instant once the timer expires.)
            if (QueenSettlementDetector.isSettling(queen.getUUID())) {
                spawnFoundingParticles(queen);
            }
            return;
        }

        // ⚠⚠ THE GROUND UNDER HER FEET, CHECKED AT LAST. The settle path founded wherever she stopped: nothing looked
        // at liquid, nothing looked at footing, and the depth rule that would have caught an ocean surface is waived
        // by queenFoundsWhereStanding and on flat worlds. [stated] "she shouldnt found on the waters surface but if
        // she does found in an ocean have her go down until she is under the surface if possible."
        var resolvedPos = com.alien.common.gameplay.hive.lifecycle.SettlementSite.resolve(queen, settlementPos);

        if (resolvedPos == null) {
            // Deep water with no reachable seabed. Forget the banked time and let her keep walking.
            QueenSettlementDetector.forget(queen.getUUID());
            queen.getLifecyclePhaseManager().restartLocationPhase();

            return;
        }

        if (!resolvedPos.equals(settlementPos)) {
            // She was on or over water: put her on the seabed she is about to found on, so she is not left treading
            // water above her own hive. The pocket carve below dams and drains the rest.
            queen.asMob().teleportTo(resolvedPos.getX() + 0.5, resolvedPos.getY(), resolvedPos.getZ() + 0.5);
        }

        var settlementPosResolved = resolvedPos;
        var result = SpreadZoneCheck.evaluate(queen, settlementPosResolved);
        if (result instanceof SpreadZoneResult.Blocked blocked) {
            // ⭐⭐⭐ SAY WHY. THE REASON WAS BEING THROWN AWAY ON THE ONE LINE THAT NEEDED IT.
            //
            // ⚠⚠ THIS IS THE "QUEEN WON'T SETTLE" LOOP, and from the outside it is invisible: the log says she woke
            // and is "handing off to founding", then says she is entering LOCATION again, and nothing in between
            // explains the bounce. A live superflat log showed her cycling four times, drifting further from spawn
            // each pass (z 664 -> 936 -> 1096, x -88 -> -360 -> -616) with no clue as to the cause.
            //
            // SpreadZoneResult.Blocked has carried a human-readable reason the whole time - foundFromResult even
            // says "caller already knows why" - and nobody ever printed it. One line turns an inference problem into
            // a read-the-log problem.
            //
            // ⚠ Logged once per BOUNCE, not per tick: this path runs only when she actually wakes and is refused, so
            // it cannot become the sort of per-tick spam the irradiated-queen founding check had to be demoted for.
            // ⭐⭐ TELL ANYONE WATCHING WHY SHE WALKED OFF.
            //
            // [stated] "someone trying to found a new one without waking/knowing an exsiting legacy hive is there
            // knows why its moving or at least something is making it move."
            //
            // ⚠ ONLY for the sleeping-legacy case, and only to players close enough to have been watching her. Every
            // other refusal reason is diagnostic noise a player cannot act on; this one is a puzzle they CAN solve -
            // kill the old queen, wake her with a command, or inhibit her.
            if (
                // ⭐⭐ AND THE OCCUPIED CASE, WHICH IS THE ONE PLAYERS ACTUALLY HIT.
                //
                // [stated] "Have a message when they try to found in an existing hive. Like this hive isnt dead."
                //
                // ⚠⚠ A PLAYER CANNOT SEE THAT A HIVE IS STILL ALIVE. One burned a hive down, killed everything
                // inside, and reasonably concluded it was dead - but its surface parties and haulers were out in the
                // field, came home, repaired the breaches and carried on. He then spawned a queen into that claim
                // and she was culled as a rival-strain intruder, which from his side was "she died from literally
                // nothing". Nothing in the world told him the hive was still there.
                (com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.LEGACY_SLEEPER_REASON.equals(blocked.reason())
                    || blocked.reason().contains("already claimed by location"))
                    // ⚠ ONCE, not every re-try: a halted queen has already told everyone nearby why she stopped.
                    && !queen.getLifecyclePhaseManager().isFoundingInPlaceHalted()
                    // ⚠ And not at all when she is about to take this hive's empty throne instead - see below.
                    && !wouldTakeVacantThrone(queen, settlementPosResolved)
                    && queen.level() instanceof net.minecraft.server.level.ServerLevel announceLevel
            ) {
                for (
                    var nearby : announceLevel.getPlayers(
                        p -> p.distanceToSqr(queen) <= LEGACY_ECHO_ANNOUNCE_RADIUS * LEGACY_ECHO_ANNOUNCE_RADIUS
                    )
                ) {
                    nearby.displayClientMessage(
                        net.minecraft.network.chat.Component
                            .literal(
                                com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.LEGACY_SLEEPER_REASON
                                    .equals(blocked.reason())
                                        ? com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.LEGACY_SLEEPER_REASON
                                        // ⚠ A DIFFERENT MESSAGE, because it is a different fact. "Old echoes" describes
                                        // a
                                        // sleeper; this hive is awake, fed and defended, and the player needs to know
                                        // that specifically - it is the belief that it was dead that got his queen
                                        // killed.
                                        : "This hive still lives - the brood returns to its search"
                            )
                            .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE),
                        true
                    );
                }
            }

            Alien.LOGGER.info(
                "Queen lifecycle: {} was refused founding at {} - {} - re-picking an anchor",
                queen.getUUID(),
                settlementPosResolved,
                blocked.reason()
            );
            // The committed anchor is no longer foundable — a hive is too close, typically a neighbour that founded
            // during her hibernation. Re-pick a fresh anchor away from current claims and run her back through
            // LOCATION -> HIBERNATION rather than leaving her stuck on a stale spot forever.
            // 🚨🚨 DO NOT RE-PICK WHEN SHE HAS NOWHERE ELSE TO GO. THIS WAS AN INFINITE LOOP.
            //
            // In build-free "founds where placed" mode enterLocation COMMITS TO HER CURRENT POSITION, so
            // restartLocationPhase hands her straight back to the same refused spot - 128 rounds of it in one live
            // log, about once a second. She never completes the handoff, so she never establishes and NEVER LAYS AN
            // EGG. Reported as "ayo why my queen aint laying eggs".
            //
            // ⭐ Re-picking is right for an ordinary queen - she has a world to search. It is wrong when the mode has
            // already decided the answer is "here": there is no second candidate, so the retry can only repeat.
            // ⭐⭐ BUILD-FREE: IF THE HIVE SHE IS STANDING IN IS HER OWN STRAIN AND ITS THRONE IS EMPTY, SHE RULES IT.
            // She cannot found on top of it, cannot dig elsewhere and cannot be a daughter in this mode, so without
            // this the only outcome was the halt below - a queen standing in a queenless hive forever, no sack.
            if (
                com.alien.common.gameplay.hive.lifecycle.BuildFreeVacantThrone.tryTake(
                    queen,
                    HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), new ChunkPos(settlementPosResolved))
                )
            ) {
                return;
            }

            if (com.alien.common.gameplay.hive.config.BuildFreeMode.queenFoundsWherePlaced()) {
                queen.getLifecyclePhaseManager().haltFoundingInPlace(blocked.reason());
                return;
            }

            queen.getLifecyclePhaseManager().restartLocationPhase();
            return;
        }

        // ⚠ She got somewhere. Reset her surface-refusal patience so a LATER relocation - a migration, an eviction
        // by an empress - searches properly again instead of inheriting a spent counter and surface-founding on its
        // first refusal.
        queen.clearSurfaceFoundingAttempts();

        // ⚠ Dig her out BEFORE the hive exists. This is the same carve a dug hive gets on arrival at its anchor; the
        // settle path never called it, which is why a queen could found sealed in terrain. It also seals liquid at
        // the rim, so a seabed hive does not immediately re-flood.
        com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager
            .clearArrivalPocket(queen, settlementPosResolved);

        var foundedId = HiveLocationFoundingService.foundFromResult(queen, settlementPosResolved, result);

        // ⭐⭐ IF SHE WAS FED A JELLY BLOCK, THIS IS WHERE SHE BECOMES AN EMPRESS.
        //
        // ⚠⚠ THE HOOK MUST BE HERE, at the moment a hive actually exists. The forced path SENDS her digging and the
        // location is not created until she arrives, so crowning her at the moment she was fed would have produced an
        // empress standing on the surface beside a hive she had not dug yet - which is precisely the half-built shape
        // this pass exists to remove.
        if (foundedId != null && queen.level() instanceof ServerLevel foundedLevel) {
            var foundedLocation = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
                .get(foundedId);
            if (foundedLocation != null) {
                com.alien.common.gameplay.hive.empress.ForcedEmpressFounding
                    .onFounded(foundedLevel, queen, foundedLocation);
            }
        }
    }

    /**
     * True once SHE has founded: some registered location's {@code founderId} is her UUID. This is the same test the
     * inspect_queen debug command uses for its "location:" line. Faction membership is deliberately NOT the signal -
     * every hive-born alien (daughter queens included) is a member of its birth hive's lineage and location factions,
     * and a daughter queen must still be allowed to settle and found her own location. If her hive was destroyed and
     * unregistered she may found again, which is the hive-loss/recovery arc working as designed.
     */
    /** How close a player must be to be told about the echo - she has to have been visible for it to make sense. */
    private static final double LEGACY_ECHO_ANNOUNCE_RADIUS = 48.0;

    /** How often a halted queen glances at whether the hive she waits in has lost its queen. Five seconds. */
    private static final int HALTED_THRONE_CHECK_TICKS = 100;

    /**
     * Cheap pre-check for the announcement only: would {@code BuildFreeVacantThrone} plausibly seat her here? Kept
     * deliberately loose (empty seat + build-free) - a false "yes" merely skips one chat line.
     */
    private static boolean wouldTakeVacantThrone(Queen queen, net.minecraft.core.BlockPos pos) {
        if (!com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return false;
        }
        var here = HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), new ChunkPos(pos));
        return here != null && here.founderId() == null;
    }

    private static boolean hasFoundedLocation(Queen queen) {
        var uuid = queen.getUUID();
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (uuid.equals(location.founderId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the location whose claim the queen currently stands in already has a live queen member that is NOT her.
     * Membership-based (not proximity): a second queen summoned into a claim auto-joins it via finalizeSpawn, and an
     * adopted/daughter queen is a member by construction, so both show up here. Excludes herself so a queen who has
     * already joined a location she is about to found in never blocks herself.
     */
    private static boolean standingInQueenOccupiedLocation(Queen queen) {
        var here = HiveLocationRegistry.INSTANCE.getByChunk(
            queen.level().dimension(),
            new ChunkPos(queen.blockPosition())
        );
        if (here == null || !here.isAlive()) {
            return false;
        }
        var queens = here.loadedMembersByType();
        for (var entry : queens.entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var memberId : entry.getValue()) {
                if (!memberId.equals(queen.getUUID())) {
                    return true; // another queen already holds this location
                }
            }
        }
        return false;
    }

    /**
     * Near-black founding motes. Dust is recolourable (unlike the old fixed-purple spell swirl); tweak the RGB to
     * taste.
     */
    private static final DustParticleOptions FOUNDING_DUST = new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.05F), 1.0F);

    /** Black dust aura while she is actively settling, throttled so it reads as a gentle aura rather than a fog. */
    private void spawnFoundingParticles(Queen queen) {
        if (queen.tickCount % 4 != 0 || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var width = queen.getBbWidth();
        var height = queen.getBbHeight();
        serverLevel.sendParticles(
            FOUNDING_DUST,
            queen.getX(),
            queen.getY() + height * 0.6,
            queen.getZ(),
            4,
            width * 0.6,
            height * 0.5,
            width * 0.6,
            0.02
        );
    }

    /**
     * Idempotently adds this alien to its variant's BLib faction. Wired to two event hooks (per the design contract in
     * {@code HIVE_REDESIGN_01_FACTIONS.md} § 1: <em>"Every alien of variant V joins variant V's faction the first time
     * it loads on the server"</em>):
     * <ul>
     * <li>{@link com.alien.common.gameplay.entity.living.alien.Alien#finalizeSpawn} — fresh spawns (natural, spawn egg,
     * command).</li>
     * <li>BLib {@code onEntityLoad} listener in the mod entry point — chunk-load reads from disk.</li>
     * </ul>
     * <p>
     * {@code addEntity} returns false when already a member, so duplicate calls between the two hooks are free.
     * <p>
     * The variant faction is sticky: a normal-variant alien that mutates to irradiated keeps its {@code variant/normal}
     * membership. Lineage-tier eviction-on-mismatch is enforced by Phase 9's {@code LineageInvariantTask}; the variant
     * tier never evicts.
     */
    public void ensureVariantFactionMembership() {
        var variant = alien.getVariant();
        if (variant == null) {
            return;
        }

        var faction = VariantFactionRegistry.getOrCreate(variant);
        if (faction.membership().hasMember(FactionMember.entity(alien))) {
            return;
        }

        faction.membership().addEntity(alien);
    }

    /**
     * Ensures every alien gets a fresh shed-grace window the first time it ticks under hive — protects newly added
     * lineage members from being shed immediately if their {@link #lastInsideLineageChunkTick} is still the sentinel
     * value 0. After the first observation the timestamp is updated normally based on chunk presence.
     */
    private void refreshLineageChunkPresence(long currentTick) {
        if (lastInsideLineageChunkTick == UNSET_TIMESTAMP) {
            lastInsideLineageChunkTick = currentTick;
        }

        if (!alien.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            return;
        }

        var alienChunk = new ChunkPos(alien.blockPosition());
        var dimension = alien.level().dimension();
        var hit = HiveLocationRegistry.INSTANCE.getByChunk(dimension, alienChunk);
        if (hit == null) {
            return;
        }

        // Only count the chunk as "inside" if the location belongs to a lineage this alien is in.
        for (var lineageId : Alien.MOD.factions().getFactionIds(alien.getUUID())) {
            if (!LineageIds.isLineageId(lineageId)) {
                continue;
            }
            if (lineageId.equals(hit.lineageFactionId())) {
                lastInsideLineageChunkTick = currentTick;
                return;
            }
        }
    }

    /**
     * Per-lineage shed check. If this alien has stayed outside its lineage territory long enough, remove its live
     * entity and return the unit to its owning hive location reserves.
     * <p>
     * Empresses and location leaders are exempt. Playable xenomorphs (parked future direction) will be exempt via a
     * one-line {@code instanceof Player} check that's currently unreachable since {@link Alien} doesn't extend Player.
     */
    public boolean tryShedFromLineages(long currentTick) {
        // Royalty is never shed. Empresses and queens are the heart of a hive; culling a founding queen for standing
        // outside her (often tiny, new) claimed footprint deletes the hive's only royal. Empresses were already exempt;
        // queens must be too, otherwise a queen that hasn't yet settled into an ovipositor gets shed after the grace
        // window and the lineage is left queenless.
        if (
            alien.getType().is(AlienEntityTypeTags.EMPRESSES)
                || alien.getType().is(AlienEntityTypeTags.QUEENS)
        ) {
            return false;
        }

        if (!alien.isAlive() || alien.isRemoved()) {
            return false;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        if (currentTick - lastInsideLineageChunkTick < config.shedGraceTicks()) {
            return false;
        }

        var returnLocation = HiveMemberLocationResolver.reserveReturnLocation(alien);
        if (returnLocation == null) {
            return false;
        }

        var faction = Alien.MOD.factions().get(returnLocation.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return false;
        }

        if (!shouldShed(lineage, currentTick, config.minLineageAgeForShedding())) {
            return false;
        }

        if (!returnLocation.localReserves().addReturningMember(alien.getType(), 1)) {
            return false;
        }

        // Drop the alien from every location faction owned by this lineage first (preserving the
        // location subset lineage invariant); idempotent for locations the alien wasn't a member of.
        for (var location : lineage.locationsById().values()) {
            var locationFaction = Alien.MOD.factions().get(location.id().value());
            if (locationFaction != null) {
                locationFaction.membership().removeMember(FactionMember.entity(alien));
            }
        }

        // Remove from lineage membership (BLib will fire onMemberRemoved which clears
        // loadedMembersByType for us via Phase 3's hook).
        faction.membership().removeMember(FactionMember.entity(alien));

        // Despawn the entity entirely; the unit is now stored in the hive location reserves.
        alien.discard();

        return true;
    }

    private boolean shouldShed(LineageFactionData lineage, long currentTick, long minLineageAge) {
        if (lineage.locationsById().isEmpty()) {
            return false;
        }

        if (lineage.ageInTicks() < minLineageAge) {
            return false;
        }

        // Leader of any of this lineage's locations is exempt.
        var alienUuid = alien.getUUID();
        for (var location : lineage.locationsById().values()) {
            if (location.leadership().isLeader(alienUuid)) {
                return false;
            }
        }

        return true;
    }

    /** Test/debug seam: jumps the timestamp into the past so the next periodic shed check fires immediately. */
    public void debugForceShedEligible() {
        this.lastInsideLineageChunkTick = 1L;
    }

    public long lastInsideLineageChunkTick() {
        return lastInsideLineageChunkTick;
    }

    /**
     * Returns this alien's lineage faction id, if any — its hive "signature" for same-hive equality checks (e.g.
     * {@code AlienPredicates.areAliensSameHive}). Resolved via LOCATION membership first: a membership set is
     * unordered, so if an alien ever holds two lineage memberships, returning the first-encountered lineage would make
     * two genuine hivemates compare unequal and fail to recognize each other. A location membership names exactly one
     * lineage unambiguously, so it wins; a bare lineage membership is only the fallback. Mirrors
     * {@code AlienTerritoryWarSystem.lineageFor} so allegiance and same-hive checks always agree.
     */
    public Option<ResourceLocation> signature() {
        ResourceLocation lineageFallback = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(alien.getUUID())) {
            if (HiveLocationIds.isHiveLocationId(factionId)) {
                var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));
                if (location != null) {
                    return Option.some(location.lineageFactionId());
                }
            } else if (lineageFallback == null && LineageIds.isLineageId(factionId)) {
                lineageFallback = factionId; // remember, but keep looking for a location membership
            }
        }
        return lineageFallback == null ? Option.none() : Option.some(lineageFallback);
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY)) {
            this.lastInsideLineageChunkTick = compoundTag.getLong(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putLong(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY, lastInsideLineageChunkTick);
    }
}
