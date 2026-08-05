package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.hive.dimension.EndStyleHiveRules;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE LEADERSHIP DUEL - end-style dimensions only. [stated] "its possible this way that two empress are made. in that
 * even if they are close they will try to fight eachother so only 1 lives. if they cant fight then the second empress
 * goes into exile." And: "the other aliens dont join in the fight they will observe but not interfere. this is a battle
 * for leadership and only the strongest survives."
 * <p>
 * MECHANICS. A periodic per-level sweep finds loaded, living, non-exiled empresses of the SAME VARIANT answering
 * DIFFERENT crowns. Two such rivals within engagement range are set on each other and flagged as DUELISTS - and while
 * the duel flag stands, no other xenomorph may target either of them (the entity base consults {@link #isDuelist}),
 * their mutual damage is excluded from the siege clock at the same choke point creative hits already are, and both
 * garrisons simply watch. Players are not bound by the ritual.
 * <p>
 * THE PATIENCE CLOCK. Rivals who share a dimension but cannot meet (separate islands, never loaded together) do not
 * stand off forever: once both have been known to this sweep for {@link #PATIENCE_TICKS} without the duel resolving,
 * the JUNIOR - the later crown, by {@link Empress#crownedAtGameTime()} - is exiled: she surrenders her lineage's crown
 * (the network reconcile collapses her influence) and becomes the remnant boss the exile system already stages. The
 * first crown is legitimate; the challenger who could not take it by force yields the field.
 */
public final class EndEmpressDuel {

    private EndEmpressDuel() {}

    /** Sweep cadence - the duel is a rare event, not a hot path. */
    private static final long SWEEP_INTERVAL_TICKS = 100L;

    /** Rivals within this range (blocks) are set on each other. */
    private static final double ENGAGE_RANGE = 128.0;

    /** Real patience before the junior yields by exile: 10 minutes of game time with both crowns standing. */
    private static final long PATIENCE_TICKS = 10L * 60L * 20L;

    /** Loaded duelists this sweep - transient; consulted by the entity base's targeting. */
    private static final java.util.Set<UUID> DUELISTS = new java.util.HashSet<>();

    /** First tick each rival PAIR (key: lower uuid + "|" + higher uuid) was seen unresolved. Transient. */
    private static final Map<String, Long> PAIR_SINCE = new HashMap<>();

    /** True while this entity is one half of a running leadership duel - other xenomorphs must not target it. */
    public static boolean isDuelist(UUID id) {
        return DUELISTS.contains(id);
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % SWEEP_INTERVAL_TICKS != 0L || !EndStyleHiveRules.isEndStyle(level)) {
            return;
        }

        var empresses = new ArrayList<Empress>();
        for (var player : level.players()) {
            // Bounded search around players - empresses only matter loaded, and loaded means near someone.
            var box = new AABB(player.blockPosition()).inflate(192.0);
            for (var empress : level.getEntitiesOfClass(Empress.class, box, e -> e.isAlive() && !e.isExiled())) {
                if (!empresses.contains(empress)) {
                    empresses.add(empress);
                }
            }
        }

        DUELISTS.clear();
        if (empresses.size() < 2) {
            PAIR_SINCE.clear();
            return;
        }

        var now = level.getGameTime();
        var pairsSeen = new java.util.HashSet<String>();

        for (int i = 0; i < empresses.size(); i++) {
            for (int j = i + 1; j < empresses.size(); j++) {
                var a = empresses.get(i);
                var b = empresses.get(j);
                if (a.getVariant() != b.getVariant()) {
                    continue; // different strains settle it as strains do - this ritual is for one species' crown.
                }
                var crownA = crownOf(a);
                var crownB = crownOf(b);
                if (crownA == null || crownB == null || crownA.equals(crownB)) {
                    continue; // uncrowned, or the same network - no rivalry.
                }

                var key = pairKey(a.getUUID(), b.getUUID());
                pairsSeen.add(key);
                PAIR_SINCE.putIfAbsent(key, now);

                if (a.distanceToSqr(b) <= ENGAGE_RANGE * ENGAGE_RANGE) {
                    DUELISTS.add(a.getUUID());
                    DUELISTS.add(b.getUUID());
                    if (a.getTarget() != b) {
                        a.setTarget(b);
                    }
                    if (b.getTarget() != a) {
                        b.setTarget(a);
                    }
                    continue;
                }

                if (now - PAIR_SINCE.get(key) >= PATIENCE_TICKS) {
                    // They cannot meet. The junior crown yields the field.
                    var junior = a.crownedAtGameTime() >= b.crownedAtGameTime() ? a : b;
                    exileJunior(junior);
                    PAIR_SINCE.remove(key);
                }
            }
        }
        PAIR_SINCE.keySet().retainAll(pairsSeen);
    }

    private static void exileJunior(Empress junior) {
        junior.exile();
        for (var factionId : Alien.MOD.factions().getFactionIds(junior.getUUID())) {
            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof LineageFactionData lineage
                    && junior.getUUID().equals(lineage.empressId())
            ) {
                lineage.setEmpressId(null);
                lineage.markDirty();
            }
        }
        Alien.LOGGER.info(
            "End: leadership could not be settled in combat - the junior empress {} goes into exile. One throne.",
            junior.getUUID()
        );
    }

    private static UUID crownOf(Empress empress) {
        for (var factionId : Alien.MOD.factions().getFactionIds(empress.getUUID())) {
            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof LineageFactionData lineage
                    && empress.getUUID().equals(lineage.empressId())
            ) {
                return lineage.empressId();
            }
        }
        return null;
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
