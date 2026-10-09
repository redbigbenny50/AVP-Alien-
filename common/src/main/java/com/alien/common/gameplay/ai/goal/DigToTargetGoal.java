package com.alien.common.gameplay.ai.goal;

import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class DigToTargetGoal extends Goal {

    private static final float DESTROY_TIME_LIMIT = 6F;

    private static final float BREAKING_SPEED = 50F;

    private final Mob mob;

    private final double reachDistance;

    private final double maxDistanceFromTarget;

    private final List<BlockPos> targetBlockPositions = new ArrayList<>();

    private final int parallelBlockBreakCount;

    private final Supplier<Boolean> canDig;

    private BlockState blockState = null;

    private Vec3 lastPosition = null;

    private int lastProgressTick = 0;

    private double lastDistanceToTarget = Double.MAX_VALUE;

    /**
     * How many times the block list may be refilled before the dig is written off.
     * <p>
     * [stated] "we dont want them to be chasing something unreachable forever though so keep that in mind." Each round
     * clears everything in reach, so a handful of rounds is a real attempt - and a target that is still unreachable
     * after that is not going to become reachable by grinding.
     * </p>
     */
    private static final int MAX_GATHER_ROUNDS = 6;

    /** How far above the mob a target counts as "up there", for the vertical assault. */
    private static final int VERTICAL_ASSAULT_MAX_RISE = 6;

    private int gatherRounds = 0;

    public DigToTargetGoal(Mob mob) {
        this(mob, 16, () -> true);
    }

    public DigToTargetGoal(Mob mob, double maxDistanceFromTarget, Supplier<Boolean> canDig) {
        this(mob, maxDistanceFromTarget, 1, canDig);
    }

    public DigToTargetGoal(Mob mob, double maxDistanceFromTarget, int parallelBlockBreakCount, Supplier<Boolean> canDig) {
        this.mob = mob;
        this.reachDistance = 4;
        this.maxDistanceFromTarget = maxDistanceFromTarget * maxDistanceFromTarget;
        this.parallelBlockBreakCount = parallelBlockBreakCount;
        this.canDig = canDig;
    }

    @Override
    public boolean canUse() {
        if (!canDig.get()) {
            return false;
        }

        var target = mob.getTarget();

        if (target == null || !mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }

        if (mob.getHealth() < (mob.getMaxHealth() / 2)) {
            return false;
        }

        return mob.onGround()
            && isStuck(target)
            && (mob.distanceToSqr(target) > 2d || !mob.getSensing().hasLineOfSight(target))
            && mob.distanceToSqr(target) < maxDistanceFromTarget;
    }

    @Override
    public boolean canContinueToUse() {
        if (!canDig.get()) {
            return false;
        }

        var target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // !!! AN EMPTY LIST USED TO END THE DIG OUTRIGHT, AND THAT IS THE REPORTED BUG. The list is built ONCE in
        // start() and never refilled, so the moment the first batch was cleared the goal stopped - reported as "they
        // dig a bit, reach the bottom of your position, sit there, and then forget about you". It is empty far too
        // early when the target is overhead, because gatherTargetBlocks casts a straight ray to the eyes and a
        // near-vertical ray hits almost nothing.
        //
        // ⭐ Empty now means "refill on the next tick", bounded by MAX_GATHER_ROUNDS so it still gives up.
        if (targetBlockPositions.isEmpty()) {
            return gatherRounds < MAX_GATHER_ROUNDS;
        }

        if (mob.getHealth() < (mob.getMaxHealth() / 2)) {
            return false;
        }

        return mob.onGround()
            && !mob.level().getBlockState(targetBlockPositions.getFirst()).isAir()
            && targetBlockPositions.getFirst().distSqr(mob.blockPosition()) < reachDistance * reachDistance;
    }

    @Override
    public void start() {
        gatherRounds = 0;
        var target = mob.getTarget();

        if (target == null) {
            return;
        }

        gatherTargetBlocks(target);

        if (!targetBlockPositions.isEmpty()) {
            initBlockBreak();
            mob.setAggressive(true);
        }
    }

    @Override
    public void stop() {
        if (!targetBlockPositions.isEmpty()) {
            targetBlockPositions.clear();
        }

        this.blockState = null;
        this.lastPosition = null;
        this.mob.setAggressive(false);
    }

    @Override
    public void tick() {
        var target = mob.getTarget();

        if (target == null) {
            return;
        }

        // ⭐ REFILL RATHER THAN STALL. canContinueToUse keeps the goal alive while there is budget left, so this is
        // where the next round of blocks is chosen. Re-gathering also re-aims: as the mob eats into the wall its
        // line to the target changes, which is how a dig follows a target that moved.
        if (targetBlockPositions.isEmpty()) {
            if (gatherRounds >= MAX_GATHER_ROUNDS) {
                return;
            }

            gatherRounds++;
            gatherTargetBlocks(target);

            if (targetBlockPositions.isEmpty()) {
                return; // nothing diggable in reach this round; canContinueToUse ends it once the budget runs out
            }
        }

        if (mob.tickCount % 4 == 0) {
            for (int i = 0; i < targetBlockPositions.size() && i < parallelBlockBreakCount; i++) {
                breakBlockAtPosition(targetBlockPositions.get(i), target);
            }
        }
    }

    private void breakBlockAtPosition(BlockPos pos, LivingEntity target) {
        mob.getLookControl().setLookAt(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);

        BlockBreakProgressManager.damage(mob.level(), pos, BREAKING_SPEED);

        var soundType = blockState.getSoundType();

        mob.level()
            .playSound(
                null,
                pos,
                soundType.getHitSound(),
                SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 8.0F,
                soundType.getPitch() * 0.5F
            );

        if (mob.level().getBlockState(pos).is(Blocks.AIR)) {
            targetBlockPositions.removeFirst();

            if (!targetBlockPositions.isEmpty()) {
                initBlockBreak();
            } else if (mob.distanceToSqr(target) > 2d && !mob.getSensing().hasLineOfSight(target)) {
                start();
            }
        }
    }

    private void initBlockBreak() {
        this.blockState = mob.level().getBlockState(targetBlockPositions.getFirst());
    }

    private void gatherTargetBlocks(@NotNull LivingEntity target) {
        int mobWidth = Mth.ceil(mob.getBbWidth());
        int mobHeight = Mth.ceil(mob.getBbHeight());

        for (var i = 0; i < mobHeight; i++) {
            for (var j = -mobWidth / 2; j <= mobWidth / 2; j++) { // Loop through the width
                for (var k = -mobWidth / 2; k <= mobWidth / 2; k++) {
                    var from = mob.position().add(j, i + 0.5d, k);
                    var to = target.getEyePosition(1f).add(j, i, k);
                    var clipContext = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob);
                    var rayTraceResult = mob.level().clip(clipContext);

                    if (
                        rayTraceResult.getType() == HitResult.Type.MISS
                            || targetBlockPositions.contains(rayTraceResult.getBlockPos())
                            || rayTraceResult.getBlockPos().getY() > 320
                    ) { // TODO: The max y level mobs can mine up to
                        continue;
                    }

                    double distance = mob.distanceToSqr(rayTraceResult.getLocation());

                    if (distance > reachDistance * reachDistance) {
                        continue;
                    }

                    BlockState state = mob.level().getBlockState(rayTraceResult.getBlockPos());

                    if (
                        state.hasBlockEntity()
                            || state.getDestroySpeed(mob.level(), rayTraceResult.getBlockPos()) == -1
                            // TODO: Make this configurable
                            || state.getBlock().defaultDestroyTime() >= DESTROY_TIME_LIMIT
                            // TODO: Make this configurable
                            || state.is(AlienBlockTags.XENOMORPH_IMMUNE)
                    ) {
                        continue;
                    }

                    // TODO: Blacklist blocks here

                    // TODO: Check if block is below walkable path and exclude.

                    targetBlockPositions.add(rayTraceResult.getBlockPos());
                }
            }
        }

        gatherVerticalAssault(target);

        Collections.reverse(targetBlockPositions);
    }

    /**
     * The way up when the target is standing above the mob.
     * <p>
     * !!! A NEAR-VERTICAL RAY HITS ALMOST NOTHING, which is why standing on a platform stopped them dead.
     * gatherTargetBlocks clips from the mob's body to the target's eyes; with the target overhead that line passes
     * through the one block under their feet and then out into open air, so the list came back nearly empty and the
     * goal ended. Reported as "they'll dig a bit - reach the bottom of your position - sit there".
     * </p>
     * <p>
     * 🚨 IT CARVES A RAMP, NOT A SHAFT, BECAUSE XENOMORPHS CANNOT CLIMB YET. [stated] "they dont have any climbing
     * pathing yet ... we built a proposal for one i think a month or 2 ago but we havnt applied it yet." A shaft dug
     * straight overhead would be actively WORSE than the bug: the mob opens a hole it has no way to ascend and stands
     * at the bottom of it. Every block chosen here is reachable by walking.
     * </p>
     * <p>
     * ⭐ TWO ROUTES, BOTH WALKABLE, BOTH THINGS HE ASKED FOR - [stated] "if they can reach the block by jumping or
     * digging out the block the players on or even digging next to them the same Y level and jumping over":
     * </p>
     * <ul>
     * <li>a STAIRCASE toward the target - one step across per step up, clearing the BODY space above each tread and
     * leaving the tread itself solid to stand on. A one-block rise is what the jump control already handles;</li>
     * <li>the block the target is STANDING ON, so the floor can simply be taken out from under them. This needs no
     * movement at all and is the reliable answer when the ramp cannot be cut.</li>
     * </ul>
     * <p>
     * ⚠ ONLY WHEN THE TARGET IS ACTUALLY ABOVE, and only within VERTICAL_ASSAULT_MAX_RISE. A horizontal dig is already
     * handled by the ray.
     * </p>
     */
    private void gatherVerticalAssault(@NotNull LivingEntity target) {
        var mobFeet = mob.blockPosition();
        var targetFeet = target.blockPosition();
        var rise = targetFeet.getY() - mobFeet.getY();

        if (rise < 1 || rise > VERTICAL_ASSAULT_MAX_RISE) {
            return;
        }

        // Take the floor out from under them. Cheapest answer and it needs no pathing at all.
        considerBlock(targetFeet.below());

        var toTarget = new Vec3(targetFeet.getX() - mobFeet.getX(), 0.0D, targetFeet.getZ() - mobFeet.getZ());
        if (toTarget.lengthSqr() < 1.0E-4D) {
            return; // directly overhead: there is no direction to build a ramp in, so the floor above is the only way
        }

        var step = toTarget.normalize();
        var mobHeight = Math.max(1, Mth.ceil(mob.getBbHeight()));

        // One across per one up. Clear the body space ABOVE each tread; the tread itself stays solid to walk on.
        for (var i = 1; i <= rise; i++) {
            var tread = mobFeet.offset(
                Mth.floor(step.x * i + 0.5D),
                i,
                Mth.floor(step.z * i + 0.5D)
            );

            for (var h = 0; h < mobHeight; h++) {
                considerBlock(tread.above(h));
            }
        }
    }

    /** Adds a position if it is in reach and diggable - the same rules the ray path applies. */
    private void considerBlock(BlockPos pos) {
        if (targetBlockPositions.contains(pos) || pos.getY() > 320) {
            return;
        }

        if (mob.distanceToSqr(Vec3.atCenterOf(pos)) > reachDistance * reachDistance * 4) {
            return; // the shaft is taller than arm's reach; the mob rises as it digs and picks up the rest later
        }

        var state = mob.level().getBlockState(pos);

        if (
            state.isAir()
                || state.hasBlockEntity()
                || state.getDestroySpeed(mob.level(), pos) == -1
                || state.getBlock().defaultDestroyTime() >= DESTROY_TIME_LIMIT
                || state.is(AlienBlockTags.XENOMORPH_IMMUNE)
        ) {
            return;
        }

        targetBlockPositions.add(pos);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public boolean isStuck(@NotNull LivingEntity target) {
        double currentDistanceToTarget = mob.distanceToSqr(target);

        // If very close, no need to consider stuck.
        if (currentDistanceToTarget <= mob.getBbWidth() * mob.getBbWidth()) {
            resetProgress();
            return false;
        }

        if (lastPosition == null) {
            // First call: record initial info.
            this.lastPosition = mob.position();
            this.lastProgressTick = mob.tickCount;
            this.lastDistanceToTarget = currentDistanceToTarget;
            return false;
        }

        // If mob moved meaningfully closer to target, update progress .
        if (currentDistanceToTarget < lastDistanceToTarget - 0.5d) {
            // must be significantly closer (0.5 blocks^2).
            this.lastPosition = mob.position();
            this.lastProgressTick = mob.tickCount;
            this.lastDistanceToTarget = currentDistanceToTarget;
            return false;
        }

        // If no meaningful progress in 4 ticks, consider stuck.
        return mob.tickCount - lastProgressTick >= 4;
    }

    private void resetProgress() {
        lastPosition = null;
        lastProgressTick = 0;
        lastDistanceToTarget = Double.MAX_VALUE;
    }
}
