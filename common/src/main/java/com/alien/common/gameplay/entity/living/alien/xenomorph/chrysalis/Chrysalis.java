package com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.StunningChargeAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai.ChrysalisGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;

public class Chrysalis extends Xenomorph implements GOAPUser<Chrysalis> {

    public static final AttackType CLAW = AttackType.builder("chrysalis_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("chrysalis_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("chrysalis_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⭐ THE STUNNING HEADBUTT. [stated] "attack charge is basically a headbutt its a hard hitting attack that stuns a
     * player for 15 seconds, it has a 40s cool down before it can be used again."
     * <p>
     * A REGULAR attack on a long cooldown rather than a special: the existing `AttackCooldownTracker` already gates
     * `selectRegular` on `cooldownInTicks`, so no new timer, GOAP action or sensor is needed. 1.5x its own 10 damage =
     * 15, comfortably its hardest hit (bite/claw/tail are all 1.0x).
     * </p>
     */
    private static final float CHARGE_DAMAGE_FRACTION = 1.5F;

    /**
     * ⚠ MATCHED TO THE CRUSHER CHARGE. [stated] "lower the stun timer to match the stun from the crusher" -
     * `CrusherChargeAttack.STUN_DURATION_IN_TICKS` is **30 ticks (1.5 seconds)**, not the 15 seconds first specified.
     * That is a 10x cut, so it is written as a raw tick count to make the size obvious rather than hidden in a
     * seconds-times-20 expression.
     * <p>
     * ⚠ It is a COPY, not a reference: the crusher's constant is private to its own attack, and coupling the two
     * castes' balance through one field would mean retuning either one silently retunes the other.
     * </p>
     */
    private static final int CHARGE_STUN_TICKS = 30;

    private static final int CHARGE_COOLDOWN_TICKS = 40 * 20;

    public static final AttackType CHARGE = StunningChargeAttack.create(
        "chrysalis_charge",
        CHARGE_DAMAGE_FRACTION,
        CHARGE_STUN_TICKS,
        CHARGE_COOLDOWN_TICKS,
        20
    );

    /**
     * ⭐⭐ THE DEFENSIVE CURL. [stated] "the alien goes into the defense stance for 30s while in this stance it takes no
     * damage. it has a 120s cool down. nothing can get it out of this stance. while in the stance it regenerates health
     * 3x as fast." - then lowered to 2x on review.
     * <p>
     * ⚠ TRIGGER IS MINE, NOT HIS - he specified the stance but not what opens it. It curls when badly hurt, which is
     * the only reading that makes the 3x regen matter. Change `DEFENSE_HEALTH_FRACTION` if that is wrong.
     * </p>
     */
    public static final int DEFENSE_DURATION_TICKS = 30 * 20;

    public static final int DEFENSE_COOLDOWN_TICKS = 120 * 20;

    /** [stated] lowered from 3x to 2x - see the balance note: 30 untouchable seconds in every 150 is already a lot. */
    public static final float DEFENSE_REGEN_MULTIPLIER = 2.0F;

    /**
     * ⭐⭐ THE TELL. [stated] "add red particles to it while its doing the defense curl so the player knows its up to
     * something tricky unless theres a health up effect in vanilla otherwise the red particles will work."
     * <p>
     * ⚠ THERE IS NO VANILLA "HEALING" PARTICLE — I checked. Regeneration has no particle of its own; what a player sees
     * is the generic potion swirl ({@code ENTITY_EFFECT}) tinted from the effect's colour, and there is no standalone
     * heal-up puff to borrow. So: red, as he said.
     * </p>
     * <p>
     * ⚠ RED **DUST**, NOT `DAMAGE_INDICATOR` OR `HEART` — both of those already mean something else to a player (a hit
     * landing, and breeding/taming). Dust carries no prior meaning, so it reads as "this thing is doing something"
     * rather than as a mis-cue.
     * </p>
     */
    private static final Vector3f DEFENSE_PARTICLE_COLOUR = new Vector3f(0.85F, 0.05F, 0.05F);

    private static final float DEFENSE_PARTICLE_SCALE = 1.8F;

    /** How many particles per burst, and how often. Low and slow: a steady shimmer, not a smoke machine. */
    private static final int DEFENSE_PARTICLE_COUNT = 12;

    private static final int DEFENSE_PARTICLE_INTERVAL_TICKS = 5;

    /** ⭐ THE TRIGGER DIAL - curls below this fraction of max health. */
    private static final float DEFENSE_HEALTH_FRACTION = 0.35F;

    public static final int ROLL_DURATION_TICKS = 100;

    public static final int ROLL_COOLDOWN_TICKS = 600;

    public static final float ROLL_SPEED_MULTIPLIER = 1.5F;

    public static final float ROLL_STRAFE_SPEED_RATIO = 1.5F;

    public static final int ROLL_SMASHED_STUN_TICKS_MIN = 28;

    public static final int ROLL_SMASHED_STUN_TICKS_MAX = 44;

    public static final float ROLL_SMASH_WALL_DAMAGE = 60F;

    /**
     * The height at which an obstacle ahead stops being terrain to roll over and becomes a WALL to smash into.
     * <p>
     * [stated] "if it hits any two block or more obstical it gets stunned" - so TWO FULL BLOCKS is the line. The test
     * is deliberately STRICT ({@code <}), because an obstacle exactly two blocks tall has its top surface exactly this
     * far above her feet and must count as a wall, not as the tallest thing she can climb.
     * </p>
     * <p>
     * ⚠ THIS ALSO SETS HER STEP HEIGHT WHILE ROLLING - see {@link #maxUpStep()}. The two MUST agree. Every alien
     * normally steps 1.5 ({@link Alien#maxUpStep()}), so raising only this figure would open a dead band between 1.5
     * and 2.0: an obstacle in there would be refused as a wall AND be too tall to climb, leaving her grinding against
     * it until the stall net stopped her. Raising both keeps "not a wall" and "can get over it" the same statement.
     * </p>
     * <p>
     * The Crusher charge draws its own line at {@code MAX_TERRAIN_STEP_UP} (1.5); the two attacks stay independent.
     * </p>
     */
    public static final double ROLL_WALL_HEIGHT = 2.0D;

    private static final EntityDimensions ROLLING_DIMENSIONS = EntityDimensions.fixed(1.0F, 1.0F);

    /**
     * Steps as high while rolling as the roll refuses to call a wall.
     * <p>
     * Aliens step 1.5 as standard. Now that anything under two blocks is terrain rather than an impact, she has to be
     * physically able to mount it too - otherwise a 1.75-block rise would be waved through by the wall probe and then
     * simply block her, and the only thing to end the roll would be the stall net six ticks later. Applies ONLY while
     * rolling; the moment the roll ends she is back to the ordinary alien step.
     * </p>
     */
    @Override
    public float maxUpStep() {
        // Null-guarded: isRolling is assigned in the constructor, and vanilla may ask an Entity for its step height
        // before that point. Falling back to the ordinary alien step is both safe and correct there.
        return isRolling != null && Boolean.TRUE.equals(isRolling.get())
            ? (float) ROLL_WALL_HEIGHT
            : super.maxUpStep();
    }

    public static AttributeSupplier.Builder createChrysalisAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 20.0F)
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

    public final DataAccessor<Boolean> isDefending;

    public final DataAccessor<Integer> defenseTicksRemaining;

    public final DataAccessor<Integer> defenseCooldownTicks;

    public final DataAccessor<Boolean> isStunned;

    public final DataAccessor<Integer> stunDurationTicks;

    private int rollTicksRemaining;

    private int stunTicksRemaining;

    private double previousRollDistanceSqr = -1.0;

    private int rollMovingAwayTicks = 0;

    private @Nullable BlockPos lastRollFirePos = null;

    private static final int ROLL_MOVING_AWAY_TICK_LIMIT = 5;

    /**
     * Backstop for anything the wall probe cannot see (entity wedges, awkward diagonals): grind for this long and stop.
     */
    private static final int ROLL_STALLED_TICK_LIMIT = 6;

    private static final double ROLL_STALL_EPSILON_SQUARED = 0.02 * 0.02;

    private double previousRollX = 0.0;

    private double previousRollZ = 0.0;

    private int rollStalledTicks = 0;

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
                        .addRegular(CHARGE)
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
        this.isDefending = new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_IS_DEFENDING.get());
        this.defenseTicksRemaining =
            new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_DEFENSE_TICKS_REMAINING.get());
        this.defenseCooldownTicks =
            new DataAccessor<>(this, AlienDataSyncKeys.CHRYSALIS_DEFENSE_COOLDOWN_TICKS.get());
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
        rollStalledTicks = 0;
        previousRollX = getX();
        previousRollZ = getZ();
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
        }
    }

    /**
     * Chews through whatever is blocking the roll.
     * <p>
     * Called from the tick BEFORE the roll decides to stop, exactly as {@code CrusherChargeAttack} does: damage the
     * wall, then re-check. If the wall gave way the roll carries on through the hole; only a wall that SURVIVES the
     * bite ends the roll and stuns. So unbreakable stone, bedrock and anything tagged immune still stop it dead.
     * <p>
     * Aimed at the blocks {@link #frontWallPositions} genuinely reported as a wall (plus one block to either side, so
     * the hole is wide enough to roll through) rather than a blind row at foot level - the old version swept a fixed
     * 3x1 row one block ahead, which on a slab floor meant it bit the FLOOR it was standing on.
     */
    private void breakWallAhead() {
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        var wallPositions = frontWallPositions();

        if (wallPositions.isEmpty()) {
            return;
        }

        var lateral = rollDirection().getClockWise();
        var damaged = new LinkedHashSet<BlockPos>();

        for (var wallPos : wallPositions) {
            for (var offset = -1; offset <= 1; offset++) {
                damaged.add(wallPos.relative(lateral, offset));
            }
        }

        for (var pos : damaged) {
            var state = level().getBlockState(pos);

            if (canDamageBlock(pos, state)) {
                BlockBreakProgressManager.damage(level(), pos, ROLL_SMASH_WALL_DAMAGE);
            }
        }
    }

    /** Mirrors the Crusher charge's blacklist: no block entities, no unbreakable blocks, nothing tagged immune. */
    private boolean canDamageBlock(BlockPos pos, BlockState state) {
        return !state.isAir()
            && !state.canBeReplaced()
            && !state.hasBlockEntity()
            && state.getDestroySpeed(level(), pos) >= 0.0F
            && !state.is(AlienBlockTags.XENOMORPH_IMMUNE)
            && !state.getCollisionShape(level(), pos).isEmpty();
    }

    private static final double ROLL_WALL_PROBE_DISTANCE = 0.5;

    private Direction rollDirection() {
        return Direction.fromYRot(rollYaw.get());
    }

    /**
     * The blocks directly ahead that the roll genuinely cannot get past.
     * <p>
     * Sweeps the rolling bounding box shifted forward and keeps only blocks whose collision shape actually intersects
     * it AND whose top surface sits higher than {@link #ROLL_WALL_HEIGHT} above the feet. That second test is the fix:
     * slabs, stairs, carpets, path blocks and one-block rises are surfaces to climb, not walls to hit, and vanilla
     * collision resolution steps the Chrysalis over them without any help from us.
     */
    private Set<BlockPos> frontWallPositions() {
        var yawRad = rollYaw.get() * Mth.DEG_TO_RAD;
        var forwardX = -Mth.sin(yawRad);
        var forwardZ = Mth.cos(yawRad);
        var reach = getBbWidth() / 2 + ROLL_WALL_PROBE_DISTANCE;
        var body = getBoundingBox().move(forwardX * reach, 0.0, forwardZ * reach);

        // ⚠ THE PROBE MUST REACH UP TO ROLL_WALL_HEIGHT, NOT JUST ACROSS HER BODY.
        // Rolling she is 1.0 x 1.0, so a probe shaped like her body spans y..y+1 and the floor/ceil below resolves to
        // exactly ONE block layer - the one at her feet. That layer's top surface is at most one block above her feet,
        // which is always under the wall threshold, so every block it found was classed as a walkable step and
        // hasWallAhead() could NEVER return true. She rolled into walls forever and never stunned. Looking up to the
        // wall height means the block ABOVE the step is examined too, and THAT is the one whose surface clears the
        // threshold on a two-high obstacle.
        var probe = new net.minecraft.world.phys.AABB(
            body.minX,
            getY(),
            body.minZ,
            body.maxX,
            getY() + ROLL_WALL_HEIGHT,
            body.maxZ
        );
        var positions = new LinkedHashSet<BlockPos>();
        var minX = Mth.floor(probe.minX + 1.0E-4);
        var minY = Mth.floor(probe.minY + 1.0E-4);
        var minZ = Mth.floor(probe.minZ + 1.0E-4);
        var maxX = Mth.floor(probe.maxX - 1.0E-4);
        var maxY = Mth.floor(probe.maxY - 1.0E-4);
        var maxZ = Mth.floor(probe.maxZ - 1.0E-4);

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var pos = new BlockPos(x, y, z);
                    var state = level().getBlockState(pos);
                    var shape = state.getCollisionShape(level(), pos);

                    if (
                        !state.canBeReplaced()
                            && !shape.isEmpty()
                            && shape.bounds().move(pos).intersects(probe)
                            && !isWalkableTerrainStep(pos, shape.max(Direction.Axis.Y))
                    ) {
                        positions.add(pos);
                    }
                }
            }
        }

        return positions;
    }

    private boolean isWalkableTerrainStep(BlockPos pos, double shapeTop) {
        var surfaceY = pos.getY() + shapeTop;
        return surfaceY > getY() - 0.5 && surfaceY < getY() + ROLL_WALL_HEIGHT;
    }

    private boolean hasWallAhead() {
        return !frontWallPositions().isEmpty();
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
            tickDefenseState();
            tickStunState();
            tickRollState();
        }
    }

    /**
     * The defensive curl: cooldown, duration, and the decision to open it.
     * <p>
     * ⚠ IT OPENS ONLY WHEN IT HAS A REASON AND A WAY OUT OF ONE. Curling mid-roll would freeze a rolling body, and
     * curling while stunned would hand it a free escape from the stun it earned by smashing into a wall - so both are
     * refused.
     * </p>
     */
    private void tickDefenseState() {
        var cooldown = defenseCooldownTicks.get();

        if (cooldown > 0) {
            defenseCooldownTicks.set(cooldown - 1);
        }

        if (isDefending.get()) {
            var remaining = defenseTicksRemaining.get() - 1;
            defenseTicksRemaining.set(remaining);

            // ⭐⭐ IT DOES NOT MOVE. [stated] "it glides around to move when it should be immobile."
            //
            // ⚠⚠ STOPPING THE NAVIGATION IS NOT ENOUGH, which is what I had. `stop()` cancels the PATH, but it
            // leaves the momentum the mob already had, and the GOAP layer re-issues a move the very next tick -
            // so a curled chrysalis kept sliding along its last heading. Zeroing the horizontal delta every tick
            // is what actually pins it, and it has to happen EVERY tick because something upstream keeps setting
            // it again.
            //
            // ⚠ VERTICAL MOTION IS LEFT ALONE ON PURPOSE - a curled chrysalis still falls. Freezing Y would hang
            // it in the air if the floor were broken out from under it, which is a far worse look than sliding.
            getNavigation().stop();
            setTarget(null);

            var motion = getDeltaMovement();
            setDeltaMovement(0.0, motion.y, 0.0);
            setXxa(0.0F);
            setZza(0.0F);
            hurtMarked = true; // server-side freeze: without this the client keeps extrapolating the old motion

            spawnDefenseParticles();

            if (remaining <= 0) {
                isDefending.set(false);
                defenseCooldownTicks.set(DEFENSE_COOLDOWN_TICKS);
            }
            return;
        }

        if (!shouldEnterDefense()) {
            return;
        }

        isDefending.set(true);
        defenseTicksRemaining.set(DEFENSE_DURATION_TICKS);
        getNavigation().stop();
    }

    private boolean shouldEnterDefense() {
        return defenseCooldownTicks.get() <= 0
            && isAlive()
            && !isRolling.get()
            && !isStunned.get()
            && getHealth() < getMaxHealth() * DEFENSE_HEALTH_FRACTION;
    }

    /**
     * ⭐⭐ THE PROPER LEVER FOR "IMMOBILE". [stated] "it glides around to move when it should be immobile", and confirmed
     * as the defence stance rather than the roll.
     * <p>
     * ⚠⚠ ZEROING THE DELTA EACH TICK TREATS THE SYMPTOM; THIS TREATS THE CAUSE. Vanilla consults {@code isImmobile()}
     * inside {@code LivingEntity.travel}/{@code aiStep} and skips applying movement input entirely when it is true -
     * the same hook that pins a dead or sleeping mob. With this, nothing upstream even gets the chance to move her, so
     * there is no momentum left to cancel.
     * </p>
     * <p>
     * ⚠ THE PER-TICK DELTA CLEAR IS KEPT ANYWAY, deliberately. isImmobile stops movement INPUT; it does not cancel
     * velocity she already carried into the curl, or a shove from something else. Belt and braces, and the two together
     * are what make "nothing can get it out of this stance" literally true.
     * </p>
     * <p>
     * ⚠ GRAVITY IS UNAFFECTED - isImmobile does not stop falling, which is what we want: a curled chrysalis with the
     * floor removed should drop, not hang.
     * </p>
     */
    @Override
    protected boolean isImmobile() {
        return isDefending.get() || super.isImmobile();
    }

    public boolean isDefending() {
        return isDefending.get();
    }

    /**
     * The red shimmer that tells a player the curl is up and hitting it is pointless.
     * <p>
     * ⚠ SERVER-SIDE `sendParticles`, not a client spawn: this is only reached from the server half of `tick()`, and
     * `sendParticles` broadcasts to everyone in range - so every player sees the same tell, not just whoever happens to
     * be simulating.
     * </p>
     */
    private void spawnDefenseParticles() {
        if (tickCount % DEFENSE_PARTICLE_INTERVAL_TICKS != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // ⚠ SPAWNED ON THE SHELL, NOT AT THE CENTRE. [stated] "i dont see the red particles" - at getY(0.5) with a
        // narrow spread they were being born INSIDE the curled body and occluded by its own model. Pushing them
        // out to the bounding box edge and up to its mid-height puts them where a player can actually see them.
        serverLevel.sendParticles(
            new DustParticleOptions(DEFENSE_PARTICLE_COLOUR, DEFENSE_PARTICLE_SCALE),
            getX(),
            getY(0.6),
            getZ(),
            DEFENSE_PARTICLE_COUNT,
            getBbWidth() * 0.9,
            getBbHeight() * 0.5,
            getBbWidth() * 0.9,
            0.0
        );
    }

    /**
     * ⚠⚠ TAKES NO DAMAGE WHILE CURLED, WITH TWO DELIBERATE EXCEPTIONS.
     * <p>
     * `isBypassInvul` covers /kill, the void and creative-mode removal - a mob that could survive those would be an
     * unkillable world-breaker rather than a tough one, and no design intent covers it. `super` is still consulted so
     * every existing species rule (radiation immunity, the does-not-hurt-aliens tag) keeps working underneath.
     * </p>
     */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource damageSource) {
        if (isDefending.get() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        return super.isInvulnerableTo(damageSource);
    }

    /** [stated] "while in the stance it regenerates health 3x as fast." */
    @Override
    protected float getHealthRegenPerSecond() {
        var base = super.getHealthRegenPerSecond();
        return isDefending.get() ? base * DEFENSE_REGEN_MULTIPLIER : base;
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

        // horizontalCollision alone is NOT an impact - vanilla raises it for any clipped movement, including the
        // step-up it is about to perform for us. Only a real wall (or a genuine stall) ends the roll.
        if (hasWallAhead()) {
            // Smash a hole in it, then STOP. Always.
            //
            // ⚠ THIS DELIBERATELY REVERSES THE PLOW-THROUGH ORDER copied from the Crusher charge. Under that order the
            // roll re-checked after breaking and CONTINUED if the wall had given way - but ROLL_SMASH_WALL_DAMAGE is
            // 60F, which flattens ordinary blocks in a single bite, so a wall never survived to trigger the stun. She
            // simply bored through it a layer per tick, barely moving and never stunning: "hits a wall and keeps
            // rolling in place, no stun animation". It also contradicts the rule outright - [stated] "if it hits any
            // two block or more obstical it gets stunned" leaves no room for rolling on through one.
            breakWallAhead();
            endRoll(true);
            return;
        }

        if (isRollStalled()) {
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

    /**
     * Catches anything the wall probe cannot see - wedged against another mob, jammed in an awkward diagonal - so a
     * blocked roll still ends in an impact instead of grinding in place for the rest of its 100 ticks.
     */
    private boolean isRollStalled() {
        var movedX = getX() - previousRollX;
        var movedZ = getZ() - previousRollZ;

        previousRollX = getX();
        previousRollZ = getZ();

        if (movedX * movedX + movedZ * movedZ < ROLL_STALL_EPSILON_SQUARED) {
            rollStalledTicks++;
        } else {
            rollStalledTicks = 0;
        }

        return rollStalledTicks >= ROLL_STALLED_TICK_LIMIT;
    }

    private boolean isMovingAwayFromTarget() {
        var target = getTarget();

        if (target == null) {
            previousRollDistanceSqr = -1.0;
            rollMovingAwayTicks = 0;
            return false;
        }

        // HORIZONTAL distance only. A 3D measure grows while the Chrysalis climbs toward a target standing above it,
        // which would abort a perfectly good uphill roll at the ROLL_MOVING_AWAY_TICK_LIMIT.
        var dx = target.getX() - getX();
        var dz = target.getZ() - getZ();
        var currentDistanceSqr = dx * dx + dz * dz;

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
