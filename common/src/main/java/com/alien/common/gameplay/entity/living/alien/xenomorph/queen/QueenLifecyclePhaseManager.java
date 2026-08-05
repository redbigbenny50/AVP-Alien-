package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.Alien;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-queen front-end life-cycle driver: developing → location → hibernation → founding-handoff (see
 * {@code AVP_Queen_Lifecycle_Design.pdf}, Part 1). Owned by {@link Queen}, ticked server-side from
 * {@link Queen#tick()}, and persisted alongside the other queen managers in
 * {@link Queen#addAdditionalSaveData}/{@code readAdditionalSaveData}.
 * <p>
 * <b>Stage 2b scope.</b> {@link QueenLifecyclePhase#DEVELOPING} → {@link QueenLifecyclePhase#LOCATION} →
 * {@link QueenLifecyclePhase#FOUNDING_HANDOFF} are live. On entering LOCATION she commits an anchor — a weighted-Y band
 * pick plus an XZ search that prefers ~16 chunks from existing claims (reusing the exact spacing gate
 * {@code SpreadZoneCheck} enforces, so the spot passes founding). The {@code location_move} GOAP package then clip-digs
 * her there (this manager reconciles her {@code digging} state — noPhysics/noGravity/fire-immunity — each tick); on
 * arrival she carves a small breathable pocket and hands off, founding at the committed anchor. {@code HIBERNATION}
 * arrives in Stage 3, slotting between LOCATION and FOUNDING_HANDOFF (replacing the arrival pocket with the 6x6 clear +
 * 3-day sleep).
 * <p>
 * <b>Single disable point.</b> {@link #isEnabled()} reads the {@code queenFrontEndPhasesEnabled} hive-config flag, so
 * the whole front-end can be toggled at runtime (in-game config inspector / {@code HiveConfigUpdateHandler}) or shipped
 * off by default. When disabled, {@link #isReadyToFound()} returns {@code true} unconditionally and {@link #tick()}
 * no-ops, so the queen founds via the existing path exactly as she does today. No other code changes.
 * <p>
 * <b>Hand-off gate.</b> The only touch-point in the working founding system is {@code HiveManager#tryQueenSettlement},
 * which early-returns while {@link #isReadyToFound()} is {@code false}. That is what holds a
 * developing/locating/hibernating queen back from settling.
 */
public class QueenLifecyclePhaseManager implements NBTSerializable {

    /** 5 minutes after adulting, per the design doc's developing window. */
    public static final int DEVELOPING_DURATION_TICKS = 5 * 60 * 20;

    /** Sleep length before she wakes and founds: 3 Minecraft days. Zeroed via the hibernation_skip debug command. */
    public static final int HIBERNATION_DURATION_TICKS = 3 * 24000;

    /** Damage at or above which a hit rouses her from hibernation; below this (a stray arrow) she sleeps through it. */
    /** One hit this hard and she is up, no matter what she was doing. */
    public static final float HIBERNATION_DISTURBANCE_DAMAGE = 6.0F;

    /**
     * Or this much damage TOTAL inside the window below. Sustained chipping rouses her just as surely as one heavy blow
     * - it just takes commitment.
     */
    public static final float SUSTAINED_DISTURBANCE_DAMAGE = 12.0F;

    /** The accumulator bleeds off at this much per second, so a slow drip never adds up to anything. */
    private static final float DISTURBANCE_DECAY_PER_SECOND = 2.0F;

    private float disturbanceAccumulator;

    /** She must stay clear of threats this long (out of combat, no survival player in her chunk) before resettling. */
    private static final int HIBERNATION_CALM_TICKS = 10 * 20;

    /** How close to the anchor (blocks) counts as home when walking back after a disturbance. */
    private static final double RETURN_ARRIVAL_RADIUS = 2.0;

    /**
     * Sub-state within HIBERNATION (Stage 3b). ASLEEP: held at the anchor, sleep clock running. DEFENDING: roused by
     * damage, fighting under normal AI, clock paused. RETURNING: walking back to the anchor after the threat clears.
     */
    public enum HibernationActivity {
        ASLEEP,
        DEFENDING,
        RETURNING
    }

    // ---- Weighted target-Y bands (AVP_Queen_Lifecycle_Design.pdf, location phase). Config candidates for later. ----
    private static final int COMMON_Y_MIN = -15;

    private static final int COMMON_Y_MAX = 20;

    private static final int RARE_Y_MIN = -50;

    private static final int RARE_Y_MAX = -25;

    private static final int VERY_RARE_Y_MIN = 25;

    private static final int VERY_RARE_Y_MAX = 45;

    private static final int COMMON_WEIGHT = 70;

    private static final int RARE_WEIGHT = 22;

    private static final int VERY_RARE_WEIGHT = 8;

    /** How far out (in chunks) the anchor search looks for a far-enough spot before giving up and settling in place. */
    private static final int MAX_ANCHOR_SEARCH_RADIUS_CHUNKS = 48;

    /** Arrival radius around the anchor. Must match {@code LocationMoveSensors.AT_ANCHOR_RADIUS}. */
    private static final double ARRIVAL_TOLERANCE = 2.0;

    private static final double ARRIVAL_TOLERANCE_SQ = ARRIVAL_TOLERANCE * ARRIVAL_TOLERANCE;

    /** Anti-suffocation pocket carved on arrival (horizontal radius / height up from the anchor floor). */
    private static final int ARRIVAL_POCKET_RADIUS = 2;

    private static final int ARRIVAL_POCKET_HEIGHT = 5;

    private static final String PHASE_TAG = "lifecyclePhase";

    private static final String DEVELOPING_TICKS_REMAINING_TAG = "lifecycleDevelopingTicksRemaining";

    private static final String ANCHOR_TAG = "lifecycleAnchor";

    private static final String HIBERNATION_TICKS_REMAINING_TAG = "lifecycleHibernationTicksRemaining";

    private static final String WILD_SPAWNED_TAG = "lifecycleWildSpawned";

    /** How often a hibernating WILD queen checks whether a nearby lineage should adopt her. */
    private static final int WILD_ADOPTION_CHECK_INTERVAL_TICKS = 200;

    /** A hive within this many chunks of a sleeping wild queen wakes and adopts her (matches the spawn margin). */
    private static final int WILD_ADOPTION_RANGE_CHUNKS = 32;

    /** Adoption only proceeds while the lineage is under its member-hive cap (mirrors maxLocationsPerLineage = 8). */
    private static final int WILD_ADOPTION_LINEAGE_CAP = 8;

    /** Players within this range hear the awakening broadcast when a wild queen's sleep runs out. */
    private static final double WILD_AWAKENING_BROADCAST_RANGE = 256.0;

    /**
     * [stated] "if it is full and its the same strain she should have the chance to wake up and relocate then found." A
     * sleeping wild queen whose only same-strain neighbours are at their hive cap does not lie there forever - she
     * wakes and moves out of that lineage's reach to found her own. She must clear the SAME range that would have let
     * them adopt her, or she would simply re-qualify for a lineage that still cannot take her.
     */
    private static final int CAPPED_LINEAGE_FLEE_CLEARANCE_CHUNKS = WILD_ADOPTION_RANGE_CHUNKS;

    /**
     * [stated] "a force spawning queen should follow the same rules causing the sleeping queen to awaken and then
     * leave... this would apply to summoned or spawn egg queens." The reaction lives on HER, not on the spawner, so it
     * covers every arrival the same way: the first-queen guarantee, a spawn egg, a summon, a queen walking past.
     * Blocks, not chunks - an entity scan wants a box.
     */
    private static final double RIVAL_QUEEN_WAKE_RANGE_BLOCKS = 128.0;

    /** How far she puts between herself and the queen that woke her (chebyshev chunks). */
    private static final int RIVAL_QUEEN_FLEE_CLEARANCE_CHUNKS = 16;

    private final Queen queen;

    private QueenLifecyclePhase phase;

    private int developingTicksRemaining;

    private int hibernationTicksRemaining;

    /**
     * Sub-state within HIBERNATION (Stage 3b). Transient — a reload simply restarts her ASLEEP, which is acceptable.
     */
    private HibernationActivity hibernationActivity = HibernationActivity.ASLEEP;

    /** True for naturally spawned (wild) queens: enables the adoption check and the awakening broadcast. */
    private boolean wildSpawned;

    /** Ticks she has been threat-free while DEFENDING; at {@link #HIBERNATION_CALM_TICKS} she heads back. Transient. */
    private int disturbanceCalmTicks;

    /**
     * Set for exactly one anchor pick when something drove her off this spot (a capped lineage, a rival queen): the
     * chunk she is fleeing and how far she must get from it. Consumed and cleared by {@link #pickAnchorChunk()}, so it
     * is deliberately transient - a reload mid-flight simply leaves her digging to the anchor she already committed.
     */
    private @Nullable ChunkPos fleeFromChunk;

    private int fleeClearanceChunks;

    /** The committed hive anchor (chunk-center XZ + target Y) chosen in LOCATION. Null until then; never re-chosen. */
    private @Nullable BlockPos anchor;

    public QueenLifecyclePhaseManager(Queen queen) {
        this.queen = queen;
        this.phase = QueenLifecyclePhase.DEVELOPING;
        this.developingTicksRemaining = DEVELOPING_DURATION_TICKS;
    }

    /**
     * Whether the front-end phase machine is active. Backed by the {@code queenFrontEndPhasesEnabled} hive-config flag.
     */
    public static boolean isEnabled() {
        return HiveLocationRegistry.INSTANCE.config().queenFrontEndPhasesEnabled();
    }

    public QueenLifecyclePhase getPhase() {
        return phase;
    }

    /**
     * The committed anchor, or null if she has not reached LOCATION yet. Read by the Stage 2b location_move package.
     */
    public @Nullable BlockPos getAnchor() {
        return anchor;
    }

    /** Debug read-out: ticks left in the developing window (only meaningful while in DEVELOPING). */
    public int getDevelopingTicksRemaining() {
        return developingTicksRemaining;
    }

    /** Debug read-out: ticks left in the hibernation sleep (only meaningful while in HIBERNATION). */
    public int getHibernationTicksRemaining() {
        return hibernationTicksRemaining;
    }

    /** Debug: zero the sleep timer so a hibernating queen wakes and founds on the next tick. No-op otherwise. */
    public void skipHibernation() {
        if (phase == QueenLifecyclePhase.HIBERNATION) {
            hibernationTicksRemaining = 0;
        }
    }

    /**
     * Whether the queen may hand off to the existing founding system. True only once she has completed the front-end
     * (terminal {@code FOUNDING_HANDOFF}), or always when the machine is disabled (preserving today's behaviour).
     */
    public boolean isReadyToFound() {
        return !isEnabled() || phase == QueenLifecyclePhase.FOUNDING_HANDOFF;
    }

    public void tick() {
        if (queen.level().isClientSide) {
            return;
        }

        if (queen.tickCount % 20 == 0) {
            decayDisturbance();
        }

        // Bound queens are frozen: a captured queen does not develop, locate, dig, hibernate, or hand off to founding.
        // Clear any in-progress dig so she stays physical for the bind clamp, then hold the front-end until fully
        // released. Without this she resumes the LOCATION dig on reload and soft-locks against the clamp.
        if (queen.getBindManager().hasAnyChain()) {
            if (queen.isDigging()) {
                queen.setDigging(false);
            }
            return;
        }

        // AN IRRADIATED QUEEN HAS NO FRONT-END AT ALL. [stated] "she doesnt try to build a hive like how normal
        // queens do, she just exists... shes just a roaming weapon of radioactive teeth and claws." Developing,
        // choosing an anchor, digging it out and sleeping in it are every one of them work toward a founding that
        // can never happen: HiveLocationFoundingService refuses her strain at the door.
        //
        // Left to run she completed the whole chain, reached FOUNDING_HANDOFF, and then hammered that refusal on a
        // ~10s loop for as long as she lived - a tester's log carried 63 identical lines from ONE queen inside ten
        // minutes. Freezing her here means the attempt is never made rather than made and rejected, and it also
        // spares her the pointless dig.
        //
        // She may still JOIN an existing irradiated hive - that is ordinary membership and never touches this
        // front-end. Shaped after the bound-queen guard above, including clearing a dig left mid-swing.
        if (com.alien.common.gameplay.hive.economy.IrradiatedHiveRules.isIrradiated(queen)) {
            if (queen.isDigging()) {
                queen.setDigging(false);
            }
            return;
        }

        // Safety net only: the DIG action owns the digging state (set while it performs, cleared on its finish), so she
        // is noclip *only* while actively digging — never while idle/combat movement is in control. This just clears
        // any
        // lingering flag if she has left LOCATION without the action's finish firing (e.g. an abrupt state change).
        if (queen.isDigging() && phase != QueenLifecyclePhase.LOCATION) {
            queen.setDigging(false);
        }

        if (!isEnabled()) {
            return;
        }

        if (phase == QueenLifecyclePhase.FOUNDING_HANDOFF) {
            return;
        }

        // Migration / already-established guard. A queen restored from a save that predates this system (or any queen
        // that has already founded) has no front-end to run: if she owns a live location or already has an ovipositor,
        // jump straight to the inert terminal state so we never re-run developing on an established queen.
        if (isAlreadyEstablished()) {
            // WAKE HER ON THE WAY THROUGH. A queen can acquire a location while still ASLEEP - the inhibitor is
            // exactly that case, because QueenInhibitionService mints her a personal severed claim with her as
            // founder the moment it is clamped on, which is what isAlreadyEstablished() looks for.
            //
            // The jump below is terminal and sits BEFORE the phase switch, so tickHibernation never runs again -
            // and tickHibernation is the only thing that ever clears the sleep flag on this path. Left as it was,
            // an inhibited hibernating queen lay there asleep FOREVER: nothing could wake her, and the
            // hibernation-skip debug command refused her for no longer being in HIBERNATION ([stated] tester
            // report: "inhibited queen while she was hibernating tried to wake her up", screenshot showing
            // "Nearest queen is in phase FOUNDING_HANDOFF, not HIBERNATION").
            //
            // Clearing the flag here rather than in the inhibitor covers every route into this jump, not just
            // that one. Same idiom tryAdoptIntoLineage already uses: clear the flag, then hand off.
            if (phase == QueenLifecyclePhase.HIBERNATION) {
                queen.isHibernating.set(false);
                Alien.LOGGER.info(
                    "Queen lifecycle: {} woke from HIBERNATION - she now holds a location, handing off",
                    queen.getUUID()
                );
            }

            phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
            return;
        }

        // Pause the front-end while she is still inside the molt/growth cocoon. A praetorian/crusher that just
        // metamorphosed exists as a Queen for the tail of the cocoon (destination cocoon + emerge) before her real
        // life begins; her developing clock should not start until the cocoon clears.
        if (queen.getCocoonManager().shouldRunCocoonAction()) {
            return;
        }

        switch (phase) {
            case DEVELOPING -> tickDeveloping();
            case LOCATION -> tickLocation();
            case HIBERNATION -> tickHibernation();
            case FOUNDING_HANDOFF -> { /* inert */ }
        }
    }

    private void tickDeveloping() {
        if (developingTicksRemaining > 0) {
            developingTicksRemaining--;
            return;
        }
        enterLocation();
    }

    /**
     * Sends her back through the LOCATION phase with a freshly-chosen anchor. Called when founding is blocked at
     * settlement because her long-committed anchor is no longer foundable — typically a neighbouring queen founded
     * nearby during her 3-day hibernation, so the spot that was far enough when she picked it is now too close. She
     * re-picks an anchor that clears the current spacing (via {@link #pickAnchorChunk()}), clip-digs to it, and
     * re-hibernates before trying to found again. If she is genuinely boxed in (no far-enough chunk within the search
     * radius) the re-pick returns her current chunk and she simply retries on the next cycle.
     */
    /**
     * Entry point for naturally spawned WILD queens: she takes root where she spawned and sleeps. The hibernation clock
     * only runs while her chunk is entity-ticking, so undisturbed wilderness queens sleep indefinitely - it is
     * sustained player activity in the area that accumulates the {@link #HIBERNATION_DURATION_TICKS} that finally wakes
     * her. A solid hit rouses her to defend as usual; a nearby lineage may adopt her (see {@code tryWildAdoption}).
     */
    /**
     * RESCUE AFTERMATH, path B (rescuers belong to a lineage): the freed queen joins their lineage as a DAUGHTER QUEEN
     * through the same adoption recipe wild hibernating queens use - faction resolved, lineage cap enforced, membership
     * joined, straight to FOUNDING_HANDOFF so she founds her own location UNDER that lineage. Returns false (and
     * changes nothing) when the lineage is at its location cap - the caller then releases the rescuers home and the
     * queen strikes out on her own.
     */
    public boolean tryAdoptIntoLineage(com.alien.common.gameplay.hive.location.HiveLocation location) {
        if (!isEnabled()) {
            return false;
        }
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction == null
                || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
                || lineage.locationsById().size() >= WILD_ADOPTION_LINEAGE_CAP
        ) {
            return false;
        }
        com.alien.common.gameplay.hive.faction.LocationMembership.join(location, queen);
        queen.isHibernating.set(false);
        phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
        Alien.LOGGER.info(
            "Queen lifecycle: freed queen {} adopted by lineage {} as a daughter queen — rescue debt honored",
            queen.getUUID(),
            location.lineageFactionId()
        );
        return true;
    }

    /**
     * RESCUE AFTERMATH, paths A/B-overflow (no lineage to join): the freed queen strikes out for a den of her own
     * through the SAME locating machinery every queen uses - {@code enterLocation()} commits a weighted-Y anchor and
     * she digs to it, founding a fresh lineage at the end. No-op if she already holds a location or the machine is
     * disabled.
     */
    public void beginFreedRelocation() {
        if (!isEnabled() || phase == QueenLifecyclePhase.LOCATION || phase == QueenLifecyclePhase.FOUNDING_HANDOFF) {
            return;
        }
        queen.isHibernating.set(false);
        enterLocation();
    }

    public void beginWildHibernation() {
        if (!isEnabled()) {
            return;
        }
        this.wildSpawned = true;
        this.anchor = queen.blockPosition();
        this.hibernationTicksRemaining = HIBERNATION_DURATION_TICKS;
        this.hibernationActivity = HibernationActivity.ASLEEP;
        this.phase = QueenLifecyclePhase.HIBERNATION;
        queen.isHibernating.set(true);

        // A sleeping queen taking root nearby is not announced outright - but instincts notice. The italic whisper
        // in her strain's color is the quiet tier: no scream, just dread. Waking her earns the scream.
        broadcastToNearbyPlayers(
            net.minecraft.network.chat.Component
                .literal("Your instincts warn you danger is near...")
                .withStyle(com.alien.common.data.AlienVariantTypes.getFor(queen).chatColor(), net.minecraft.ChatFormatting.ITALIC)
        );
    }

    /**
     * Entry point for THE FIRST wild queen of a world: no slumber, no waiting - she founds right away where she
     * spawned, with her own genesis line as the world's hive-genesis announcement. Later wild queens use
     * {@link #beginWildHibernation()} and are discovered, not announced.
     */
    public void beginWildImmediateFounding() {
        if (!isEnabled()) {
            return;
        }
        this.wildSpawned = true;
        this.anchor = queen.blockPosition();
        this.hibernationActivity = HibernationActivity.ASLEEP;
        this.phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
        queen.isHibernating.set(false);
        broadcastToNearbyPlayers(
            net.minecraft.network.chat.Component
                .literal("Something ancient screams towards the heavens...")
                .withStyle(com.alien.common.data.AlienVariantTypes.getFor(queen).chatColor()),
            true
        );
    }

    public void restartLocationPhase() {
        if (!isEnabled()) {
            return;
        }
        queen.isHibernating.set(false);
        enterLocation();
    }

    /** Reads her situation and commits a hive anchor (chunk-center XZ + weighted target Y), then enters LOCATION. */
    private void enterLocation() {
        // END-STYLE: no digging - there is only void below the island. She founds WHERE SHE STANDS, the same shape
        // the world's first wild queen uses (anchor in place, straight to the handoff), skipping LOCATION and the
        // hibernation that follows it entirely. The player placed her deliberately; siting the island IS the
        // placement puzzle, and the fortress rises at the spot they chose.
        if (
            queen.level() instanceof ServerLevel endCheckLevel
                && com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(endCheckLevel)
        ) {
            this.anchor = queen.blockPosition();
            this.phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
            queen.isHibernating.set(false);
            Alien.LOGGER.info(
                "Queen lifecycle: {} entering FOUNDING_HANDOFF in place (end-style dimension, no dig) at {}",
                queen.getUUID(),
                anchor
            );
            return;
        }
        var chunk = pickAnchorChunk();
        var targetY = pickTargetY();
        // She digs down or settles level - never rises. If she is already at or below the rolled depth
        // (spawned below her whole band on a deep/flat world, or in a cave), there is nowhere to dig down
        // to, so anchor at her current Y and she settles in place instead of floating up to the band.
        targetY = Math.min(targetY, queen.blockPosition().getY());
        // GROUND SAFETY: the weighted band lands squarely in the deepslate lava-lake range. If the chunk is
        // loaded, walk the anchor column UP from the rolled Y (she only ever digs down, so up is always
        // reachable) until the anchor cell, its headroom, and its floor are all lava-free. Unloaded chunks are
        // handled at arrival instead (the pocket carve seals hazards with resin).
        if (
            queen.level() instanceof ServerLevel foundingLevel
                && foundingLevel.isLoaded(chunk.getWorldPosition())
        ) {
            var probe = chunk.getMiddleBlockPosition(targetY);
            int maxY = queen.blockPosition().getY();
            while (
                probe.getY() < maxY
                    && (foundingLevel.getBlockState(probe).liquid()
                        || foundingLevel.getBlockState(probe.above()).liquid()
                        || foundingLevel.getBlockState(probe.below()).liquid())
            ) {
                probe = probe.above();
            }
            targetY = probe.getY();
        }
        this.anchor = chunk.getMiddleBlockPosition(targetY);
        this.phase = QueenLifecyclePhase.LOCATION;

        Alien.LOGGER.info(
            "Queen lifecycle: {} entering LOCATION — committed anchor chunk {} target Y {} (anchor {})",
            queen.getUUID(),
            chunk,
            targetY,
            anchor
        );
    }

    private void tickLocation() {
        if (anchor == null) {
            // Defensive: a LOCATION-phase queen with no committed anchor (partial/legacy state) recomputes one.
            enterLocation();
            return;
        }

        // The location_move GOAP package clip-digs her toward the anchor; wait until she arrives.
        if (!isAtAnchor()) {
            return;
        }

        // Arrived at depth. Clear a small air pocket (floor kept at the anchor) so she doesn't suffocate, then enter
        // HIBERNATION: she sleeps at the committed anchor for HIBERNATION_DURATION_TICKS and founds on waking.
        clearArrivalPocket();
        enterHibernation();
    }

    /**
     * Begins the sleep at the committed anchor. The hibernation GOAP hold pins her here and runs the sleep animation.
     */
    /**
     * True when a hive genuinely promoted and sent this queen. Membership ALONE is not enough - finalizeSpawn
     * auto-joins any xenomorph spawned inside a claimed chunk, so a spawn-egged queen placed in territory was instantly
     * a "member" and skipped her sleep ([stated] "wild queen hibernation is still ending in 1 second"; the log's
     * "sleeping 0 ticks" was this). The player-placed flag breaks the tie: transitions never call finalizeSpawn, so a
     * promoted daughter can never carry it, while an egg or command queen always does.
     */
    private boolean wasDispatchedByAHive() {
        if (queen.isPlayerPlaced()) {
            return false;
        }
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (com.alien.common.gameplay.hive.id.HiveLocationIds.isHiveLocationId(factionId)) {
                return true;
            }
        }
        return false;
    }

    private void enterHibernation() {
        this.phase = QueenLifecyclePhase.HIBERNATION;

        // A HIVE-RAISED queen does not sleep. Hibernation is the WILD queen's bargain: she takes root alone in the
        // open with nothing to protect her, so she waits out a long vulnerable sleep before she is strong enough to
        // found. A daughter promoted inside a defended hive has already had that protection - she was raised in the
        // chamber, escorted out, and dispatched with orders - so she founds on arrival.
        //
        // This is not only pacing. The sleep clock ONLY advances while her chunk is entity-ticking, so a dispatched
        // daughter whose player wandered off would sleep forever and never found - and her mother hive would have
        // already spent the jelly, the praetorian, and one of its two lifetime daughters on her. It also made a
        // WATCHED hive spread an hour slower than an ignored one, since the unloaded path mints its daughter
        // outright.
        //
        // Zero rather than a skipped phase so the existing wake-and-hand-off path runs unchanged on the next tick.
        // NOT `wildSpawned`. That was the first attempt and it was wrong: the flag is set only on the two genuinely
        // WILD paths, so a queen placed by egg or command is false too - and she skipped the sleep entirely,
        // waking the same tick she entered. A live log caught it exactly: "entering HIBERNATION ... sleeping 0
        // ticks" followed 42ms later by "woke from HIBERNATION".
        //
        // The real question is whether a HIVE SENT HER. A daughter promoted by QueenPromotionService carries her
        // mother's location membership across the molt, so she already belongs to a hive; a wild or hand-spawned
        // queen belongs to nothing. Only the dispatched one skips - she was raised in a defended chamber and left
        // with orders, which is the whole reason she does not need to lie dormant first.
        this.hibernationTicksRemaining = wasDispatchedByAHive() ? 0 : HIBERNATION_DURATION_TICKS;
        this.hibernationActivity = HibernationActivity.ASLEEP;
        this.disturbanceCalmTicks = 0;
        queen.isHibernating.set(true);

        // Settle her onto the pocket floor (anchor Y) instead of wherever she stopped digging. She arrives within the
        // arrival radius — typically a block or two above the anchor — and the hold then zeroes her velocity, which
        // would otherwise pin her floating in the middle of the cleared pocket. Snapping her down puts her on the
        // floor.
        if (anchor != null) {
            queen.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        }

        Alien.LOGGER.info(
            "Queen lifecycle: {} entering HIBERNATION at anchor {} — sleeping {} ticks",
            queen.getUUID(),
            anchor,
            hibernationTicksRemaining
        );
    }

    /** Counts the sleep down; on completion hands off to the founding system (Stage 3a: undisturbed sleep only). */
    private void tickHibernation() {
        switch (hibernationActivity) {
            case ASLEEP -> tickHibernationAsleep();
            case DEFENDING -> tickHibernationDefending();
            case RETURNING -> tickHibernationReturning();
        }
    }

    /** ASLEEP: hold + sleep clock. Re-asserts the synced pose flag each tick (covers a queen restored mid-sleep). */
    private void tickHibernationAsleep() {
        queen.isHibernating.set(true);

        if (wildSpawned && queen.tickCount % WILD_ADOPTION_CHECK_INTERVAL_TICKS == 0) {
            // Adoption first: joining a lineage that has room always beats picking up and moving.
            if (tryWildAdoption() || tryFleeRivalQueen()) {
                return;
            }
        }

        if (hibernationTicksRemaining > 0) {
            hibernationTicksRemaining--;
            return;
        }

        queen.isHibernating.set(false);
        Alien.LOGGER.info(
            "Queen lifecycle: {} woke from HIBERNATION at anchor {} — handing off to founding",
            queen.getUUID(),
            anchor
        );

        if (wildSpawned) {
            broadcastWildAwakening();
        }

        phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
    }

    /**
     * A sleeping wild queen inside a same-strain lineage's 32-chunk reach wakes and is adopted: she joins that
     * lineage's membership (variant-gated by {@code LocationMembership.join}) and hands off to founding, so her hive
     * becomes another member of the adopting lineage instead of starting a fresh one. Lineages at their member-hive cap
     * leave her sleeping.
     */
    private boolean tryWildAdoption() {
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        var queenChunk = queen.chunkPosition();
        HiveLocation cappedNeighbour = null;

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(serverLevel.dimension())) {
                continue;
            }
            if (!java.util.Objects.equals(location.lineageVariantOrNull(), queen.getVariant())) {
                continue;
            }

            var withinRange = false;
            for (var chunk : location.claimedChunks()) {
                if (
                    Math.max(Math.abs(chunk.x - queenChunk.x), Math.abs(chunk.z - queenChunk.z)) <= WILD_ADOPTION_RANGE_CHUNKS
                ) {
                    withinRange = true;
                    break;
                }
            }
            if (!withinRange) {
                continue;
            }

            var faction = Alien.MOD.factions().get(location.lineageFactionId());
            if (
                faction == null
                    || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
            ) {
                continue;
            }
            if (lineage.locationsById().size() >= WILD_ADOPTION_LINEAGE_CAP) {
                // Her own strain, in reach, with no room for her. Remember it and keep looking - another lineage of
                // the same strain may still have a seat, and being adopted always beats moving.
                cappedNeighbour = location;
                continue;
            }

            com.alien.common.gameplay.hive.faction.LocationMembership.join(location, queen);
            queen.isHibernating.set(false);
            phase = QueenLifecyclePhase.FOUNDING_HANDOFF;
            Alien.LOGGER.info(
                "Queen lifecycle: wild queen {} adopted by lineage {} — waking to found",
                queen.getUUID(),
                location.lineageFactionId()
            );
            return true;
        }

        if (cappedNeighbour != null) {
            wakeAndFlee(
                new ChunkPos(cappedNeighbour.centerPos()),
                CAPPED_LINEAGE_FLEE_CLEARANCE_CHUNKS,
                "her own strain's lineage " + cappedNeighbour.lineageFactionId() + " is at its hive cap"
            );
            return true;
        }

        return false;
    }

    /**
     * [stated] the rival-queen half of the same rule: a queen arriving near her - forced by the first-queen guarantee,
     * dropped from a spawn egg, summoned, or simply passing through - wakes her and she leaves. Strain does not matter
     * here: two queens do not share ground, and a queen with no hive behind her has nobody to send after a sleeper, so
     * moving away IS the resolution. An established hive that grows a claim over her is the other case entirely - that
     * one is an execution, and it lives in DormantQueenPurge.
     */
    private boolean tryFleeRivalQueen() {
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        var box = queen.getBoundingBox().inflate(RIVAL_QUEEN_WAKE_RANGE_BLOCKS);
        for (var other : serverLevel.getEntitiesOfClass(Queen.class, box)) {
            if (other == queen || !other.isAlive()) {
                continue;
            }
            // Two sleepers side by side is a spawn-spacing question, not a threat - neither of them is doing
            // anything. It is the WAKING queen, the one about to claim this ground, that moves her.
            if (Boolean.TRUE.equals(other.isHibernating.get())) {
                continue;
            }
            wakeAndFlee(
                other.chunkPosition(),
                RIVAL_QUEEN_FLEE_CLEARANCE_CHUNKS,
                "another queen (" + other.getUUID() + ") took this ground"
            );
            return true;
        }

        return false;
    }

    /**
     * Wakes her out of hibernation and sends her through the ordinary relocation machinery with a no-go zone attached:
     * she re-picks an anchor that clears {@code clearanceChunks} of {@code fleeFrom} as well as the usual hive spacing,
     * clip-digs there, and re-hibernates before founding. If nothing legal exists inside the search radius she settles
     * where she can, exactly as a boxed-in daughter queen does.
     */
    private void wakeAndFlee(ChunkPos fleeFrom, int clearanceChunks, String reason) {
        this.fleeFromChunk = fleeFrom;
        this.fleeClearanceChunks = clearanceChunks;
        Alien.LOGGER.info(
            "Queen lifecycle: wild queen {} woke and is relocating away from {} — {}",
            queen.getUUID(),
            fleeFrom,
            reason
        );
        beginFreedRelocation();
    }

    /** "You have awakened a slumbering nightmare" — sent to every player whose activity accumulated her sleep clock. */
    private void broadcastWildAwakening() {
        broadcastToNearbyPlayers(
            net.minecraft.network.chat.Component
                .literal("You have awakened a slumbering nightmare")
                .withStyle(com.alien.common.data.AlienVariantTypes.getFor(queen).chatColor()),
            true
        );
    }

    /** Sends a wild-queen message to every player within {@link #WILD_AWAKENING_BROADCAST_RANGE} blocks of her. */
    private void broadcastToNearbyPlayers(net.minecraft.network.chat.Component message) {
        broadcastToNearbyPlayers(message, false);
    }

    /**
     * As {@link #broadcastToNearbyPlayers(net.minecraft.network.chat.Component)}, optionally led by the queen's scream
     * for the loud tiers (genesis and awakening). Sound and text share one audience so no player ever hears a scream
     * without its line or reads a line without its scream. The hibernating whisper stays silent by design.
     */
    private void broadcastToNearbyPlayers(net.minecraft.network.chat.Component message, boolean withQueenScream) {
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (var player : serverLevel.players()) {
            if (player.distanceToSqr(queen) <= WILD_AWAKENING_BROADCAST_RANGE * WILD_AWAKENING_BROADCAST_RANGE) {
                if (withQueenScream) {
                    player.playNotifySound(
                        com.alien.common.registry.init.AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(),
                        net.minecraft.sounds.SoundSource.MASTER,
                        1,
                        1
                    );
                }
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * DEFENDING: awake and fighting, sleep clock paused. Once threats stay clear long enough, head back to the anchor.
     */
    private void tickHibernationDefending() {
        if (isThreatPresent()) {
            disturbanceCalmTicks = 0;
            return;
        }

        disturbanceCalmTicks++;
        if (disturbanceCalmTicks >= HIBERNATION_CALM_TICKS) {
            hibernationActivity = HibernationActivity.RETURNING;
            Alien.LOGGER.info(
                "Queen lifecycle: {} hibernation threat clear — returning to anchor {}",
                queen.getUUID(),
                anchor
            );
        }
    }

    /**
     * RETURNING: walking back (the GOAP return action drives the steps). Re-disturb on a new threat; sleep on arrival.
     */
    private void tickHibernationReturning() {
        if (isThreatPresent()) {
            hibernationActivity = HibernationActivity.DEFENDING;
            disturbanceCalmTicks = 0;
            return;
        }

        if (anchor == null) {
            resumeSleep();
            return;
        }

        var arrived = queen.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) <= RETURN_ARRIVAL_RADIUS
            * RETURN_ARRIVAL_RADIUS;
        if (arrived) {
            resumeSleep();
        }
    }

    /**
     * Damage hook (called from {@link Queen#hurt}). A hit at or above {@link #HIBERNATION_DISTURBANCE_DAMAGE} rouses a
     * sleeping or returning queen into the defend sub-state; weaker hits, and any damage taken when she is not
     * hibernating, are ignored.
     */
    /**
     * Registers a hit against her composure and reports whether it was enough to make her abandon her post.
     * <p>
     * [stated] "the queen gets off her eggsack too easily when damaged... when he used a syringe on her she got up. so
     * we need to have it be multiple sustained small hits or a hard damaging hit will get her up. so if someone hits
     * her accidentally or uses the syringe item it wont make her abandon her duty to fight."
     * <p>
     * TWO WAYS UP, and only two: one blow of {@link #HIBERNATION_DISTURBANCE_DAMAGE} or more, or
     * {@link #SUSTAINED_DISTURBANCE_DAMAGE} accumulated before the accumulator bleeds off. A syringe deals 0.01 - it
     * would take twelve hundred of them inside the window, which is the point. The accumulator DECAYS, so chipping at
     * her once a minute never adds up; you have to mean it.
     *
     * @return true if she has been roused and should be allowed to retaliate
     */
    public boolean registerDisturbance(float amount) {
        if (amount >= HIBERNATION_DISTURBANCE_DAMAGE) {
            rouse(amount, "single blow");
            return true;
        }

        disturbanceAccumulator += amount;
        if (disturbanceAccumulator >= SUSTAINED_DISTURBANCE_DAMAGE) {
            rouse(disturbanceAccumulator, "sustained");
            return true;
        }

        return false;
    }

    /** Bleeds the accumulator off, so only sustained pressure counts. Called once a second from {@link #tick}. */
    private void decayDisturbance() {
        if (disturbanceAccumulator > 0.0F) {
            disturbanceAccumulator = Math.max(0.0F, disturbanceAccumulator - DISTURBANCE_DECAY_PER_SECOND);
        }
    }

    private void rouse(float amount, String reason) {
        disturbanceAccumulator = 0.0F;

        if (phase != QueenLifecyclePhase.HIBERNATION || hibernationActivity == HibernationActivity.DEFENDING) {
            return;
        }

        hibernationActivity = HibernationActivity.DEFENDING;
        disturbanceCalmTicks = 0;
        queen.isHibernating.set(false);
        Alien.LOGGER.info(
            "Queen lifecycle: {} disturbed in HIBERNATION ({} dmg, {}) — defending",
            queen.getUUID(),
            amount,
            reason
        );
    }

    /**
     * Called by the return action when there is no walkable route back to the anchor: she abandons the old spot,
     * re-anchors where she is standing, and sleeps there. The paused sleep clock resumes from where it stopped.
     */
    public void onReturnPathBlocked() {
        if (phase != QueenLifecyclePhase.HIBERNATION || hibernationActivity != HibernationActivity.RETURNING) {
            return;
        }
        this.anchor = queen.blockPosition();
        Alien.LOGGER.info(
            "Queen lifecycle: {} could not path back to its anchor — re-anchoring at {}",
            queen.getUUID(),
            anchor
        );
        resumeSleep();
    }

    /** Drops back into the ASLEEP sub-state at the (possibly re-chosen) anchor, preserving the paused sleep clock. */
    private void resumeSleep() {
        hibernationActivity = HibernationActivity.ASLEEP;
        disturbanceCalmTicks = 0;
        if (anchor != null) {
            queen.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        }
        queen.isHibernating.set(true);
        Alien.LOGGER.info(
            "Queen lifecycle: {} resumed HIBERNATION at anchor {} — {} ticks left",
            queen.getUUID(),
            anchor,
            hibernationTicksRemaining
        );
    }

    /**
     * Threat test for the defend/return loop: an attack target, a recent hit, or a survival-mode player inside her
     * chunk (a hostile lingering in her territory keeps her awake even after the hits stop). Damage itself is not
     * tested here — that is the wake trigger ({@link #registerDisturbance}); this only gates when she may resettle.
     */
    private boolean isThreatPresent() {
        if (queen.getTarget() != null) {
            return true;
        }
        if (queen.hurtTime > 0) {
            return true;
        }

        var chunkPos = new ChunkPos(queen.blockPosition());
        var box = new AABB(
            chunkPos.getMinBlockX(),
            queen.getY() - 24.0,
            chunkPos.getMinBlockZ(),
            chunkPos.getMaxBlockX() + 1,
            queen.getY() + 24.0,
            chunkPos.getMaxBlockZ() + 1
        );
        for (var player : queen.level().getEntitiesOfClass(Player.class, box)) {
            if (!player.isCreative() && !player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    public HibernationActivity getHibernationActivity() {
        return hibernationActivity;
    }

    /** Ticks she has currently been threat-free while DEFENDING (counts toward returning to the anchor). Debug aid. */
    public int getDisturbanceCalmTicks() {
        return disturbanceCalmTicks;
    }

    /** Within {@link #ARRIVAL_TOLERANCE} of the anchor (3D). Must match {@code LocationMoveSensors} so GOAP agrees. */
    private boolean isAtAnchor() {
        if (anchor == null) {
            return false;
        }
        var dx = queen.getX() - (anchor.getX() + 0.5);
        var dy = queen.getY() - anchor.getY();
        var dz = queen.getZ() - (anchor.getZ() + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= ARRIVAL_TOLERANCE_SQ;
    }

    /**
     * Whether the dig may pass through this block. True for air/open space, blocks resin can replace
     * ({@link AlienBlockTags#RESIN_REPLACEABLE}), and blocks aliens can break — the latter mirrors
     * {@code Xenomorph.canPathBreakBlock}'s block test (no block entity, breakable, not
     * {@link AlienBlockTags#XENOMORPH_IMMUNE}). The mob-griefing gate is intentionally omitted: this is the queen's
     * lifecycle movement, not block-breaking, and her founding must not depend on that rule. Undiggable blocks
     * (bedrock, obsidian and other immune/reinforced blocks, block-entities) stop her.
     */
    public static boolean isDiggable(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.is(AlienBlockTags.RESIN_REPLACEABLE)) {
            return true;
        }
        return !state.hasBlockEntity()
            && state.getDestroySpeed(level, pos) >= 0.0F
            && !state.is(AlienBlockTags.XENOMORPH_IMMUNE);
    }

    /**
     * Called by the dig action when an undiggable block blocks her path to the anchor before she reaches it: she can't
     * pass, so she settles where she stands. Retargets the anchor to her current block, which trips the normal arrival
     * flow (pocket-clear + hand-off) on the next tick.
     */
    public void onDigBlocked() {
        if (phase == QueenLifecyclePhase.LOCATION) {
            this.anchor = queen.blockPosition();
        }
    }

    /** Carves a small breathable pocket around the anchor (floor preserved), skipping air and undiggable blocks. */
    private void clearArrivalPocket() {
        if (anchor == null || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var resin = com.alien.common.data.AlienVariantTypes.getFor(queen.getVariant())
            .resin()
            .get()
            .defaultBlockState();
        for (var dx = -ARRIVAL_POCKET_RADIUS; dx <= ARRIVAL_POCKET_RADIUS; dx++) {
            for (var dz = -ARRIVAL_POCKET_RADIUS; dz <= ARRIVAL_POCKET_RADIUS; dz++) {
                // FLOOR SEAL: whatever is under each pocket cell, if it is liquid (a lava/water lake edge she
                // dug into) it becomes her resin - the hive seals hazards rather than hibernating on them.
                var floorPos = anchor.offset(dx, -1, dz);
                if (serverLevel.getBlockState(floorPos).liquid()) {
                    serverLevel.setBlock(floorPos, resin, 3);
                }
                for (var dy = 0; dy < ARRIVAL_POCKET_HEIGHT; dy++) {
                    var pos = anchor.offset(dx, dy, dz);
                    var state = serverLevel.getBlockState(pos);
                    // Liquid in the pocket volume (lake edge) is sealed to resin at the RIM and cleared inside:
                    // the outermost ring becomes a resin dam so the lake can't re-flood the pocket.
                    if (state.liquid()) {
                        boolean rim = Math.abs(dx) == ARRIVAL_POCKET_RADIUS || Math.abs(dz) == ARRIVAL_POCKET_RADIUS;
                        serverLevel.setBlock(pos, rim ? resin : Blocks.AIR.defaultBlockState(), 3);
                        continue;
                    }
                    // Nothing to clear for air; never carve undiggable blocks (immune/unbreakable/block-entities).
                    if (state.isAir() || !isDiggable(serverLevel, pos)) {
                        continue;
                    }
                    serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Picks the anchor chunk. If her current chunk is already far enough from every existing location she digs in
     * place; otherwise she searches outward for the nearest far-enough chunk (the "prefer to relocate ~16 chunks away"
     * rule), and if boxed in on all sides within the search radius she settles for her current chunk (the "settle for
     * what she can get" rule — that overlap case is a parked design item). The far-enough test is the exact gate
     * {@code SpreadZoneCheck} enforces, so any chunk returned here that isn't the boxed-in fallback will pass founding.
     */
    private ChunkPos pickAnchorChunk() {
        var dimension = queen.level().dimension();
        var minimum = HiveLocationRegistry.INSTANCE.config().minimumHiveLocationDistanceChunks();
        var current = queen.chunkPosition();

        // If she can already found where she stands, she does - no need to relocate at all. A queen who was DRIVEN
        // off this spot is the exception: staying put is the one answer she is not allowed to give.
        if (SpreadZoneCheck.wouldAllow(queen, current) && clearsFleeZone(current)) {
            return clearFleeZone(current);
        }

        // She can't found here (too close to a hive). Rather than crawl outward from HERSELF (which lands her at
        // a distance that depends on where she happens to stand - up to the neighbour's whole spread zone), aim
        // directly for the nearest hive's minimum-spacing RING: the closest legal chunk to her that sits exactly
        // `minimum` chunks from that hive's centre. A daughter queen thus settles right at the 16-chunk gap, on
        // the side facing her, and never digs further than she must.
        var nearest = nearestLocation(dimension, current);
        if (nearest != null) {
            var ringPick = nearestFoundableOnSpacingRing(nearest, current, minimum);
            if (ringPick != null && clearsFleeZone(ringPick)) {
                return clearFleeZone(ringPick);
            }
        }

        // Fallback: no hive found to space away from (or its whole spacing ring was blocked). Crawl outward from
        // her for the nearest foundable chunk - the old behaviour, kept only as a last resort.
        var random = queen.getRandom();
        for (var radius = 1; radius <= MAX_ANCHOR_SEARCH_RADIUS_CHUNKS; radius++) {
            var ring = farEnoughChunksInRing(current, radius, dimension, minimum);
            ring.removeIf(candidate -> !clearsFleeZone(candidate));
            if (!ring.isEmpty()) {
                return clearFleeZone(ring.get(random.nextInt(ring.size())));
            }
        }

        // Boxed in even counting the no-go zone: she settles for what she can get, same as any daughter queen.
        return clearFleeZone(current);
    }

    /** True when no flight is pending, or when this chunk is far enough from whatever drove her off. */
    private boolean clearsFleeZone(ChunkPos candidate) {
        return fleeFromChunk == null
            || Math.max(Math.abs(candidate.x - fleeFromChunk.x), Math.abs(candidate.z - fleeFromChunk.z)) >= fleeClearanceChunks;
    }

    /** One anchor pick per flight: the no-go zone is consumed here so a later re-pick is an ordinary one. */
    private ChunkPos clearFleeZone(ChunkPos picked) {
        fleeFromChunk = null;
        fleeClearanceChunks = 0;
        return picked;
    }

    /** Nearest hive-location centre (Chebyshev, same-dimension) to {@code from}, or null if there are none. */
    private HiveLocation nearestLocation(ResourceKey<Level> dimension, ChunkPos from) {
        HiveLocation best = null;
        var bestDistance = Integer.MAX_VALUE;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(dimension)) {
                continue;
            }
            var locChunk = new ChunkPos(location.centerPos());
            var distance = Math.max(Math.abs(locChunk.x - from.x), Math.abs(locChunk.z - from.z));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = location;
            }
        }
        return best;
    }

    /**
     * The foundable chunk on {@code hive}'s minimum-spacing ring (Chebyshev square of radius {@code minimum} around its
     * centre) that is closest to {@code toward} - i.e. the least she has to move to reach a legal 16-chunk gap. Walks
     * the ring outward one step at a time so if the ideal ring is partly blocked (another hive's spread zone clips it)
     * she takes the next-closest legal chunk rather than overshooting. Returns null only if no chunk from the ring out
     * to the search cap is foundable.
     */
    private ChunkPos nearestFoundableOnSpacingRing(HiveLocation hive, ChunkPos toward, int minimum) {
        var centre = new ChunkPos(hive.centerPos());
        ChunkPos best = null;
        var bestToward = Integer.MAX_VALUE;
        for (var radius = minimum; radius <= MAX_ANCHOR_SEARCH_RADIUS_CHUNKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // ring border only
                    }
                    var candidate = new ChunkPos(centre.x + dx, centre.z + dz);
                    if (!SpreadZoneCheck.wouldAllow(queen, candidate)) {
                        continue;
                    }
                    var distToward = Math.max(
                        Math.abs(candidate.x - toward.x),
                        Math.abs(candidate.z - toward.z)
                    );
                    if (distToward < bestToward) {
                        bestToward = distToward;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best; // closest ring with ANY foundable chunk wins; take the nearest chunk on it
            }
        }
        return null;
    }

    private List<ChunkPos> farEnoughChunksInRing(
        ChunkPos center,
        int radius,
        ResourceKey<Level> dimension,
        int minimum
    ) {
        var out = new ArrayList<ChunkPos>();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                    continue; // border of the ring only
                }
                var candidate = new ChunkPos(center.x + dx, center.z + dz);
                if (SpreadZoneCheck.wouldAllow(queen, candidate)) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }

    private int pickTargetY() {
        var random = queen.getRandom();
        var roll = random.nextInt(COMMON_WEIGHT + RARE_WEIGHT + VERY_RARE_WEIGHT);
        int rolled;
        if (roll < COMMON_WEIGHT) {
            rolled = randomBetween(random, COMMON_Y_MIN, COMMON_Y_MAX);
        } else if (roll < COMMON_WEIGHT + RARE_WEIGHT) {
            rolled = randomBetween(random, RARE_Y_MIN, RARE_Y_MAX);
        } else {
            rolled = randomBetween(random, VERY_RARE_Y_MIN, VERY_RARE_Y_MAX);
        }
        // The bands above are hand-tuned OVERWORLD depths; other dimensions have fundamentally different vertical
        // shape (the Nether's floor is Y 0 - the deep bands would aim below its bedrock). Remap the rolled Y into
        // this dimension's own depth band, preserving the weighted shape; identity in the overworld.
        if (queen.level() instanceof ServerLevel bandLevel) {
            var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(bandLevel);
            rolled = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.remapFromOverworldBand(rolled, profile);
        }
        return rolled;
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    /**
     * Whether the queen is already past the front-end: she has an ovipositor, or she is the founder of any live
     * location in her dimension. Resolved by founder UUID across the registry, mirroring {@code FoundingMoveSensors}.
     */
    private boolean isAlreadyEstablished() {
        if (queen.hasOvipositor()) {
            return true;
        }

        var dimension = queen.level().dimension();
        var founderId = queen.getUUID();

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (location.isAlive() && founderId.equals(location.founderId()) && location.dimension().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(PHASE_TAG)) {
            this.phase = QueenLifecyclePhase.byNameOrDefault(
                compoundTag.getString(PHASE_TAG),
                QueenLifecyclePhase.DEVELOPING
            );
        }

        if (compoundTag.contains(DEVELOPING_TICKS_REMAINING_TAG)) {
            this.developingTicksRemaining = compoundTag.getInt(DEVELOPING_TICKS_REMAINING_TAG);
        }

        this.anchor = compoundTag.contains(ANCHOR_TAG) ? BlockPos.of(compoundTag.getLong(ANCHOR_TAG)) : null;

        if (compoundTag.contains(HIBERNATION_TICKS_REMAINING_TAG)) {
            this.hibernationTicksRemaining = compoundTag.getInt(HIBERNATION_TICKS_REMAINING_TAG);
        }

        this.wildSpawned = compoundTag.getBoolean(WILD_SPAWNED_TAG);
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putString(PHASE_TAG, phase.name());
        compoundTag.putInt(DEVELOPING_TICKS_REMAINING_TAG, developingTicksRemaining);
        compoundTag.putInt(HIBERNATION_TICKS_REMAINING_TAG, hibernationTicksRemaining);
        compoundTag.putBoolean(WILD_SPAWNED_TAG, wildSpawned);
        if (anchor != null) {
            compoundTag.putLong(ANCHOR_TAG, anchor.asLong());
        }
    }
}
