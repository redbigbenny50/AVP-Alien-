package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Taking the war to the other hive. [stated] "Attackers would aim to destroy the queen and navigate to her if possible
 * while engaging in combat as it happens", "the hive will use vents to its advantage", and "because hives can be levels
 * apart the attacking forces should warp into the enemy hives territories."
 * <p>
 * WARP IS NOT A SHORTCUT HERE, IT IS THE ONLY ROUTE. Two hives at war routinely sit a hundred blocks apart vertically
 * with solid stone between them - there is no path to walk, so a marching order would grind against rock until the war
 * ended on its own. The strike force leaves through its own vent and arrives at the enemy's, which is also what makes
 * [stated] "the hive will use vents to its advantage" literal rather than decorative.
 * <p>
 * ONCE INSIDE THEY BEHAVE LIKE INVADERS, NOT LIKE A CURSOR. Every cycle each attacker either fights what is in front of
 * it or walks toward the queen's chamber - [stated] "navigate to her if possible WHILE ENGAGING IN COMBAT AS IT
 * HAPPENS". The march is what they do between fights, not instead of them.
 */
public final class WarOffensive {

    /** How often a hive considers sending, and re-orders the force already inside. */
    private static final long INTERVAL_TICKS = 100L;

    /** Ticks between waves leaving. A war is a siege, not a stream. */
    private static final long DISPATCH_COOLDOWN_TICKS = 1200L;

    /** Attackers per wave. Small enough that a defender's garrison is a real answer to it. */
    private static final int STRIKE_FORCE_SIZE = 4;

    /** Home is never emptied to attack - this many stay standing before a wave may leave. */
    private static final int HOME_GARRISON_FLOOR = 4;

    /** How far from the enemy queen an attacker looks for something to kill on the way in. */
    /**
     * How far from the strike a defender still counts as part of the battle.
     * <p>
     * Raised from 24 to a hive-scale figure: a hive is hundreds of blocks of corridor, and this is measured from the
     * centre of the strike, so 24 covered barely one chamber. Both sides ended up inside the same hive and still out of
     * each other's reach.
     */
    private static final double ENGAGEMENT_RANGE = 96.0;

    /** March speed toward the throne when nothing is in the way. */
    private static final double MARCH_SPEED = 1.15D;

    /** Live strike forces, one per attacking hive. Transient: a reload simply sends a fresh wave. */
    private static final Map<HiveLocation, Strike> STRIKES = Collections.synchronizedMap(new WeakHashMap<>());

    private WarOffensive() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAtWar() || !location.isAlive()) {
            STRIKES.remove(location);
            return;
        }

        var strike = STRIKES.get(location);
        if (strike != null) {
            var target = liveLocation(strike.targetId);
            if (target == null || !location.isAtWarWith(strike.targetId)) {
                STRIKES.remove(location);
                return;
            }
            commandStrike(level, strike, target);
            if (level.getGameTime() - strike.dispatchedTick < DISPATCH_COOLDOWN_TICKS) {
                return;
            }
            STRIKES.remove(location);
            return;
        }

        var target = pickTarget(level, location);
        if (target == null) {
            return;
        }

        var force = muster(level, location, target);
        if (force.isEmpty()) {
            return;
        }

        STRIKES.put(location, new Strike(target.id().value(), force, level.getGameTime()));
        Alien.LOGGER.info(
            "War: hive {} sent {} attacker(s) into {} — objective, the queen.",
            location.id(),
            force.size(),
            target.id()
        );
    }

    /**
     * Brings the survivors home when the fighting stops. [stated] "have them recall once the wars over so nothing is
     * left being in the old dead hive."
     * <p>
     * Deliberately identity-FREE: rather than trusting a strike record - which a wave outlives, and which a reload
     * loses entirely - this asks the only question that matters at the end of a war. Is one of my members standing in
     * ground that belonged to the enemy? Then it is a straggler in a corpse, and it goes home. That covers waves whose
     * record expired, waves sent before a restart, and members that wandered in on their own.
     */
    public static void recallFrom(ServerLevel level, HiveLocation location, @Nullable HiveLocation enemy) {
        STRIKES.remove(location);
        if (enemy == null) {
            return;
        }

        var home = location.centerPos();
        int recalled = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            for (var uuid : entry.getValue()) {
                if (!(level.getEntity(uuid) instanceof Mob member) || !member.isAlive()) {
                    continue;
                }
                if (!enemy.claimedChunks().contains(member.chunkPosition())) {
                    continue;
                }
                member.setTarget(null);
                member.getNavigation().stop();
                member.teleportTo(home.getX() + 0.5, location.hiveFloorY() + 1, home.getZ() + 0.5);
                recalled++;
            }
        }

        if (recalled > 0) {
            Alien.LOGGER.info(
                "War: hive {} recalled {} survivor(s) out of {}'s ground now the war is over.",
                location.id(),
                recalled,
                enemy.id()
            );
        }
    }

    /**
     * The enemy this hive can actually reach. An unloaded target is left to the wargame simulation - warping a strike
     * force into chunks nobody is ticking would just park it in limbo.
     */
    @Nullable
    private static HiveLocation pickTarget(ServerLevel level, HiveLocation location) {
        for (var enemyId : location.warEnemies()) {
            var enemy = liveLocation(enemyId);
            if (
                enemy != null
                    && enemy.dimension().equals(location.dimension())
                    && level.hasChunkAt(enemy.centerPos())
            ) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * Picks the wave and warps it in. Attackers come from the STANDING GARRISON rather than the bank: the mobilizer
     * refills home behind them, so an offensive costs the hive its field strength for a while instead of duplicating
     * bodies out of nowhere.
     */
    private static List<UUID> muster(ServerLevel level, HiveLocation location, HiveLocation target) {
        var available = new ArrayList<Mob>();
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (level.getEntity(uuid) instanceof Mob member && isDeployable(member)) {
                    available.add(member);
                }
            }
        }
        if (available.size() <= HOME_GARRISON_FLOOR) {
            return List.of();
        }

        // [stated] "they bring their heaviest hitter to hit the hardest enemies" - the wave is taken off the TOP of
        // the roster, so an assault is the hive's best, not whoever happened to be standing near the door.
        available.sort(Comparator.comparingDouble(WarOffensive::power).reversed());

        int sendable = Math.min(STRIKE_FORCE_SIZE, available.size() - HOME_GARRISON_FLOOR);
        var arrival = arrivalPoint(level, target);
        var force = new ArrayList<UUID>(sendable);
        for (var i = 0; i < sendable; i++) {
            var attacker = available.get(i);
            attacker.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
            force.add(attacker.getUUID());
        }
        return force;
    }

    /**
     * Where a wave lands: an enemy vent if the hive has one exposed, otherwise its floor. Arriving at the vent is the
     * doctrine - it puts the attackers where the defenders come out, at the mouth of the hive rather than on top of the
     * throne.
     */
    private static BlockPos arrivalPoint(ServerLevel level, HiveLocation target) {
        var vents = target.ventManager().ventsOfKind(VentKind.values());
        for (var vent : vents) {
            if (level.hasChunkAt(vent)) {
                return vent.above();
            }
        }
        var centre = target.centerPos();
        return new BlockPos(centre.getX(), target.hiveFloorY() + 1, centre.getZ());
    }

    /**
     * One cycle of orders for a force already inside. Each attacker fights whatever defender is closest - paired
     * heaviest-to-heaviest so the praetorian meets the praetorian - and marches on the queen's chamber when nothing is
     * in reach.
     */
    private static void commandStrike(ServerLevel level, Strike strike, HiveLocation target) {
        var attackers = new ArrayList<Mob>();
        for (var uuid : strike.force) {
            if (level.getEntity(uuid) instanceof Mob attacker && attacker.isAlive()) {
                attackers.add(attacker);
            }
        }
        if (attackers.isEmpty()) {
            return;
        }

        // Measure from the CENTRE OF THE STRIKE, not from attackers.get(0). A strike spreads out as it marches,
        // so anchoring the search on whichever attacker happened to be first in the list meant defenders standing
        // beside the rest of the force were invisible - and with no defender found, every attacker fell through to
        // "march to the throne" and stood there. [stated] "both are kinda just chilling inside the pure hive
        // because they are too far apart to beef - so there is no actual war just a silly stalemate".
        var strikeCentre = strikeCentre(attackers);
        var defenders = defendersNear(level, target, strikeCentre);
        attackers.sort(Comparator.comparingDouble(WarOffensive::power).reversed());
        defenders.sort(Comparator.comparingDouble(WarOffensive::power).reversed());

        var throne = target.centerPos();

        // EVERY attacker gets a defender while any defender is standing. The old rule paired by INDEX - attacker
        // i to defender i - so the moment attackers outnumbered defenders the surplus marched past a live enemy
        // to mill around the throne. Wrapping the index means a heavier force gangs up in waves instead, which is
        // what a hive war should look like.
        for (var i = 0; i < attackers.size(); i++) {
            var attacker = attackers.get(i);
            if (!defenders.isEmpty()) {
                attacker.setTarget(defenders.get(i % defenders.size()));
                continue;
            }
            if (attacker.getTarget() != null && attacker.getTarget().isAlive()) {
                continue; // already busy with something it found itself
            }
            attacker.getNavigation().moveTo(throne.getX(), target.hiveFloorY() + 1, throne.getZ(), MARCH_SPEED);
        }

        // AND COMMAND THE DEFENCE. Nothing here ever told a defender who to fight - the whole method only ever
        // spoke to attackers, and the defenders were left to notice an intruder with their own senses. Inside a
        // hive of corridors and chambers that frequently never happens, which is the other half of the stalemate:
        // [stated] "they dont actively target all the members when they are at war so they kind of just stand
        // around... how can all members die if they arent tracking them". A defender already in a fight is left
        // alone; the rest are put onto their nearest attacker.
        for (var defender : defenders) {
            if (defender.getTarget() != null && defender.getTarget().isAlive()) {
                continue;
            }
            Mob nearest = null;
            var bestDistance = Double.MAX_VALUE;
            for (var attacker : attackers) {
                if (!attacker.isAlive()) {
                    continue;
                }
                var distance = defender.distanceToSqr(attacker);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = attacker;
                }
            }
            if (nearest != null) {
                defender.setTarget(nearest);
            }
        }
    }

    /** The middle of the strike force, so the engagement search is not anchored on one arbitrary attacker. */
    private static BlockPos strikeCentre(List<Mob> attackers) {
        var x = 0;
        var y = 0;
        var z = 0;
        for (var attacker : attackers) {
            x += attacker.getBlockX();
            y += attacker.getBlockY();
            z += attacker.getBlockZ();
        }
        return new BlockPos(x / attackers.size(), y / attackers.size(), z / attackers.size());
    }

    /** Enemy members standing between the attackers and the throne. The queen counts - she is the objective. */
    private static List<Mob> defendersNear(ServerLevel level, HiveLocation target, BlockPos from) {
        var defenders = new ArrayList<Mob>();
        for (var entry : target.loadedMembersByType().entrySet()) {
            for (var uuid : entry.getValue()) {
                if (
                    level.getEntity(uuid) instanceof Mob defender
                        && defender.isAlive()
                        && defender.blockPosition().closerThan(from, ENGAGEMENT_RANGE)
                ) {
                    defenders.add(defender);
                }
            }
        }
        return defenders;
    }

    /** A rough weight class: what this member hits for, backed by how much it can take. */
    private static double power(LivingEntity member) {
        var damage = member.getAttribute(Attributes.ATTACK_DAMAGE);
        return (damage == null ? 0.0D : damage.getValue()) * 4.0D + member.getMaxHealth();
    }

    /** Deployable = alive, unburdened, not minding eggs. Members already fighting at home are left to it. */
    private static boolean isDeployable(Mob member) {
        return member.isAlive()
            && member.getTarget() == null
            && !member.isPassenger()
            && member.getPassengers().isEmpty()
            && !EggDutyGuard.isOnEggDuty(member);
    }

    @Nullable
    private static HiveLocation liveLocation(net.minecraft.resources.ResourceLocation id) {
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(id));
        return location != null && location.isAlive() ? location : null;
    }

    /** One hive's wave: who it is aimed at, who went, and when they left. */
    private record Strike(
        net.minecraft.resources.ResourceLocation targetId,
        List<UUID> force,
        long dispatchedTick
    ) {}
}
