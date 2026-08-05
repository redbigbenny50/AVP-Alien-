package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.DamageApplicator;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The front kick - a barrier breaker, not a fighting move.
 * <p>
 * [stated] "if theres a wall or obstical infront of him he uses the front kick to knock it down it does a block break
 * of a 5x5 area infront of him. now this is for actual walls or barricades not solid mountains or cliffs hes not using
 * this to tunnel. so it would be walls about 4 blocks in depth with an open area on the other side."
 * <p>
 * <b>The mountain test is the whole design.</b> Before the kick will even offer itself, it walks the blocks along her
 * facing and demands the obstruction be a WALL: solid for at most {@value #MAX_WALL_DEPTH_IN_BLOCKS} blocks, with open
 * space behind it. A cliff or hillside is solid for more than that and simply fails the check, so the attack never
 * becomes a tunnelling tool - she cannot chew into terrain with it, only through something built thin enough to have a
 * far side.
 * <p>
 * The scan works on her CARDINAL facing rather than a free-angle ray. Walls and barricades are built on the grid, so a
 * snapped direction measures depth honestly; a diagonal ray through a 1-block wall reads as thicker than it is and
 * would refuse perfectly good walls.
 * <p>
 * Timing from {@code attack.frontkick} (0.7917s = 16 ticks): the knee chambers high at 0.125s, then the leg pistons out
 * and the lower body braces back hard (-67.5 degrees) at 0.2917s. That brace is the stomp - tick 6.
 */
public final class HarbingerFrontKickAttack {

    /** How far ahead she will look for something to kick down. */
    private static final int WALL_SEARCH_DISTANCE_IN_BLOCKS = 3;

    /** [stated] "walls about 4 blocks in depth" - anything thicker is terrain, and terrain is not hers to remove. */
    private static final int MAX_WALL_DEPTH_IN_BLOCKS = 4;

    /** How much clear space has to sit behind the wall before it counts as having a far side. */
    private static final int REQUIRED_OPEN_DEPTH_IN_BLOCKS = 2;

    /** [stated] "a 5x5 area infront of him" - 5 wide by 5 tall, punched through the full depth of the wall. */
    private static final int BREACH_HALF_WIDTH = 2;

    private static final int BREACH_HEIGHT = 5;

    /** Height she sights along when looking for the wall - chest level, not her feet. */
    private static final int SIGHT_HEIGHT_OFFSET = 1;

    private static final int COOLDOWN_IN_TICKS = 30 * 20;

    /** Contact frame: 0.2917s of a 0.7917s clip. */
    private static final float DAMAGE_POINT_PERCENT = 6F / HarbingerAnimationRefs.FRONT_KICK_DURATION_TICKS;

    public static final AttackType ATTACK = AttackType.builder("harbinger_front_kick")
        .requiresAllLegs()
        .defaultDurationInTicks(HarbingerAnimationRefs.FRONT_KICK_DURATION_TICKS)
        .damageThresholdPercent(DAMAGE_POINT_PERCENT)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .activationCondition(HarbingerFrontKickAttack::hasBreakableBarrierAhead)
        .damageApplicator(DamageApplicator.NOOP)
        .executorFactory(Executor::new)
        .build();

    private HarbingerFrontKickAttack() {
        throw new UnsupportedOperationException();
    }

    /**
     * True when something directly ahead is a wall she is allowed to knock down. Runs off the GOAP sensor, but only
     * after the cooldown gate, so the block reads happen at most once per tick per off-cooldown harbinger and only a
     * handful of them.
     */
    private static boolean hasBreakableBarrierAhead(Xenomorph xenomorph) {
        if (!xenomorph.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }

        return measureWallDepth(xenomorph) > 0;
    }

    /**
     * Depth of the wall in front of her in blocks, or 0 if what is ahead is not a wall: nothing there, something she
     * may not break, or terrain too thick to have a far side.
     */
    private static int measureWallDepth(Xenomorph xenomorph) {
        var level = xenomorph.level();
        var facing = Direction.fromYRot(xenomorph.getYRot());
        var sightPos = xenomorph.blockPosition().above(SIGHT_HEIGHT_OFFSET);

        var wallStart = 0;

        for (var distance = 1; distance <= WALL_SEARCH_DISTANCE_IN_BLOCKS; distance++) {
            if (isObstruction(level, sightPos.relative(facing, distance))) {
                wallStart = distance;
                break;
            }
        }

        if (wallStart == 0) {
            return 0;
        }

        var depth = 0;

        while (depth < MAX_WALL_DEPTH_IN_BLOCKS && isObstruction(level, sightPos.relative(facing, wallStart + depth))) {
            depth++;
        }

        // Still solid after the maximum depth: this is terrain, not a barricade. Refuse - she is not a tunnelling tool.
        if (isObstruction(level, sightPos.relative(facing, wallStart + depth))) {
            return 0;
        }

        // The far side has to actually be somewhere worth breaking through to, at both stride and head height.
        for (var beyond = 0; beyond < REQUIRED_OPEN_DEPTH_IN_BLOCKS; beyond++) {
            var pos = sightPos.relative(facing, wallStart + depth + beyond);

            if (isObstruction(level, pos) || isObstruction(level, pos.above())) {
                return 0;
            }
        }

        // Nothing in the wall may be shatterable-proof, or she would kick a bedrock-cored wall forever.
        return canShatterAny(level, xenomorph, facing, wallStart, depth) ? depth : 0;
    }

    private static boolean canShatterAny(Level level, Xenomorph xenomorph, Direction facing, int wallStart, int depth) {
        var origin = xenomorph.blockPosition();

        for (var layer = 0; layer < depth; layer++) {
            for (var lateral = -BREACH_HALF_WIDTH; lateral <= BREACH_HALF_WIDTH; lateral++) {
                for (var height = 0; height < BREACH_HEIGHT; height++) {
                    var pos = breachPos(origin, facing, wallStart + layer, lateral, height);

                    if (canShatter(level, pos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static BlockPos breachPos(BlockPos origin, Direction facing, int distance, int lateral, int height) {
        return origin
            .relative(facing, distance)
            .relative(facing.getClockWise(), lateral)
            .above(height);
    }

    /** Anything that would stop her walking through - the thing a wall is made of. */
    private static boolean isObstruction(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);

        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * THE HARBINGER BREAK RULE. Her kick goes through things ordinary xenomorph digging cannot, so it gets its own,
     * much shorter blacklist rather than reusing {@code XENOMORPH_IMMUNE}:
     * <ul>
     * <li>anything with negative hardness - bedrock, barriers, end portal frames, command blocks - is unbreakable by
     * definition and needs no listing;</li>
     * <li>{@link AlienBlockTags#HARBINGER_UNBREAKABLE} catches the rest, the blocks that ARE breakable in principle but
     * should never fall to a mob: reinforced deepslate and friends. That tag is the extension point - adding a block to
     * it is a datapack edit, no code change;</li>
     * <li>block entities are skipped outright. Chests, spawners and machines are contents rather than masonry, and
     * vaporising someone's storage because they walled it in is not what "knock the wall down" means.</li>
     * </ul>
     */
    private static boolean canShatter(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);

        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }

        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        return !state.is(AlienBlockTags.HARBINGER_UNBREAKABLE);
    }

    private static void breachWall(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        // Re-measured at the contact frame, not carried from the wind-up: she may have been shoved, or the wall may
        // have changed, in the six ticks since the kick started.
        var depth = measureWallDepth(xenomorph);

        if (depth <= 0) {
            return;
        }

        var facing = Direction.fromYRot(xenomorph.getYRot());
        var origin = xenomorph.blockPosition();
        var wallStart = 0;
        var sightPos = origin.above(SIGHT_HEIGHT_OFFSET);

        for (var distance = 1; distance <= WALL_SEARCH_DISTANCE_IN_BLOCKS; distance++) {
            if (isObstruction(level, sightPos.relative(facing, distance))) {
                wallStart = distance;
                break;
            }
        }

        if (wallStart == 0) {
            return;
        }

        BlockState shattered = null;

        for (var layer = 0; layer < depth; layer++) {
            for (var lateral = -BREACH_HALF_WIDTH; lateral <= BREACH_HALF_WIDTH; lateral++) {
                for (var height = 0; height < BREACH_HEIGHT; height++) {
                    var pos = breachPos(origin, facing, wallStart + layer, lateral, height);

                    if (!canShatter(level, pos)) {
                        continue;
                    }

                    if (shattered == null) {
                        shattered = level.getBlockState(pos);
                    }

                    // No drops: a 5x5x4 breach would carpet the floor in items, and the point is the hole.
                    level.destroyBlock(pos, false, xenomorph);
                }
            }
        }

        playBreachEffects(level, xenomorph, facing, wallStart, shattered);
    }

    private static void playBreachEffects(
        ServerLevel level,
        Xenomorph xenomorph,
        Direction facing,
        int wallStart,
        BlockState shattered
    ) {
        var face = xenomorph.blockPosition().above(SIGHT_HEIGHT_OFFSET).relative(facing, wallStart).getCenter();

        level.playSound(null, face.x, face.y, face.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.0F, 0.7F);

        if (shattered != null) {
            level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, shattered),
                face.x,
                face.y,
                face.z,
                90,
                1.4,
                1.4,
                1.4,
                0.35
            );
        }
    }

    /** Counts ticks and punches the hole on the stomp frame. No damage stage - this attack fights masonry. */
    public static class Executor implements com.alien.common.gameplay.entity.living.alien.xenomorph.AttackExecutor {

        private static final int IMPACT_TICK = 6;

        private int elapsedTicks;

        private boolean kicked;

        @Override
        public int totalDurationInTicks(AttackType attack) {
            return HarbingerAnimationRefs.FRONT_KICK_DURATION_TICKS;
        }

        @Override
        public void onStart(
            Xenomorph entity,
            AttackType attack,
            net.minecraft.world.entity.@org.jetbrains.annotations.Nullable LivingEntity target
        ) {
            this.elapsedTicks = 0;
            this.kicked = false;
        }

        @Override
        public boolean onTick(Xenomorph entity, AttackType attack) {
            entity.getNavigation().stop();

            if (!kicked && elapsedTicks >= IMPACT_TICK) {
                breachWall(entity);
                kicked = true;
            }

            elapsedTicks++;

            return elapsedTicks < HarbingerAnimationRefs.FRONT_KICK_DURATION_TICKS;
        }
    }
}
