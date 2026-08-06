package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackExecutor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.DamageApplicator;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** A committed, wall-breaching special attack for the Crusher. */
public final class CrusherChargeAttack {

    public static final int MIN_VISIBLE_RANGE_IN_BLOCKS = 4;

    public static final int MAX_RANGE_IN_BLOCKS = 24;

    private static final int WINDUP_DURATION_IN_TICKS = 20;

    private static final int OBSCURED_TARGET_BACKUP_TICKS = 20;

    private static final int CHARGE_DURATION_IN_TICKS = 5 * 20;

    private static final int MAX_GROUND_LOSS_TICKS = 12;

    private static final int COOLDOWN_IN_TICKS = 4 * 20;

    private static final int STUN_DURATION_IN_TICKS = 30;

    private static final float WALL_IMPACT_DAMAGE = 60F;

    private static final float OPEN_SPACE_DAMAGE_MULTIPLIER = 0.25F;

    private static final double INITIAL_CHARGE_SPEED_MULTIPLIER = 2.0D;

    private static final double MAX_CHARGE_SPEED_MULTIPLIER = 5.0D;

    private static final float INITIAL_TURN_RATE_DEGREES = 1.5F;

    private static final float MAX_SPEED_TURN_RATE_DEGREES = 0.35F;

    private static final double BACKUP_SPEED_MULTIPLIER = 1.25D;

    private static final double TERRAIN_PROBE_DISTANCE = 1.25D;

    private static final double MAX_TERRAIN_STEP_UP = 1.05D;

    private static final int TERRAIN_SEARCH_DEPTH = 4;

    private static final double HIT_RANGE_IN_BLOCKS = 2.5D;

    private static final double OPEN_SPACE_KNOCKBACK = 0.8D;

    private static final double SHIELD_BLOCK_KNOCKBACK = 1.5D;

    private static final double KNOCKBACK_VERTICAL_BOOST = 0.25D;

    public static final AttackType BACKUP = AttackType.builder("crusher_charge_backup")
        .requiresAllLegs()
        .defaultDurationInTicks(OBSCURED_TARGET_BACKUP_TICKS)
        .damageThresholdPercent(0F)
        .damageApplicator(DamageApplicator.NOOP)
        .build();

    public static final AttackType WINDUP = AttackType.builder("crusher_charge_windup")
        .requiresAllLegs()
        .defaultDurationInTicks(WINDUP_DURATION_IN_TICKS)
        .damageThresholdPercent(0F)
        .damageApplicator(DamageApplicator.NOOP)
        .build();

    public static final AttackType ATTACK = AttackType.builder("crusher_charge")
        .requiresAllLegs()
        .defaultDurationInTicks(CHARGE_DURATION_IN_TICKS)
        .damageThresholdPercent(0F)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_LUNGE)
        .damageApplicator(DamageApplicator.NOOP)
        .executorFactory(Executor::new)
        .build();

    private CrusherChargeAttack() {
        throw new UnsupportedOperationException();
    }

    public static class Executor implements AttackExecutor {

        private final Set<LivingEntity> carriedTargets = new LinkedHashSet<>();

        private int windupTicksRemaining;

        private int backupTicksRemaining;

        private int chargeTicksRemaining;

        private int chargeTicksElapsed;

        private int groundLossTicks;

        private float lockedYaw;

        private @Nullable LivingEntity chargeTarget;

        private boolean victimsReleased;

        @Override
        public int totalDurationInTicks(AttackType attack) {
            return OBSCURED_TARGET_BACKUP_TICKS + WINDUP_DURATION_IN_TICKS + CHARGE_DURATION_IN_TICKS + 1;
        }

        @Override
        public void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target) {
            windupTicksRemaining = WINDUP_DURATION_IN_TICKS;
            backupTicksRemaining = target != null && isTargetObscured(entity, target)
                ? OBSCURED_TARGET_BACKUP_TICKS
                : 0;
            chargeTicksRemaining = CHARGE_DURATION_IN_TICKS;
            chargeTicksElapsed = 0;
            groundLossTicks = 0;
            carriedTargets.clear();
            victimsReleased = false;
            chargeTarget = target;
            lockedYaw = target != null ? computeYawTowards(entity, target) : entity.getYRot();
            if (backupTicksRemaining > 0) {
                entity.transitionAttack(BACKUP, backupTicksRemaining + 1);
            } else {
                entity.transitionAttack(WINDUP, WINDUP_DURATION_IN_TICKS + 1);
            }
        }

        @Override
        public boolean onTick(Xenomorph entity, AttackType attack) {
            lockYaw(entity);

            if (backupTicksRemaining > 0) {
                moveBackward(entity);
                backupTicksRemaining--;

                if (backupTicksRemaining == 0) {
                    entity.transitionAttack(WINDUP, WINDUP_DURATION_IN_TICKS + 1);
                }

                return true;
            }

            if (windupTicksRemaining > 0) {
                var movement = entity.getDeltaMovement();
                entity.setDeltaMovement(0.0D, movement.y, 0.0D);
                windupTicksRemaining--;

                if (windupTicksRemaining == 0) {
                    entity.transitionAttack(attack, CHARGE_DURATION_IN_TICKS + 1);
                }

                return true;
            }

            pruneInvalidTargets(entity);

            if (chargeTicksRemaining-- <= 0) {
                releaseInOpenSpace(entity, true);
                return false;
            }

            chargeTicksElapsed++;
            steerTowardTarget(entity);
            lockYaw(entity);
            propel(entity);

            if (groundLossTicks > MAX_GROUND_LOSS_TICKS) {
                releaseInOpenSpace(entity, true);
                return false;
            }

            var shieldBlocker = collectTargetsInFront(entity);

            if (shieldBlocker != null) {
                resolveShieldBlock(entity, shieldBlocker);
                return false;
            }

            var carryingTargets = !carriedTargets.isEmpty();

            if (entity.horizontalCollision || hasWallAhead(entity, carryingTargets)) {
                damageWallInFront(entity);

                if (hasWallAhead(entity, carryingTargets)) {
                    resolveBlockedImpact(entity);
                    return false;
                }
            }

            positionCarriedTargets(entity);
            return true;
        }

        @Override
        public void onComplete(Xenomorph entity, AttackType attack) {
            if (!victimsReleased) {
                releaseInOpenSpace(entity, false);
            }

            carriedTargets.clear();
            chargeTarget = null;
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2D));
        }

        private void lockYaw(Xenomorph entity) {
            entity.setYRot(lockedYaw);
            entity.setYHeadRot(lockedYaw);
            entity.setYBodyRot(lockedYaw);
            entity.getNavigation().stop();
        }

        private void propel(Xenomorph entity) {
            var forward = forward(entity);
            var speedMultiplier = Mth.lerp(
                chargeProgress(),
                INITIAL_CHARGE_SPEED_MULTIPLIER,
                MAX_CHARGE_SPEED_MULTIPLIER
            );
            var speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedMultiplier;
            var currentMovement = entity.getDeltaMovement();
            var groundY = findGroundYAhead(entity, forward);
            var verticalSpeed = currentMovement.y;

            if (Double.isNaN(groundY)) {
                groundLossTicks++;
            } else {
                groundLossTicks = 0;
                var groundDelta = groundY - entity.getY();

                if (groundDelta < -0.05D) {
                    verticalSpeed = Mth.clamp(groundDelta * 0.6D, -0.5D, -0.05D);
                } else {
                    // Alien already has a 1.5-block step height. Let vanilla collision resolution climb uphill instead
                    // of applying upward velocity, which can suspend the Crusher above the terrain.
                    verticalSpeed = Math.min(currentMovement.y, 0.0D);
                }
            }

            entity.setDeltaMovement(forward.x * speed, verticalSpeed, forward.z * speed);
        }

        private void steerTowardTarget(Xenomorph entity) {
            if (
                chargeTarget == null
                    || chargeTarget.isRemoved()
                    || !chargeTarget.isAlive()
                    || carriedTargets.contains(chargeTarget)
            ) {
                return;
            }

            var desiredYaw = computeYawTowards(entity, chargeTarget);
            var yawDifference = Mth.wrapDegrees(desiredYaw - lockedYaw);
            var maximumTurn = Mth.lerp(
                (float) chargeProgress(),
                INITIAL_TURN_RATE_DEGREES,
                MAX_SPEED_TURN_RATE_DEGREES
            );
            lockedYaw += Mth.clamp(yawDifference, -maximumTurn, maximumTurn);
        }

        private double chargeProgress() {
            return Mth.clamp(chargeTicksElapsed / (double) CHARGE_DURATION_IN_TICKS, 0.0D, 1.0D);
        }

        private void moveBackward(Xenomorph entity) {
            var backward = forward(entity).reverse();
            var speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED) * BACKUP_SPEED_MULTIPLIER;
            var currentMovement = entity.getDeltaMovement();
            var groundY = findGroundYAhead(entity, backward);
            var verticalSpeed = currentMovement.y;

            if (!Double.isNaN(groundY)) {
                var groundDelta = groundY - entity.getY();
                verticalSpeed = groundDelta < -0.05D
                    ? Mth.clamp(groundDelta * 0.6D, -0.5D, -0.05D)
                    : Math.min(currentMovement.y, 0.0D);
            }

            entity.setDeltaMovement(backward.x * speed, verticalSpeed, backward.z * speed);
        }

        private static boolean isTargetObscured(Xenomorph entity, LivingEntity target) {
            var hit = entity.level()
                .clip(
                    new ClipContext(
                        entity.getEyePosition(),
                        target.getEyePosition(),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        entity
                    )
                );
            return hit.getType() == HitResult.Type.BLOCK;
        }

        private static double findGroundYAhead(Xenomorph entity, Vec3 forward) {
            var probeX = entity.getX() + forward.x * TERRAIN_PROBE_DISTANCE;
            var probeZ = entity.getZ() + forward.z * TERRAIN_PROBE_DISTANCE;
            var startY = Mth.floor(entity.getY()) + 1;
            var minimumY = Mth.floor(entity.getY()) - TERRAIN_SEARCH_DEPTH;

            for (var y = startY; y >= minimumY; y--) {
                var pos = BlockPos.containing(probeX, y, probeZ);
                var shape = entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos);

                if (shape.isEmpty()) {
                    continue;
                }

                var surfaceY = pos.getY() + shape.max(Direction.Axis.Y);

                if (surfaceY <= entity.getY() + MAX_TERRAIN_STEP_UP) {
                    return surfaceY;
                }
            }

            return Double.NaN;
        }

        private @Nullable Player collectTargetsInFront(Xenomorph entity) {
            var forward = forward(entity);
            var searchBox = entity.getBoundingBox()
                .expandTowards(forward.scale(HIT_RANGE_IN_BLOCKS))
                .inflate(entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.25D, entity.getBbWidth() * 0.5D);
            var damageSource = entity.damageSources().mobAttack(entity);
            var targets = entity.level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    target -> target != entity
                        && target.isAlive()
                        && !target.isPassenger()
                        && AlienPredicates.canTarget(entity, target)
                        && !target.isInvulnerableTo(damageSource)
                        && isInFront(entity, target, forward)
                        && entity.getSensing().hasLineOfSight(target)
                );

            for (var target : targets) {
                if (target instanceof Player player && isBlockingCharge(player, entity)) {
                    return player;
                }
            }

            carriedTargets.addAll(targets);
            return null;
        }

        private static boolean isBlockingCharge(Player player, Xenomorph entity) {
            if (!player.isBlocking()) {
                return false;
            }

            var look = player.getLookAngle();
            var horizontalLook = new Vec3(look.x, 0.0D, look.z);
            var towardCrusher = new Vec3(entity.getX() - player.getX(), 0.0D, entity.getZ() - player.getZ());

            return horizontalLook.lengthSqr() <= 1.0E-4D
                || towardCrusher.lengthSqr() <= 1.0E-4D
                || horizontalLook.normalize().dot(towardCrusher.normalize()) >= 0.0D;
        }

        private void resolveShieldBlock(Xenomorph entity, Player player) {
            entity.setDeltaMovement(Vec3.ZERO);

            if (!carriedTargets.isEmpty()) {
                releaseInOpenSpace(entity, false);
            }

            applyKnockback(player, forward(entity), SHIELD_BLOCK_KNOCKBACK);
            applyStun(entity);
            victimsReleased = true;
        }

        private void pruneInvalidTargets(Xenomorph entity) {
            var damageSource = entity.damageSources().mobAttack(entity);
            carriedTargets.removeIf(
                target -> target.isRemoved()
                    || !target.isAlive()
                    || target.isPassenger()
                    || !AlienPredicates.canTarget(entity, target)
                    || target.isInvulnerableTo(damageSource)
            );
        }

        private void positionCarriedTargets(Xenomorph entity) {
            var targets = new ArrayList<>(carriedTargets);
            var forward = forward(entity);
            var right = new Vec3(forward.z, 0.0D, -forward.x);
            var availableWidth = Math.max(entity.getBbWidth(), 1.0D);
            var slotWidth = targets.isEmpty() ? 0.0D : Math.min(0.8D, availableWidth / targets.size());

            for (var i = 0; i < targets.size(); i++) {
                var target = targets.get(i);
                var sideOffset = (i - (targets.size() - 1) * 0.5D) * slotWidth;
                var frontOffset = entity.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.15D;
                var position = entity.position()
                    .add(forward.scale(frontOffset))
                    .add(right.scale(sideOffset));

                target.setPos(position.x, entity.getY(), position.z);
                target.setDeltaMovement(entity.getDeltaMovement());
                target.hurtMarked = true;
            }
        }

        private void resolveBlockedImpact(Xenomorph entity) {
            entity.setDeltaMovement(Vec3.ZERO);

            if (carriedTargets.isEmpty()) {
                applyStun(entity);
                victimsReleased = true;
                return;
            }

            var damageSource = entity.damageSources().mobAttack(entity);
            var damage = (float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE);

            positionCarriedTargetsAgainstWall(entity);

            for (var target : carriedTargets) {
                target.hurt(damageSource, damage);
                applyStun(target);
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
            }

            carriedTargets.clear();
            victimsReleased = true;
        }

        private void releaseInOpenSpace(Xenomorph entity, boolean dealDamage) {
            var forward = forward(entity);
            var damageSource = entity.damageSources().mobAttack(entity);
            var damage = (float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * OPEN_SPACE_DAMAGE_MULTIPLIER;

            for (var target : carriedTargets) {
                if (dealDamage) {
                    target.hurt(damageSource, damage);
                }

                applyKnockback(target, forward, OPEN_SPACE_KNOCKBACK);
            }

            carriedTargets.clear();
            victimsReleased = true;
        }

        private static void applyStun(LivingEntity target) {
            target.addEffect(
                new MobEffectInstance(
                    AlienMobEffects.getStunnedHolder(),
                    STUN_DURATION_IN_TICKS,
                    0,
                    false,
                    false,
                    true
                )
            );
        }

        private static void applyKnockback(LivingEntity target, Vec3 direction, double strength) {
            target.knockback(strength, -direction.x, -direction.z);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, KNOCKBACK_VERTICAL_BOOST, 0.0D));
            target.hurtMarked = true;
        }

        private static boolean isInFront(Xenomorph entity, LivingEntity target, Vec3 forward) {
            var toTarget = target.position().subtract(entity.position());
            var horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
            return horizontal.lengthSqr() > 0.001D && forward.dot(horizontal.normalize()) >= 0.35D;
        }

        private static boolean hasWallAhead(Xenomorph entity, boolean carryingTargets) {
            return !frontCollisionPositions(entity, carryingTargets).isEmpty();
        }

        private static void damageWallInFront(Xenomorph entity) {
            if (!entity.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return;
            }

            for (var pos : impactBlockPositions(entity)) {
                var state = entity.level().getBlockState(pos);

                if (canDamageBlock(entity, pos, state)) {
                    BlockBreakProgressManager.damage(entity.level(), pos, WALL_IMPACT_DAMAGE);
                }
            }
        }

        private static Set<BlockPos> impactBlockPositions(Xenomorph entity) {
            var impactDirection = horizontalDirection(entity);
            var lateralDirection = impactDirection.getClockWise();
            var collisionPositions = frontCollisionPositions(entity, false);
            var center = collisionPositions.stream()
                .min(
                    (first, second) -> Double.compare(
                        distanceAlongImpactDirection(entity, first, impactDirection),
                        distanceAlongImpactDirection(entity, second, impactDirection)
                    )
                )
                .map(pos -> alignImpactCenter(entity.blockPosition(), pos, impactDirection))
                .orElseGet(() -> entity.blockPosition().relative(impactDirection));
            var positions = new LinkedHashSet<BlockPos>();

            for (var dy = 0; dy < 3; dy++) {
                for (var lateral = -1; lateral <= 1; lateral++) {
                    positions.add(center.above(dy).relative(lateralDirection, lateral));
                }
            }

            return positions;
        }

        private static double distanceAlongImpactDirection(Xenomorph entity, BlockPos pos, Direction direction) {
            var offsetX = pos.getX() + 0.5D - entity.getX();
            var offsetZ = pos.getZ() + 0.5D - entity.getZ();
            return offsetX * direction.getStepX() + offsetZ * direction.getStepZ();
        }

        private static BlockPos alignImpactCenter(BlockPos entityPos, BlockPos collisionPos, Direction direction) {
            return direction.getAxis() == Direction.Axis.X
                ? new BlockPos(collisionPos.getX(), entityPos.getY(), entityPos.getZ())
                : new BlockPos(entityPos.getX(), entityPos.getY(), collisionPos.getZ());
        }

        private static boolean canDamageBlock(Xenomorph entity, BlockPos pos, BlockState state) {
            return !state.isAir()
                && !state.canBeReplaced()
                && !state.hasBlockEntity()
                && state.getDestroySpeed(entity.level(), pos) >= 0.0F
                && !state.is(AlienBlockTags.XENOMORPH_IMMUNE)
                && !state.getCollisionShape(entity.level(), pos).isEmpty();
        }

        private static Set<BlockPos> frontCollisionPositions(Xenomorph entity, boolean carryingTargets) {
            var forward = forward(entity);
            var probe = entity.getBoundingBox().move(forward.scale(entity.getBbWidth() * 0.5D + 0.35D));
            var positions = new LinkedHashSet<BlockPos>();
            var minX = Mth.floor(probe.minX + 1.0E-4D);
            var minY = Mth.floor(probe.minY + 1.0E-4D);
            var minZ = Mth.floor(probe.minZ + 1.0E-4D);
            var maxX = Mth.floor(probe.maxX - 1.0E-4D);
            var maxY = Mth.floor(probe.maxY - 1.0E-4D);
            var maxZ = Mth.floor(probe.maxZ - 1.0E-4D);

            for (var x = minX; x <= maxX; x++) {
                for (var y = minY; y <= maxY; y++) {
                    for (var z = minZ; z <= maxZ; z++) {
                        var pos = new BlockPos(x, y, z);
                        var state = entity.level().getBlockState(pos);
                        var shape = state.getCollisionShape(entity.level(), pos);

                        if (
                            !state.canBeReplaced()
                                && !shape.isEmpty()
                                && shape.bounds().move(pos).intersects(probe)
                                && (carryingTargets || !isWalkableTerrainStep(entity, pos, shape.max(Direction.Axis.Y)))
                        ) {
                            positions.add(pos);
                        }
                    }
                }
            }

            return positions;
        }

        private void positionCarriedTargetsAgainstWall(Xenomorph entity) {
            var wallBlocks = frontCollisionPositions(entity, true);

            if (wallBlocks.isEmpty()) {
                positionCarriedTargets(entity);
                return;
            }

            var targets = new ArrayList<>(carriedTargets);
            var direction = horizontalDirection(entity);
            var forward = forward(entity);
            var right = new Vec3(forward.z, 0.0D, -forward.x);
            var slotWidth = targets.isEmpty() ? 0.0D : Math.min(0.8D, Math.max(entity.getBbWidth(), 1.0D) / targets.size());
            var wallFace = switch (direction) {
                case EAST -> wallBlocks.stream().mapToInt(BlockPos::getX).min().orElse(entity.blockPosition().getX());
                case WEST -> wallBlocks.stream().mapToInt(BlockPos::getX).max().orElse(entity.blockPosition().getX()) + 1.0D;
                case SOUTH -> wallBlocks.stream().mapToInt(BlockPos::getZ).min().orElse(entity.blockPosition().getZ());
                case NORTH -> wallBlocks.stream().mapToInt(BlockPos::getZ).max().orElse(entity.blockPosition().getZ()) + 1.0D;
                default -> throw new IllegalStateException("Charge wall direction must be horizontal");
            };

            for (var i = 0; i < targets.size(); i++) {
                var target = targets.get(i);
                var sideOffset = (i - (targets.size() - 1) * 0.5D) * slotWidth;
                var sidePosition = entity.position().add(right.scale(sideOffset));
                var halfWidth = target.getBbWidth() * 0.5D + 0.01D;
                var x = sidePosition.x;
                var z = sidePosition.z;

                if (direction == Direction.EAST)
                    x = wallFace - halfWidth;
                else if (direction == Direction.WEST)
                    x = wallFace + halfWidth;
                else if (direction == Direction.SOUTH)
                    z = wallFace - halfWidth;
                else if (direction == Direction.NORTH)
                    z = wallFace + halfWidth;

                target.setPos(x, entity.getY(), z);
            }
        }

        private static boolean isWalkableTerrainStep(Xenomorph entity, BlockPos pos, double shapeTop) {
            var surfaceY = pos.getY() + shapeTop;
            return surfaceY > entity.getY() - 0.5D && surfaceY <= entity.getY() + MAX_TERRAIN_STEP_UP;
        }

        private static Vec3 forward(Xenomorph entity) {
            var yawRadians = entity.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yawRadians), 0.0D, Mth.cos(yawRadians));
        }

        private static Direction horizontalDirection(Xenomorph entity) {
            var forward = forward(entity);

            if (Math.abs(forward.x) > Math.abs(forward.z)) {
                return forward.x > 0.0D ? Direction.EAST : Direction.WEST;
            }

            return forward.z > 0.0D ? Direction.SOUTH : Direction.NORTH;
        }

        private static float computeYawTowards(Xenomorph entity, LivingEntity target) {
            var dx = target.getX() - entity.getX();
            var dz = target.getZ() - entity.getZ();
            return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        }
    }
}
