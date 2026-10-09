package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.model.lifecycle.growth.CocooningConfig;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-queen empress promotion timer. {@link #start} marks a queen as emerging — she's set
 * {@code invulnerable + noAi + persistenceRequired} for
 * {@link com.alien.common.gameplay.hive.config.HiveConfig#empressMoltDurationTicks()} (default 30 seconds). At
 * completion, her queen entity is replaced with the matching variant {@link Empress} entity at her position; the
 * lineage's {@code empressId} is set; the empress is added to the lineage's BLib membership; and the
 * {@code pendingEmpressEmergence} flag is cleared.
 * <p>
 * State is in-memory only (per the {@link com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector} pattern). A
 * server restart aborts in-flight emergences cleanly — the queens stay queens (with their invulnerable/noAi flags reset
 * by the next start attempt or by save/load).
 * <p>
 * Phase 10 ships the timer + swap; the visual molt animation pipeline is parked for a polish pass and can hook into the
 * existing {@link com.alien.common.gameplay.entity.living.alien.MoltingManager}.
 */
public final class EmpressEmergenceRitual {

    /** How far around the emerging empress to look for the husk she left behind. */
    private static final double CLUTCH_SWEEP_RADIUS = 8.0D;

    private static final Map<UUID, EmergenceState> states = new HashMap<>();

    /**
     * Loaded ticks an elected seat has been waiting for its queen to resolve, keyed by lineage. A bounded wait, not an
     * open one: waiting is right for the tick or two she takes to load in, but an unbounded wait would let a seat whose
     * queen is simply GONE hold the crown forever - the lineage collecting every empress benefit behind an empress who
     * can never be found and never killed.
     */
    private static final Map<ResourceLocation, Integer> materializeWaits = new HashMap<>();

    private static final int MAX_MATERIALIZE_WAIT_TICKS = 200;

    private EmpressEmergenceRitual() {}

    /**
     * Clears the "my queen is evolving" flag on the hive the royal is standing in, if any.
     * <p>
     * ⚠ TAKES AN Entity, NOT A Queen. It used to run before the swap, when a queen was still there to ask. It now runs
     * at the far end of the molt, by which point the only body left is the empress - standing in the same chunk, which
     * is all this ever needed.
     * </p>
     */
    private static void clearEvolvingMarker(Entity royal) {
        var location = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
            .getByChunk(royal.level().dimension(), royal.chunkPosition());
        if (location != null && location.isQueenEvolving()) {
            location.setQueenEvolving(false);
        }
    }

    public static void clear() {
        states.clear();
        materializeWaits.clear();
    }

    /**
     * Abandon any in-flight molt for {@code lineageFactionId} and give the queen her body back.
     * <p>
     * {@link #start} sets {@code invulnerable + noAi + persistenceRequired}, and all three PERSIST to NBT while the
     * timer driving them lives only in memory. Dropping the state without this leaves an unkillable, brainless queen
     * standing in the hive permanently. She is restored rather than killed: a half-molted queen is still a working
     * queen, and destroying a hive's royal as a side effect of an election changing its mind would be a far larger
     * consequence than the election deserves.
     */
    public static void cancelFor(MinecraftServer server, ResourceLocation lineageFactionId) {
        materializeWaits.remove(lineageFactionId);
        var iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next().getValue();
            if (!state.lineageFactionId().equals(lineageFactionId)) {
                continue;
            }
            restoreQueen(server, state);
            iterator.remove();
        }
    }

    /** Undo the molt flags on the queen named by {@code state}, if she is still around to receive them. */
    private static void restoreQueen(MinecraftServer server, EmergenceState state) {
        var serverLevel = server.getLevel(state.dimension());
        if (serverLevel == null) {
            return;
        }
        if (serverLevel.getEntity(state.queenId()) instanceof Queen queen) {
            queen.setInvulnerable(false);
            queen.setNoAi(false);
            // Tear the molt down with her, or she stays wrapped in a royal cocoon that nothing is driving toward a
            // destination any more.
            queen.getCocoonManager().cancel();
            Alien.LOGGER.info("Hive: empress molt cancelled - queen {} restored", state.queenId());
        }
    }

    /** Whether some queen of {@code lineageFactionId} is currently in the emergence ritual. */
    public static boolean isEmergingFor(ResourceLocation lineageFactionId) {
        for (var state : states.values()) {
            if (state.lineageFactionId().equals(lineageFactionId)) {
                return true;
            }
        }
        return false;
    }

    /** Read-only snapshot for debug commands. */
    public static Map<UUID, EmergenceState> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(states));
    }

    /**
     * Give an already-ELECTED empress her body, if her seat is loaded and she is still eligible.
     * <p>
     * Called from the loaded tick of the elected seat. The election happened abstractly and possibly a very long time
     * ago, so this both waits for the queen entity to tick in and re-checks she has not been captured or inhibited
     * since. If she is gone or ineligible the seat is RELEASED along with {@code empressId}, and the next
     * {@link EmpressEmergenceTask} scan elects somewhere else.
     */
    public static void tryMaterialize(
        ServerLevel serverLevel,
        com.alien.common.gameplay.hive.location.HiveLocation location,
        LineageFactionData lineage
    ) {
        var lineageFactionId = lineage.factionId();
        if (lineageFactionId == null || isEmergingFor(lineageFactionId)) {
            return;
        }

        var founderId = location.founderId();
        if (founderId != null && serverLevel.getEntity(founderId) == null) {
            // Chunks are loaded but she has not ticked in yet. Waiting is correct - releasing immediately would
            // thrash the election every time a player walked into the seat hive - but the wait is BOUNDED, so a seat
            // whose queen is genuinely gone eventually surrenders the crown instead of holding it forever.
            var waited = materializeWaits.merge(lineageFactionId, 1, Integer::sum);
            if (waited < MAX_MATERIALIZE_WAIT_TICKS) {
                return;
            }
        }

        // ⚠⚠ SHE MAY ALREADY BE AN EMPRESS. The ritual's state map is in-memory only, but the COCOON is persisted -
        // so a world reloaded mid-molt finishes the molt with nobody supervising it, and this used to find no queen
        // at the seat and release the crown from under an empress who was standing right there.
        var alreadyCrowned = lineage.empressId() == null ? null : serverLevel.getEntity(lineage.empressId());
        if (alreadyCrowned instanceof Empress standing && standing.isAlive()) {
            materializeWaits.remove(lineageFactionId);
            EmpressCandidatePicker.clearBench(location.id());
            adopt(lineage, standing, location.founderId());
            Alien.LOGGER.info(
                "Hive: adopted an already-emerged empress {} at seat {} - her molt completed across a reload",
                standing.getUUID(),
                location.id()
            );
            return;
        }

        // ⚠⚠⚠ SHE MAY BE STILL INSIDE THE COCOON WITH NOBODY WATCHING, AND THAT USED TO END THE MOLT.
        // The cocoon persists across a reload; the states map does not. So on the tick after a restart nothing
        // answered isEmergingFor(), execution reached resolveSeatedQueen - which REQUIRES hasOvipositor() - and a
        // queen who dismounted her sack to climb into the cocoon fails that test by definition. The crown was
        // released and the seat benched for five minutes, out from under a molt that was still running and would
        // shortly produce an empress carrying an id the lineage had just forgotten.
        //
        // * Re-register the supervision instead. The cocoon itself is the record of what she is becoming, and it
        // carries the agreed id, so the ritual can pick the thread back up from the entity alone.
        var seatFounderId = location.founderId();
        var seated = seatFounderId == null ? null : serverLevel.getEntity(seatFounderId);
        if (seated instanceof Queen molting && molting.isAlive() && !molting.isRemoved()) {
            var cocoon = molting.getCocoonManager();
            var forcedId = cocoon.getForcedNewEntityId();
            var target = cocoon.getTargetType();

            if (cocoon.shouldRunCocoonAction() && forcedId != null && target != null && target.is(AlienEntityTypeTags.EMPRESSES)) {
                materializeWaits.remove(lineageFactionId);
                EmpressCandidatePicker.clearBench(location.id());
                // Repair the crown if a previous pass already released it - the cocoon is the authority on which id
                // is about to walk out of it.
                lineage.setEmpressId(forcedId);
                states.put(
                    molting.getUUID(),
                    new EmergenceState(
                        molting.getUUID(),
                        forcedId,
                        lineageFactionId,
                        serverLevel.dimension(),
                        serverLevel.getGameTime()
                    )
                );
                Alien.LOGGER.info(
                    "Hive: resumed supervision of queen {}'s empress molt at seat {} - it was still running across a "
                        + "reload",
                    molting.getUUID(),
                    location.id()
                );
                return;
            }
        }

        var queen = EmpressCandidatePicker.resolveSeatedQueen(serverLevel, location);
        if (queen == null) {
            materializeWaits.remove(lineageFactionId);
            Alien.LOGGER.info(
                "Hive: empress election released - elected seat {} has no eligible queen on load (dead, captured or "
                    + "inhibited); surrendering the crown for re-election",
                location.id()
            );
            lineage.setPendingEmpressSeatId(null);
            lineage.setEmpressId(null);
            // Bench it. Without this the next scan re-elects the same seat immediately, because pickSeat only
            // sees persisted data and cannot know she is inhibited, contained, or not yet founded.
            EmpressCandidatePicker.benchSeat(location.id(), serverLevel.getGameTime());
            return;
        }

        materializeWaits.remove(lineageFactionId);
        EmpressCandidatePicker.clearBench(location.id());
        start(queen, lineageFactionId, serverLevel.getGameTime());
    }

    /**
     * Begin the molt for {@code queen}. Idempotent: a queen already in the map is left alone.
     */
    public static void start(Queen queen, ResourceLocation lineageFactionId, long currentTick) {
        var uuid = queen.getUUID();
        if (states.containsKey(uuid)) {
            return;
        }

        // She is already wrapped up in some other metamorphosis. prepare() would silently no-op and we would sit
        // here supervising a molt that is heading somewhere else entirely.
        if (queen.getCocoonManager().shouldRunCocoonAction()) {
            Alien.LOGGER.warn(
                "Hive: empress emergence refused for queen {} - she is already mid-cocoon toward {}",
                uuid,
                queen.getCocoonManager().getTargetType()
            );
            return;
        }

        var faction = Alien.MOD.factions().get(lineageFactionId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            Alien.LOGGER.warn("Hive: empress emergence refused - lineage {} is missing", lineageFactionId);
            return;
        }

        // !! THE ID IS AGREED BEFORE THE BODY EXISTS, and that is the whole reason this molt is special. The lineage
        // elects an empress and publishes her id; every abstract effect keyed to it has been running ever since. The
        // cocoon is told to give the emerging empress exactly that id rather than minting a fresh one.
        //
        // * If nothing has been published yet (the forced/evolve-in-place routes can arrive here first), mint one NOW
        // and publish it, so there is still exactly one agreed id rather than a surprise at the far end.
        var electedId = lineage.empressId();
        if (electedId == null) {
            electedId = UUID.randomUUID();
            lineage.setEmpressId(electedId);
        }

        var empressType = Empress.getType(queen.getVariant());

        // Step 2 of his molt: she gets off her eggsack. Dismount only - the husk lingers where she left it and rots
        // on Ovipositor.ABANDONED_LINGER_TICKS, which is why that was dropped to 45s: at 120s the dead sack was still
        // lying in the clutch long after the empress had grown her own.
        queen.getOvipositorManager().abandonOvipositor();

        // ⚠ INVULNERABLE AND PERSISTENT, BUT **NOT** setNoAi(true). performCocoonTick() is a GOAP ACTION - under noAi
        // the agent never ticks, so the cocoon would spawn and then hang there forever. The cocoon graph immobilises
        // her itself (stopMovementAndTargeting every tick), so nothing is lost by dropping it.
        queen.setInvulnerable(true);
        queen.setPersistenceRequired();

        var moltTicks = (int) Math.max(1L, HiveLocationRegistry.INSTANCE.config().empressMoltDurationTicks());

        // [stated] "we want it to at least be as long as the praetorians" - so the same shape the praetorian -> queen
        // promotion uses: the window spent as a QUEEN wrapping up, then the same window again spent as an EMPRESS in
        // her own in-cocoon loop, then the emerge. Both royal transformations now read with the same weight.
        queen.getCocoonManager().prepare(empressType, null, new CocooningConfig(moltTicks, moltTicks), electedId);

        states.put(
            uuid,
            new EmergenceState(uuid, electedId, lineageFactionId, queen.level().dimension(), currentTick)
        );

        Alien.LOGGER.info(
            "Hive: empress emergence started for queen {} (lineage {}) - royal cocoon molt {}t + {}t, emerging as {}",
            uuid,
            lineageFactionId,
            moltTicks,
            moltTicks,
            electedId
        );
    }

    /**
     * Per-server-tick driver. Walks every emerging queen; aborts those whose entity is gone, fires completion when the
     * molt timer elapses.
     */
    public static void tick(MinecraftServer server) {
        if (states.isEmpty()) {
            return;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        Iterator<Map.Entry<UUID, EmergenceState>> iterator = states.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            var state = entry.getValue();
            var serverLevel = server.getLevel(state.dimension());

            if (serverLevel == null) {
                Alien.LOGGER.info(
                    "Hive: empress emergence aborted for queen {} — dimension {} unloaded",
                    state.queenId(),
                    state.dimension().location()
                );
                iterator.remove();
                continue;
            }

            // !! THIS IS NO LONGER A TIMER. The royal cocoon owns the schedule now - it wraps her up, swaps the
            // entity at its own moment and runs the emerge. All this has to do is notice when the empress has
            // arrived, which is trivial because her id was agreed before the molt began.
            //
            // ⚠ In the evolve-in-place route the agreed id IS the queen's own, so this lookup returns HER while she
            // is still a queen. The instanceof is what tells the two apart, not the id.
            if (serverLevel.getEntity(state.empressId()) instanceof Empress empress) {
                iterator.remove();
                finish(empress, state);
                continue;
            }

            var entity = serverLevel.getEntity(state.queenId());
            if (!(entity instanceof Queen queen) || !queen.isAlive() || queen.isRemoved()) {
                Alien.LOGGER.info("Hive: empress emergence aborted - queen {} no longer present", state.queenId());
                iterator.remove();
                continue;
            }

            // Her cocoon ended without producing an empress - obstructed, cleared, or cancelled from elsewhere.
            // Give it back rather than supervising a molt that is not happening.
            if (!queen.getCocoonManager().shouldRunCocoonAction()) {
                Alien.LOGGER.info(
                    "Hive: empress emergence aborted - queen {} left the cocoon without emerging",
                    state.queenId()
                );
                iterator.remove();
                restoreQueen(server, state);
                continue;
            }

            // Bounded supervision. The cocoon retries on obstruction (TRANSITION_RETRY_TIME_IN_TICKS), so a queen
            // wrapped up somewhere she cannot fit as an empress would otherwise hold the crown indefinitely.
            var elapsed = serverLevel.getGameTime() - state.startedAtTick();
            if (elapsed > config.empressMoltDurationTicks() * 4L + 600L) {
                Alien.LOGGER.warn(
                    "Hive: empress emergence gave up on queen {} after {}t - the cocoon never completed (most likely "
                        + "she cannot fit as an empress where she is standing)",
                    state.queenId(),
                    elapsed
                );
                iterator.remove();
                restoreQueen(server, state);
            }
        }
    }

    /**
     * Bookkeeping only. The empress already exists - the cocoon built her, with her agreed id, carrying the queen's
     * NBT. All that is left is to point the lineage and the seat at her.
     */
    /**
     * Removes the queen's abandoned eggsack at the moment the empress steps out of the cocoon.
     * <p>
     * [stated] "the empress ourpaces the eggsack despawn" - she was emerging while the old sack was still lying there,
     * which is the exact thing the 45s linger was chosen to avoid.
     * </p>
     * <p>
     * !!! THE TIMING WAS NEVER GOING TO BE RELIABLE. The linger starts when the sack STOPS BEING CARRIED, and the molt
     * is 61.5s against a 45s linger - so on paper it clears 16s early. It does not, which means the dismount is not
     * landing when the ritual asks for it. Rather than tune two clocks into agreeing, this makes the outcome
     * independent of both: whenever she emerges, the old clutch is gone. The linger still governs every OTHER way a
     * sack is abandoned - a queen killed, roused, or unloaded.
     * </p>
     * <p>
     * ⚠ ABANDONED SACKS ONLY, AND ONLY HERS. A sack still being carried belongs to a living royal and is never touched;
     * the search is tight because she emerges exactly where the queen stood.
     * </p>
     */
    private static void clearTheOldClutch(Empress empress) {
        if (!(empress.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var area = empress.getBoundingBox().inflate(CLUTCH_SWEEP_RADIUS);

        for (
            var sack : serverLevel.getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor.class,
                area
            )
        ) {
            if (sack.getVehicle() != null) {
                continue; // a living royal is still on it
            }

            Alien.LOGGER.info(
                "Hive: cleared the queen's abandoned eggsack at {} - the empress has emerged",
                sack.blockPosition()
            );
            sack.discard();
        }

        // ⭐ AND THE CAGE. She steps out exactly where it stands, so any royal cocoon still here is the one she came
        // out of. Belt and braces with RoyalCocoon's own orphan check: that one is the safety net for every caste,
        // this makes the empress path independent of the cage handover entirely.
        for (
            var cage : serverLevel.getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon.class,
                area
            )
        ) {
            var occupant = cage.getOccupantId();
            if (occupant != null && !occupant.equals(empress.getUUID())) {
                continue; // somebody else is molting in there
            }

            Alien.LOGGER.info("Hive: consumed the royal cocoon at {} - the empress has emerged", cage.blockPosition());
            cage.setOccupantId(null);
            cage.discard();
        }
    }

    private static void finish(Empress empress, EmergenceState state) {
        clearTheOldClutch(empress);

        var faction = Alien.MOD.factions().get(state.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            Alien.LOGGER.warn(
                "Hive: empress {} emerged but lineage {} is missing - she stands, uncrowned",
                empress.getUUID(),
                state.lineageFactionId()
            );
            return;
        }

        // Carried over from the old complete(): the queen is gone from the world, so she must stop being a
        // trackable queen, and the hive must stop advertising that its queen is mid-change.
        if (empress.level() instanceof ServerLevel serverLevel) {
            TrackedQueenRegistry.markLostAndAnnounce(serverLevel, state.queenId(), TrackedQueenRegistry.REASON_EMPRESS);
        }
        clearEvolvingMarker(empress);

        adopt(lineage, empress, state.queenId());

        Alien.LOGGER.info(
            "Hive: empress emergence completed - queen {} -> empress {} (variant {}, lineage {})",
            state.queenId(),
            empress.getUUID(),
            empress.getVariant(),
            state.lineageFactionId()
        );
    }

    /**
     * Hands the lineage and the vacated seat over to {@code empress}.
     * <p>
     * ⚠ SEPARATE FROM {@link #finish} ON PURPOSE. The in-memory ritual state does not survive a server restart, but the
     * COCOON does - so a world reloaded mid-molt will produce an empress with nobody supervising her. tryMaterialize
     * calls this when it finds one, instead of releasing the crown as it used to.
     * </p>
     */
    private static void adopt(LineageFactionData lineage, Empress empress, @Nullable UUID formerQueenId) {
        lineage.setEmpressId(empress.getUUID());
        lineage.setPendingEmpressEmergence(false);
        // The body has caught up with the crown; the seat reservation has done its job.
        lineage.setPendingEmpressSeatId(null);
        materializeWaits.remove(lineage.factionId());

        // Hand the seat's founder pointer to the empress. She IS that hive's royal now; leaving it on the queen we
        // just replaced would leave the location naming an entity that no longer exists, which reads as "has a
        // queen" to the growth and economy tasks and as a crownable seat to the next election.
        for (var location : lineage.locationsById().values()) {
            var founder = location.founderId();
            var isFormerSeat = formerQueenId != null && formerQueenId.equals(founder);

            if (isFormerSeat || empress.getUUID().equals(founder)) {
                location.setFounderId(empress.getUUID());
                // ⚠ RECORD THE SEAT. A natural empress takes the throne of the hive she founded as a queen, and the
                // occupancy checks in the succession and schism paths read this rather than scanning entities - so a
                // naturally-emerged empress that never set it would leave her own hive looking vacant.
                location.setSittingEmpressId(empress.getUUID());
            }
        }
    }

    public record EmergenceState(
        UUID queenId,
        UUID empressId,
        ResourceLocation lineageFactionId,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
        long startedAtTick
    ) {}
}
