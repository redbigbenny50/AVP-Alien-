package com.alien.common.gameplay.hive.location;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.property.AlienProperties;
import com.alien.common.property.AlienPropertyAccess;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.function.Supplier;

/**
 * Per-location boss bar. Visible to players within {@link HiveConfig#bossBarDisplayRadiusBlocks()} of the location's
 * center. Title identifies the lineage/location numbers; color follows the lineage variant.
 * <p>
 * Progress = (loaded xenomorphs inside this location's chunks + xenomorphs in local reserves) /
 * {@code peakXenomorphCount}. The peak decays at 1 per minute (fixes {@code HIVE_SYSTEM_ANALYSIS.md} § 9.5.22) and is
 * floored at 1 (fixes § 9.1.4 NaN).
 * <p>
 * Two display states ride on top:
 * <ul>
 * <li><b>Angry</b> — any player is on the bar (no special visual; meant as the implicit default state behavior driver
 * for later phases that pause claims, suppress shedding, etc.).</li>
 * <li><b>Evacuating</b> — set by Phase 8b migration dispatch. Color shifts to YELLOW, title gets " (Evacuating)"
 * appended. Held for {@link HiveConfig#migrationRallyTicks()}.</li>
 * </ul>
 * <p>
 * See {@code HIVE_REDESIGN_04_BOSS_BAR.md}.
 */
public final class HiveLocationBossBar {

    private static final long PEAK_DECAY_INTERVAL_TICKS = 20L * 60L; // 1 per minute

    private static final long VISIBILITY_REEVAL_INTERVAL_TICKS = 20L;

    /**
     * How long the bar keeps the current engagement's baseline after the last player stops watching.
     * <p>
     * Without a grace window, stepping out of range for a moment and coming back would re-snapshot at whatever is left
     * - walk away at 50 out of 200, walk back and it reads 50/50, as though the fight had never happened.
     * </p>
     */
    private static final long ENGAGEMENT_RESET_TICKS = 20L * 60L;

    private final HiveLocation location;

    private final Supplier<HiveConfig> configSupplier;

    private final ServerBossEvent bossEvent;

    /**
     * The hive's population at the moment this engagement began - the denominator on the bar.
     * <p>
     * [stated] "hive members total/hive member count at time of attack. then the first part goes down and once it
     * reaches 0 the hives dead. the only variable that actively changes is that first number."
     * </p>
     * <p>
     * ⚠⚠ IT USED TO BE A CAP, AND THAT IS WHAT EVERYONE WAS MISREADING. The old denominator was
     * {@code populationPerChunk x claimedChunks} - a CEILING, not a headcount - so a hive of 500 could read "/200" and
     * players took the 200 for the size of the thing they were fighting. Against a snapshot of what was actually there
     * when they arrived, the bar answers the question they are really asking: how much of this is left.
     * </p>
     * <p>
     * ⚠ MEMORY ONLY, DELIBERATELY. A snapshot of one fight is not worth persisting; a server restart mid-siege simply
     * re-baselines at whatever is standing, which is the honest reading for a fight that is starting again.
     * </p>
     */
    private int engagementBaseline;

    /** Ticks since the last player was watching. Drives {@link #ENGAGEMENT_RESET_TICKS}. */
    private long ticksSinceWatched;

    public HiveLocationBossBar(HiveLocation location, AlienVariant variant, Supplier<HiveConfig> configSupplier) {
        this.location = location;
        this.configSupplier = configSupplier;
        this.bossEvent = (ServerBossEvent) new ServerBossEvent(
            titleComponent(ownedXenomorphCount()),
            AlienVariantTypes.getFor(variant).bossBarColor(),
            BossEvent.BossBarOverlay.PROGRESS
        ).setDarkenScreen(AlienPropertyAccess.INSTANCE.getOrThrow(AlienProperties.Hive.DARKEN_SCREEN));
    }

    public void tick(MinecraftServer server, AlienVariant variant, LineageFactionData lineage) {
        decayPeak();
        decayEvacuating();
        updateEngagement(server);
        var xenomorphCount = updateProgress(lineage);
        updateColorAndTitle(variant, xenomorphCount);
        updateTrackingPlayers(server, variant);
    }

    private void decayPeak() {
        var elapsed = location.peakDecayElapsedTicks() + 1L;
        var peak = location.peakXenomorphCount();

        while (elapsed >= PEAK_DECAY_INTERVAL_TICKS) {
            elapsed -= PEAK_DECAY_INTERVAL_TICKS;
            peak = Math.max(1, peak - 1);
        }

        location.setPeakDecayElapsedTicks(elapsed);
        location.setPeakXenomorphCount(peak);
    }

    private void decayEvacuating() {
        var remaining = location.evacuatingRemainingTicks();
        if (remaining > 0) {
            location.setEvacuatingRemainingTicks(remaining - 1);
        }
    }

    /**
     * Opens, holds or closes the current engagement.
     * <p>
     * !!! THE TRIGGER IS BEING ATTACKED, NOT BEING LOOKED AT. It first keyed off "this bar gained a viewer", and that
     * was wrong twice over:
     * </p>
     * <p>
     * ⚠⚠ THE BAR CARRIES FURTHER THAN THE HIVE LOADS. totalReliableXenomorphPopulation is loaded members PLUS the
     * banks, and an unloaded hive has no loaded members - so a bar caught at range snapshotted the BANK ALONE and then
     * froze there. A hive with 2 banked and 48 asleep read "50/2" the moment its chunks came in. Reported from a
     * screenshot of exactly that.
     * </p>
     * <p>
     * ⚠ AND STANDING NEARBY IS NOT A SIEGE. A player who parks within bar range while a hive grows kept a baseline from
     * an hour ago.
     * </p>
     * <p>
     * ⭐ So while nothing hostile is in the territory the baseline simply TRACKS the live count - the bar reads N/N, a
     * full bar and an honest headcount. The moment the hive has someone to defend against it freezes, and from then on
     * the first number is the only one that moves. Uses the same intruder set as the vent response and the harbinger
     * reveal, so all three agree on what "under attack" means, and it excludes creative and spectator.
     * </p>
     * <p>
     * * ONCE TAKEN IT DOES NOT MOVE, INCLUDING UPWARDS. [stated] "say i start the hive attack i havnt killed anything
     * it reads 65/65 and more xenos are born in the host chamber or just finish molting it can go to 68/65 thats fine.
     * the first number needs to be dynamic." So the hive out-breeding your kill rate is meant to SHOW as 68/65 rather
     * than be hidden by quietly moving the goalposts. The fill clamps at full; the numbers tell the truth.
     * </p>
     */
    private void updateEngagement(MinecraftServer server) {
        var level = server.getLevel(location.dimension());
        var underAttack = level != null
            && !com.alien.common.gameplay.hive.tick.HiveTerritoryAggroTask
                .intrudersInTerritory(level, location)
                .isEmpty();

        if (underAttack) {
            ticksSinceWatched = 0;

            // The fight has started: take the snapshot once, then hold it.
            if (engagementBaseline <= 0) {
                engagementBaseline = Math.max(1, ownedXenomorphCount());
            }
            return;
        }

        ticksSinceWatched++;
        if (ticksSinceWatched >= ENGAGEMENT_RESET_TICKS) {
            // Nothing is fighting this hive, so there is no engagement to measure against. Track live, which reads
            // N/N - a full bar and an honest headcount until someone actually attacks.
            engagementBaseline = 0;
        }

        if (engagementBaseline <= 0) {
            engagementBaseline = Math.max(1, ownedXenomorphCount());
        }
    }

    private int updateProgress(LineageFactionData lineage) {
        var total = ownedXenomorphCount();

        // Reference `lineage` to satisfy the param contract; future lineage-level display rules may use it.
        if (lineage == null) {
            return total;
        }

        // The peak is still tracked and still persisted - it is a genuine record of what this hive once was, and
        // reverting the bar to it is a one-line change if the battered-hive read is wanted back.
        var peak = Math.max(location.peakXenomorphCount(), Math.max(1, total));
        location.setPeakXenomorphCount(peak);

        // ⭐ THE BAR FILLS AGAINST THE CAP, NOT AGAINST THE PEAK. It used to be total/peak-ever - a ratchet - so a
        // hive that took losses and rebuilt all the way back to its ceiling still showed a half-empty bar forever,
        // and nobody could tell a thriving hive from a dying one. Against the cap the bar answers the question a
        // player actually has: how much room is left in this thing.
        var baseline = engagementBaseline;
        bossEvent.setProgress(baseline > 0 ? Math.min(1.0F, total / (float) baseline) : total / (float) peak);
        return total;
    }

    /**
     * What this hive actually owns right now: loaded adults plus all three reserve banks.
     * <p>
     * !!! INBOUND CONVOY MEMBERS ARE **NOT** IN HERE, and that is the whole point. [stated] "once it reaches 0 the
     * hives dead" - so the number on the bar has to be the same quantity the death handler tests, or a hive would read
     * 10 and then die anyway because a convoy was in the air. Reinforcements on their way are reported ALONGSIDE the
     * count instead, where they inform without lying about what is left to kill.
     * </p>
     */
    private int ownedXenomorphCount() {
        return CastePopulation.totalReliableXenomorphPopulation(location);
    }

    /**
     * Adults riding a convoy that is on its way HERE to stay.
     * <p>
     * [stated] "if they are arriving at another hive and staying there they should count to the recieving hive." A
     * dispatched convoy DRAINS its composition out of the source's reserves and only pours it into the destination's on
     * arrival, so for the whole journey those members existed in neither hive's number and the bar silently dropped by
     * the size of the convoy with nobody dying.
     * </p>
     * <p>
     * !!! DISPLAY ONLY. THIS MUST NEVER BE FOLDED INTO CastePopulation.totalReliableXenomorphPopulation. [stated] "a
     * convoy mid journey when a hive is attacked though shouldnt keep the hive alive waiting for its arrival though
     * that would get it stuck and not dying." That function is read by LineageDeathHandler and LocationDormancyTask,
     * both of which kill a location on {@code == 0} - so counting an inbound convoy there would make a hive you have
     * just wiped out UNKILLABLE until the convoy landed. The number on the bar and the number that decides life and
     * death are deliberately different, and this is the one place they diverge.
     * </p>
     * <p>
     * ⚠ REINFORCEMENT AND MIGRATION ONLY. A Raid convoy is passing through to attack something, not arriving to stay,
     * and it has no destination LOCATION to be counted against in the first place.
     * </p>
     */
    private int inboundConvoyCount() {
        var faction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return 0;
        }

        var inbound = 0;
        for (var convoy : lineage.convoys()) {
            com.alien.common.gameplay.hive.id.HiveLocationId destination = null;

            if (convoy instanceof com.alien.common.gameplay.hive.convoy.Convoy.Reinforcement reinforcement) {
                destination = reinforcement.destinationLocationId();
            } else if (convoy instanceof com.alien.common.gameplay.hive.convoy.Convoy.Migration migration) {
                destination = migration.destinationLocationId();
            }

            if (destination == null || !destination.equals(location.id())) {
                continue;
            }

            // Same tag test the owned count uses, so ovomorphs, facehuggers, chestbursters and adolescents are
            // excluded here exactly as they are everywhere else.
            inbound += convoy.composition().getCountMatching(type -> type.is(AlienEntityTypeTags.XENOMORPHS));
        }

        return inbound;
    }

    private void updateColorAndTitle(AlienVariant variant, int xenomorphCount) {
        var evacuating = location.evacuatingRemainingTicks() > 0;
        if (evacuating) {
            bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
        } else {
            bossEvent.setColor(AlienVariantTypes.getFor(variant).bossBarColor());
        }
        bossEvent.setName(titleComponent(xenomorphCount));
    }

    /**
     * ⭐ THE TITLE CARRIES THE CAP TOO, because the number alone was ambiguous and testers read it three different ways.
     * {@code xenomorphCount} is EVERY ADULT THE HIVE OWNS - the ones standing in front of you PLUS all three reserve
     * banks - so it is deliberately not a headcount of what is visible. Printing it against its ceiling is the cheapest
     * way to say which quantity it is.
     */
    /**
     * {@code Hive (lineage, location) - LEFT/AT-ARRIVAL (+N arriving)}.
     * <p>
     * Both numbers are the same quantity - adults owned, standing and banked - so the fraction reads as plainly as it
     * looks: kill the first number down to zero and the hive is gone. Only the denominator is frozen.
     * </p>
     */
    private Component titleComponent(int xenomorphCount) {
        var baseline = engagementBaseline;
        var population = baseline > 0 ? xenomorphCount + "/" + baseline : String.valueOf(xenomorphCount);

        // Outside the fraction on purpose. These are real members and they will count the moment they land, but
        // until then they are not standing in this hive and must not pad the number a player is working down.
        var inbound = inboundConvoyCount();
        var arriving = inbound > 0 ? " (+" + inbound + " arriving)" : "";

        return Component.literal(
            "Hive (" + locationLineageNumber() + ", " + location.locationNumber() + ") - " + population + arriving
        );
    }

    private long locationLineageNumber() {
        var faction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            return lineage.lineageNumber();
        }
        return -1L;
    }

    private void updateTrackingPlayers(MinecraftServer server, AlienVariant variant) {
        if (location.ageInTicks() % VISIBILITY_REEVAL_INTERVAL_TICKS != 0) {
            return;
        }

        var level = server.getLevel(location.dimension());
        if (level == null) {
            bossEvent.removeAllPlayers();
            return;
        }

        var radius = configSupplier.get().bossBarDisplayRadiusBlocks();
        var radiusSqr = (double) radius * radius;

        // Add players newly in range.
        for (var player : level.players()) {
            var inRangeNow = player.blockPosition().distSqr(location.centerPos()) <= radiusSqr
                && AlienPredicates.isValidTarget(variant, player);

            if (inRangeNow && !bossEvent.getPlayers().contains(player)) {
                bossEvent.addPlayer(player);
            }
        }

        // Remove players who left, changed dimension, or died.
        var toRemove = bossEvent.getPlayers()
            .stream()
            .filter(player -> shouldRemove(player, variant, radiusSqr))
            .toList();

        toRemove.forEach(bossEvent::removePlayer);
    }

    private boolean shouldRemove(ServerPlayer player, AlienVariant variant, double radiusSqr) {
        if (!player.level().dimension().equals(location.dimension())) {
            return true;
        }
        if (!AlienPredicates.isValidTarget(variant, player)) {
            return true;
        }
        return player.blockPosition().distSqr(location.centerPos()) > radiusSqr;
    }

    /** Drops every player from the bar. Called on location removal. */
    public void onRemoved() {
        bossEvent.removeAllPlayers();
    }

    public boolean isAngry() {
        return !bossEvent.getPlayers().isEmpty();
    }

    public boolean isEvacuating() {
        return location.evacuatingRemainingTicks() > 0;
    }

    /** Used by Phase 8b migration dispatch to push the boss bar into the Evacuating window. */
    public void enterEvacuating(long durationTicks) {
        var current = location.evacuatingRemainingTicks();
        location.setEvacuatingRemainingTicks(Math.max(current, durationTicks));
    }
}
