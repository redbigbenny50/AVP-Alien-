package com.alien.common.gameplay.hive.location;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.property.AlienProperties;
import com.alien.common.property.AlienPropertyAccess;
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

    private final HiveLocation location;

    private final Supplier<HiveConfig> configSupplier;

    private final ServerBossEvent bossEvent;

    public HiveLocationBossBar(HiveLocation location, AlienVariant variant, Supplier<HiveConfig> configSupplier) {
        this.location = location;
        this.configSupplier = configSupplier;
        this.bossEvent = (ServerBossEvent) new ServerBossEvent(
            titleComponent(currentXenomorphCount()),
            AlienVariantTypes.getFor(variant).bossBarColor(),
            BossEvent.BossBarOverlay.PROGRESS
        ).setDarkenScreen(AlienPropertyAccess.INSTANCE.getOrThrow(AlienProperties.Hive.DARKEN_SCREEN));
    }

    public void tick(MinecraftServer server, AlienVariant variant, LineageFactionData lineage) {
        decayPeak();
        decayEvacuating();
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

    private int updateProgress(LineageFactionData lineage) {
        var total = currentXenomorphCount();

        // Reference `lineage` to satisfy the param contract; future lineage-level display rules may use it.
        if (lineage == null) {
            return total;
        }

        var peak = Math.max(location.peakXenomorphCount(), Math.max(1, total));
        location.setPeakXenomorphCount(peak);
        bossEvent.setProgress(total / (float) peak);
        return total;
    }

    private int currentXenomorphCount() {
        return CastePopulation.totalReliableXenomorphPopulation(location);
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

    private Component titleComponent(int xenomorphCount) {
        return Component.literal(
            "Hive (" + locationLineageNumber() + ", " + location.locationNumber() + ") - " + xenomorphCount
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
