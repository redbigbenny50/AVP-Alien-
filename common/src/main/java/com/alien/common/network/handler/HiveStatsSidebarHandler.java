package com.alien.common.network.handler;

import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.economy.JellyProduction;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug sidebar for tracking the nearest hive location from the vanilla right-side scoreboard HUD.
 */
public final class HiveStatsSidebarHandler {

    private static final int UPDATE_INTERVAL_TICKS = 20;

    private static final String OBJECTIVE_NAME = "avp_hive_stats";

    private static final String ROW_PREFIX = "avp_hive_stats_";

    private static final int MAX_ROWS = 15;

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private static Objective previousSidebarObjective;

    private static int tickCounter;

    private HiveStatsSidebarHandler() {}

    public static void tick(MinecraftServer server) {
        if (ENABLED_PLAYERS.isEmpty()) {
            return;
        }
        if (++tickCounter < UPDATE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        for (var player : server.getPlayerList().getPlayers()) {
            if (ENABLED_PLAYERS.contains(player.getUUID())) {
                update(player);
            }
        }
    }

    public static boolean isEnabled(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        var scoreboard = player.server.getScoreboard();
        if (enabled) {
            if (ENABLED_PLAYERS.isEmpty()) {
                previousSidebarObjective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            }
            ENABLED_PLAYERS.add(player.getUUID());
            update(player);
            return;
        }

        ENABLED_PLAYERS.remove(player.getUUID());
        clearRows(scoreboard);
        if (ENABLED_PLAYERS.isEmpty() && scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == objective(scoreboard)) {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, previousSidebarObjective);
            previousSidebarObjective = null;
        }
    }

    private static void update(ServerPlayer player) {
        var scoreboard = player.server.getScoreboard();
        var objective = objective(scoreboard);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        clearRows(scoreboard);

        var location = HiveLocationRegistry.INSTANCE.findNearestInDim(player.level().dimension(), player.blockPosition());
        var lines = location == null ? noHiveLines() : hiveLines(player, location);
        for (var i = 0; i < Math.min(MAX_ROWS, lines.size()); i++) {
            setLine(scoreboard, objective, i, lines.get(i));
        }
    }

    private static Objective objective(ServerScoreboard scoreboard) {
        var objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            return objective;
        }
        return scoreboard.addObjective(
            OBJECTIVE_NAME,
            ObjectiveCriteria.DUMMY,
            Component.literal("Nearest Hive").withStyle(ChatFormatting.DARK_PURPLE),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            BlankFormat.INSTANCE
        );
    }

    private static void clearRows(ServerScoreboard scoreboard) {
        var objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }
        for (var i = 0; i < MAX_ROWS; i++) {
            scoreboard.resetSinglePlayerScore(rowHolder(i), objective);
        }
    }

    private static void setLine(ServerScoreboard scoreboard, Objective objective, int index, Component line) {
        var score = scoreboard.getOrCreatePlayerScore(rowHolder(index), objective);
        score.set(MAX_ROWS - index);
        score.display(line);
        score.numberFormatOverride(BlankFormat.INSTANCE);
    }

    private static ScoreHolder rowHolder(int index) {
        return ScoreHolder.forNameOnly(ROW_PREFIX + index);
    }

    private static List<Component> noHiveLines() {
        return List.of(Component.literal("No hive in this dimension").withStyle(ChatFormatting.GRAY));
    }

    private static List<Component> hiveLines(ServerPlayer player, HiveLocation location) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var biomassCap = BiomassIncome.biomassCap(location, config);
        var pop = CastePopulation.totalTrackedPopulation(location);
        var popCap = config.populationPerChunk() * location.claimedChunks().size();
        var loaded = location.loadedMembersByType().values().stream().mapToInt(Set::size).sum();
        var reserves = location.localReserves().getReliableCount();
        var center = location.centerPos();
        var distance = Math.round(Math.sqrt(center.distSqr(player.blockPosition())));
        var bossBar = location.bossBar();
        var state = !location.isAlive()
            ? "dead"
            : location.isInhibited()
                ? "inhibited"
                : bossBar != null && bossBar.isAngry()
                    ? "angry"
                    : "alive";

        var lines = new ArrayList<Component>();
        lines.add(Component.literal(shortId(location.id().value().toString())).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("State: " + state + "  " + distance + "m").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Biomass: " + location.biomass() + "/" + biomassCap).withStyle(ChatFormatting.GREEN));
        lines.add(Component.literal("Pop: " + pop + "/" + popCap).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Loaded: " + loaded + "  Res: " + reserves).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Chunks: " + location.claimedChunks().size()).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("Royal: " + location.royalJelly() + "/" + JellyProduction.royalJellyCap(location)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Scourge: " + location.scourgeJelly() + "/" + JellyProduction.scourgeJellyCap(location)).withStyle(ChatFormatting.DARK_GREEN));
        lines.add(Component.literal("Vents: " + location.ventManager().ventCount()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Repro: " + location.reproductiveEstablished()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Leader: " + shortUuid(location.leadership().getLeaderIdOrNull())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Center: " + center.toShortString()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static String shortId(String id) {
        var slash = id.lastIndexOf('/');
        return slash >= 0 && slash + 1 < id.length() ? id.substring(slash + 1) : id;
    }

    private static String shortUuid(UUID uuid) {
        return uuid == null ? "none" : uuid.toString().substring(0, 8);
    }
}
