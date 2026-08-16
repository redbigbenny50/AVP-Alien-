package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.data_sync.v1.DataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * THE QUEEN'S DEFENSIVE SCREAM - a scripted panic at two fixed wounds, not an attack she chooses.
 * <p>
 * [stated] "it activates as two points for the queen... when the queen has lost 1/4 her total health and when she is
 * down to 25% of her total health so near death. When she screams it stuns the player and all attackers for the
 * duration of the animation. While this happens 3 praetoraians are summoned to her defense and spawn about 12 blocks
 * near the player. if there are multiple players involved it splits amoung them. the scream also has a cooldown of 180s
 * incase her health goes up and then drops again so it doesnt play in a loop the player cant get past."
 * </p>
 * <h2>⚠⚠ THIS IS NOT AN AttackType, AND THAT IS DELIBERATE</h2> Everything else she does is a swing the attack router
 * picks when a target is in reach. This fires off a HEALTH THRESHOLD with no target, no reach and no rotation - routing
 * it through the attack system would mean it could only happen while she was already mid-fight and in range of someone,
 * which is exactly when she least needs rescuing.
 * <h2>⚠ TWO LATCHES AND A COOLDOWN, BECAUSE THEY SOLVE DIFFERENT PROBLEMS</h2> The latches stop ONE threshold re-firing
 * while she sits below it. The cooldown stops the SECOND threshold firing moments after the first, and covers his
 * stated case - healing back up and dropping again. Either alone leaves a hole: latches alone let a
 * healed-and-re-wounded queen scream twice in seconds, and a cooldown alone lets her scream once at 75% and never again
 * at 25% if the fight is quick.
 */
public final class QueenScreamDefense {

    /**
     * ⭐⭐ WHAT A SCREAMING ROYAL HAS TO PROVIDE. The empress screams too and summons FIVE rather than three, but she
     * extends {@code Xenomorph} and NOT {@code Queen} - so the shared behaviour is reached through this rather than by
     * copying the whole class into her package, where the two would drift apart at the first tuning change.
     */
    public interface ScreamingRoyal {

        DataAccessor<Integer> screamCooldownTicks();

        DataAccessor<Boolean> screamedAtFirstThreshold();

        DataAccessor<Boolean> screamedAtSecondThreshold();

        /** [stated] the queen calls three, the empress five. */
        int praetoriansSummoned();
    }

    /** [stated] "lost 1/4 her total health" and "down to 25% of her total health". */
    private static final float FIRST_THRESHOLD = 0.75F;

    private static final float SECOND_THRESHOLD = 0.25F;

    public static final int SCREAM_COOLDOWN_TICKS = 180 * 20;

    /** The authored clip is 1.75s; the stun lasts exactly as long as the animation. */
    public static final int SCREAM_DURATION_TICKS = 35;

    /** [stated] "spawn about 12 blocks near the player". */
    private static final int SUMMON_DISTANCE = 12;

    /** How far out she looks for who to stun and who to answer. */
    private static final double SCREAM_RADIUS = 32.0;

    /** ⚠ Louder than the chat-message use of the same sound, which is what he asked for. */
    private static final float SCREAM_VOLUME = 3.0F;

    private QueenScreamDefense() {}

    /**
     * @return true if she screamed this tick, so the caller can drive the animation
     */
    public static <T extends Xenomorph & ScreamingRoyal> boolean tick(T queen) {
        if (!(queen.level() instanceof ServerLevel level) || !queen.isAlive()) {
            return false;
        }

        var cooldown = queen.screamCooldownTicks().get();

        if (cooldown > 0) {
            queen.screamCooldownTicks().set(cooldown - 1);
        }

        var fraction = queen.getHealth() / queen.getMaxHealth();

        // ⚠ LATCHES CLEAR ON THE WAY BACK UP. Without this a queen who healed past a threshold could never scream at it
        // again, and his "incase her health goes up and then drops again" case would be silently dead.
        if (fraction > FIRST_THRESHOLD) {
            queen.screamedAtFirstThreshold().set(false);
        }

        if (fraction > SECOND_THRESHOLD) {
            queen.screamedAtSecondThreshold().set(false);
        }

        if (queen.screamCooldownTicks().get() > 0) {
            return false;
        }

        if (fraction <= SECOND_THRESHOLD && !queen.screamedAtSecondThreshold().get()) {
            queen.screamedAtSecondThreshold().set(true);
            // ⚠ The first latch is set too: crossing straight past both in one hit must not queue a second scream.
            queen.screamedAtFirstThreshold().set(true);
            scream(level, queen);
            return true;
        }

        if (fraction <= FIRST_THRESHOLD && !queen.screamedAtFirstThreshold().get()) {
            queen.screamedAtFirstThreshold().set(true);
            scream(level, queen);
            return true;
        }

        return false;
    }

    private static <T extends Xenomorph & ScreamingRoyal> void scream(ServerLevel level, T queen) {
        queen.screamCooldownTicks().set(SCREAM_COOLDOWN_TICKS);

        level.playSound(
            null,
            queen.getX(),
            queen.getY(),
            queen.getZ(),
            AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(),
            SoundSource.HOSTILE,
            SCREAM_VOLUME,
            1.0F
        );

        var players = stunEveryoneAgainstHer(level, queen);
        summonDefenders(level, queen, players);
    }

    /**
     * Stuns everything hostile to her within earshot and reports the PLAYERS among them.
     *
     * @return the players stunned, which is also the list the summon splits across
     */
    private static <T extends Xenomorph & ScreamingRoyal> List<Player> stunEveryoneAgainstHer(ServerLevel level, T queen) {
        var players = new ArrayList<Player>();
        var box = new AABB(queen.blockPosition()).inflate(SCREAM_RADIUS);

        for (var victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
            // ⚠ HER OWN KIND ARE NEVER STUNNED. A scream that froze the praetorians it just summoned would be a
            // liability rather than a defence, and the same applies to every hive member already fighting for her.
            if (victim instanceof Alien) {
                continue;
            }

            if (victim instanceof Player player) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }

                players.add(player);
            } else if (!isAttackingHer(victim, queen)) {
                // [stated] "the player and all attackers" - a passing cow is not an attacker.
                continue;
            }

            victim.addEffect(
                new MobEffectInstance(AlienMobEffects.getStunnedHolder(), SCREAM_DURATION_TICKS, 0, false, false, true)
            );
        }

        return players;
    }

    private static boolean isAttackingHer(LivingEntity victim, Xenomorph queen) {
        if (victim.getLastHurtMob() == queen) {
            return true;
        }

        return victim instanceof Mob mob && mob.getTarget() == queen;
    }

    /**
     * [stated] "3 praetoraians are summoned to her defense and spawn about 12 blocks near the player. if there are
     * multiple players involved it splits amoung them."
     * <p>
     * ⚠ THEY SPAWN NEAR THE PLAYER, NOT NEAR HER - that is the whole point. Three praetorians appearing beside the
     * queen would just join a fight the player is already winning at range; appearing beside the PLAYER cuts off the
     * retreat, which is what a panicking queen wants.
     * </p>
     * <p>
     * ⚠ WITH NO PLAYERS PRESENT SHE STILL GETS HER GUARD, spawned around herself. She can be worn down by mobs, and a
     * defence that only works against players would be a strange hole.
     * </p>
     */
    private static <T extends Xenomorph & ScreamingRoyal> void summonDefenders(ServerLevel level, T queen, List<Player> players) {
        var praetorianType = Praetorian.getType(queen.getVariant());

        if (praetorianType == null) {
            return;
        }

        for (var index = 0; index < queen.praetoriansSummoned(); index++) {
            // Round-robin across the players, so 3 among 2 gives 2/1 rather than all on whoever was found first.
            var anchor = players.isEmpty() ? queen : players.get(index % players.size());
            var spawnPos = findSpawnNear(level, anchor.blockPosition(), queen);

            if (spawnPos == null) {
                continue;
            }

            var praetorian = praetorianType.create(level);

            if (praetorian == null) {
                continue;
            }

            praetorian.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, queen.getYRot(), 0.0F);

            if (praetorian instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);

                if (anchor != queen) {
                    mob.setTarget(anchor);
                }
            }

            level.addFreshEntity(praetorian);
        }
    }

    /**
     * A standable spot roughly {@link #SUMMON_DISTANCE} out from the anchor.
     * <p>
     * ⚠ RANDOM BEARING, THEN A VERTICAL SEARCH. A fixed offset would stack all three in one place and drop them inside
     * whatever wall happened to be there; the height scan is what keeps them out of ceilings and off cliff faces.
     * </p>
     */
    private static BlockPos findSpawnNear(ServerLevel level, BlockPos anchor, Xenomorph queen) {
        var random = queen.getRandom();

        for (var attempt = 0; attempt < 12; attempt++) {
            var angle = random.nextDouble() * Math.PI * 2.0;
            var x = anchor.getX() + (int) Math.round(Math.cos(angle) * SUMMON_DISTANCE);
            var z = anchor.getZ() + (int) Math.round(Math.sin(angle) * SUMMON_DISTANCE);

            for (var dy = 4; dy >= -8; dy--) {
                var candidate = new BlockPos(x, anchor.getY() + dy, z);

                if (
                    level.getBlockState(candidate.below()).isSolid()
                        && level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.above()).isAir()
                        && level.getBlockState(candidate.above(2)).isAir()
                ) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
