package com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai.ChrysalisGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Chrysalis extends Xenomorph implements GOAPUser<Chrysalis> {

    public static final AttackType CLAW = AttackType.builder("chrysalis_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("chrysalis_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("chrysalis_tail")
        .requiresTail()
        .defaultDurationInTicks(12)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final int ROLL_DURATION_TICKS = 100;

    public static final int ROLL_COOLDOWN_TICKS = 600;

    public static final float ROLL_SPEED_MULTIPLIER = 1.5F;

    public static final float ROLL_STRAFE_SPEED_RATIO = 1.5F;

    public static final int ROLL_SMASHED_STUN_TICKS_MIN = 28;

    public static final int ROLL_SMASHED_STUN_TICKS_MAX = 44;

    public static final float ROLL_SMASH_WALL_DAMAGE = 60F;

    private static final EntityDimensions ROLLING_DIMENSIONS = EntityDimensions.fixed(1.0F, 1.0F);

    public static AttributeSupplier.Builder createChrysalisAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 12.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.5F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 6F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    public final DataAccessor<Boolean> isRolling;

    public final DataAccessor<Float> rollYaw;

    public final DataAccessor<Integer> rollCooldownTicks;

    public final DataAccessor<Boolean> rollWasSmashed;

    public final DataAccessor<Boolean> isStunned;

    public final DataAccessor<Integer> stunDurationTicks;

    private int rollTicksRemaining;

    private int stunTicksRemaining;

    private double previousRollDistanceSqr = -1.0;

    private int rollMovingAwayTicks = 0;

    private @Nullable BlockPos lastRollFirePos = null;

    private static final int ROLL_MOVING_AWAY_TICK_LIMIT = 5;

    private final ChrysalisAnimationDispatcher animationDispatcher;

    public Chrysalis(EntityType<? extends Chrysalis> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.LARGE, Chrysalis::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(CLAW)
                        .addRegular(BITE)
                        .addRegular(TAIL)
                        .build()
                )
                .parallelDigCount(2)
                .pushedByFluid(false)
                .build()
        );
        this.isRolling = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_IS_ROLLING.get());
        this.rollYaw = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_ROLL_YAW.get());
        this.rollCooldownTicks = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_ROLL_COOLDOWN_TICKS.get());
        this.rollWasSmashed = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_ROLL_WAS_SMASHED.get());
        this.isStunned = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_IS_STUNNED.get());
        this.stunDurationTicks = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_STUN_DURATION_TICKS.get());
        this.animationDispatcher = new ChrysalisAnimationDispatcher(this);

        isRolling.onChange($ -> refreshDimensions());
    }

    @Override
    public Agent.Builder<Chrysalis> blib$applyGOAPAgentProperties(Agent.Builder<Chrysalis> agentBuilder) {
        return ChrysalisGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Chrysalis> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(ChrysalisGOAP.GRAPH);
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 vec3) {
        if (isStunned.get()) {
            var current = getDeltaMovement();
            setDeltaMovement(0, current.y, 0);
            super.travel(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }

        super.travel(vec3);
    }

    @Override
    public @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        if (isRolling.get()) {
            return ROLLING_DIMENSIONS;
        }

        return super.getDefaultDimensions(pose);
    }

    public void startRoll(float yaw) {
        rollYaw.set(yaw);
        rollWasSmashed.set(false);
        isRolling.set(true);
        rollTicksRemaining = ROLL_DURATION_TICKS;
        previousRollDistanceSqr = -1.0;
        rollMovingAwayTicks = 0;
        lastRollFirePos = null;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        getNavigation().stop();
        playSound(
            AlienSoundEvents.ENTITY_XENOMORPH_LUNGE.get(),
            getSoundVolume(),
            (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F
        );
    }

    public void endRoll(boolean smashed) {
        rollWasSmashed.set(smashed);
        isRolling.set(false);
        rollTicksRemaining = 0;
        rollCooldownTicks.set(ROLL_COOLDOWN_TICKS);
        setDeltaMovement(getDeltaMovement().scale(0.2));

        if (smashed) {
            var stunDuration = random.nextIntBetweenInclusive(ROLL_SMASHED_STUN_TICKS_MIN, ROLL_SMASHED_STUN_TICKS_MAX);
            stunTicksRemaining = stunDuration;
            stunDurationTicks.set(stunDuration);
            isStunned.set(true);
            getNavigation().stop();
            damageWallOnSmash();
        }
    }

    private void damageWallOnSmash() {
        var yawRad = rollYaw.get() * Mth.DEG_TO_RAD;
        var forwardX = -Mth.sin(yawRad);
        var forwardZ = Mth.cos(yawRad);
        var rightX = Mth.cos(yawRad);
        var rightZ = Mth.sin(yawRad);

        var feetY = blockPosition().getY();
        var heightBlocks = Math.max(1, (int) Math.ceil(getBbHeight()));
        var centerX = getX() + forwardX;
        var centerZ = getZ() + forwardZ;

        for (var dy = 0; dy < heightBlocks; dy++) {
            var y = feetY + dy;

            for (var dr = -1; dr <= 1; dr++) {
                var px = centerX + rightX * dr;
                var pz = centerZ + rightZ * dr;
                var pos = BlockPos.containing(px, y, pz);

                if (!level().getBlockState(pos).isAir()) {
                    BlockBreakProgressManager.damage(level(), pos, ROLL_SMASH_WALL_DAMAGE);
                }
            }
        }
    }

    private static final double ROLL_WALL_PROBE_DISTANCE = 0.5;

    private boolean hasWallAhead(double forwardX, double forwardZ) {
        var probeX = getX() + forwardX * (getBbWidth() / 2 + ROLL_WALL_PROBE_DISTANCE);
        var probeZ = getZ() + forwardZ * (getBbWidth() / 2 + ROLL_WALL_PROBE_DISTANCE);
        var heightBlocks = Math.max(1, (int) Math.ceil(getBbHeight()));
        var feetY = blockPosition().getY();

        for (var dy = 0; dy < heightBlocks; dy++) {
            var pos = BlockPos.containing(probeX, feetY + dy, probeZ);

            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public boolean isRollCooldownReady() {
        return rollCooldownTicks.get() <= 0;
    }

    public int getRollTicksRemaining() {
        return rollTicksRemaining;
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            tickStunState();
            tickRollState();
        }
    }

    private void tickStunState() {
        if (stunTicksRemaining <= 0) {
            return;
        }

        stunTicksRemaining--;
        getNavigation().stop();

        if (stunTicksRemaining <= 0) {
            isStunned.set(false);
        }
    }

    private void tickRollState() {
        var cooldown = rollCooldownTicks.get();

        if (cooldown > 0) {
            rollCooldownTicks.set(cooldown - 1);
        }

        if (!isRolling.get()) {
            return;
        }

        if (rollTicksRemaining > 0) {
            rollTicksRemaining--;
        }

        var yaw = rollYaw.get();

        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        getNavigation().stop();

        var yawRad = yaw * Mth.DEG_TO_RAD;
        var forwardX = -Mth.sin(yawRad);
        var forwardZ = Mth.cos(yawRad);
        var rightX = Mth.cos(yawRad);
        var rightZ = Mth.sin(yawRad);
        var speed = Math.max(0.5, getAttributeValue(Attributes.MOVEMENT_SPEED) * ROLL_SPEED_MULTIPLIER);
        var strafeSpeed = speed * ROLL_STRAFE_SPEED_RATIO;
        var strafeFactor = computeStrafeFactor(rightX, rightZ);
        var current = getDeltaMovement();

        var velX = forwardX * speed + rightX * strafeSpeed * strafeFactor;
        var velZ = forwardZ * speed + rightZ * strafeSpeed * strafeFactor;

        setDeltaMovement(velX, current.y, velZ);

        if (getVariant() == AlienVariant.NETHER) {
            leaveFireTrail();
        }

        var victim = findRollVictim();

        if (victim != null) {
            applyDirectionalKnockback(victim);
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(victim);
            endRoll(false);
            return;
        }

        if (horizontalCollision || hasWallAhead(forwardX, forwardZ)) {
            endRoll(true);
            return;
        }

        if (isMovingAwayFromTarget()) {
            endRoll(false);
            return;
        }

        if (rollTicksRemaining <= 0) {
            endRoll(false);
        }
    }

    private boolean isMovingAwayFromTarget() {
        var target = getTarget();

        if (target == null) {
            previousRollDistanceSqr = -1.0;
            rollMovingAwayTicks = 0;
            return false;
        }

        var currentDistanceSqr = distanceToSqr(target);

        if (previousRollDistanceSqr < 0) {
            previousRollDistanceSqr = currentDistanceSqr;
            return false;
        }

        if (currentDistanceSqr > previousRollDistanceSqr) {
            rollMovingAwayTicks++;
        } else {
            rollMovingAwayTicks = 0;
        }

        previousRollDistanceSqr = currentDistanceSqr;

        return rollMovingAwayTicks >= ROLL_MOVING_AWAY_TICK_LIMIT;
    }

    private double computeStrafeFactor(double rightX, double rightZ) {
        var target = getTarget();

        if (target == null) {
            return 0.0;
        }

        var toTargetX = target.getX() - getX();
        var toTargetZ = target.getZ() - getZ();
        var rightProjection = toTargetX * rightX + toTargetZ * rightZ;

        return Math.max(-1.0, Math.min(1.0, rightProjection / 5.0));
    }

    private void leaveFireTrail() {
        var currentPos = blockPosition();
        var trailPos = lastRollFirePos;

        lastRollFirePos = currentPos;

        if (trailPos == null || trailPos.equals(currentPos)) {
            return;
        }

        if (level().isEmptyBlock(trailPos) && BaseFireBlock.canBePlacedAt(level(), trailPos, getDirection())) {
            level().setBlockAndUpdate(trailPos, BaseFireBlock.getState(level(), trailPos));
        }
    }

    private @Nullable LivingEntity findRollVictim() {
        var target = getTarget();

        if (target == null || !target.isAlive()) {
            return null;
        }

        var bounds = getBoundingBox().inflate(0.2);

        return bounds.intersects(target.getBoundingBox()) ? target : null;
    }

    private static final float ROLL_HEAD_ON_CONE_DEGREES = 30F;

    private static final double ROLL_KNOCKBACK_STRENGTH = 1.5;

    private void applyDirectionalKnockback(LivingEntity victim) {
        var yawRad = rollYaw.get() * Mth.DEG_TO_RAD;
        var forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        var right = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));

        var hitVec = victim.position().subtract(position());
        var hitFlat = new Vec3(hitVec.x, 0, hitVec.z);

        Vec3 kbDir;

        if (hitFlat.lengthSqr() < 1.0E-4) {
            kbDir = forward;
        } else {
            var hitDir = hitFlat.normalize();
            var forwardComponent = hitDir.dot(forward);
            var rightComponent = hitDir.dot(right);
            var angleFromForward = Math.toDegrees(Math.atan2(rightComponent, forwardComponent));

            if (Math.abs(angleFromForward) <= ROLL_HEAD_ON_CONE_DEGREES) {
                kbDir = forward;
            } else if (rightComponent > 0) {
                kbDir = right;
            } else {
                kbDir = right.scale(-1);
            }
        }

        victim.knockback(ROLL_KNOCKBACK_STRENGTH, -kbDir.x, -kbDir.z);
        victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.3, 0));
        victim.hurtMarked = true;
    }

    public ChrysalisAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.CHRYSALIS.get();
            case NETHER -> AlienEntityTypes.NETHER_CHRYSALIS.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_CHRYSALIS.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_CHRYSALIS.get();
        };
    }
}
