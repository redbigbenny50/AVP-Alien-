package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ScaledDamage;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.ai.EmpressGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenHeadRamAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenScreamDefense;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Empress extends Xenomorph implements GOAPUser<Empress>, EggLayer, com.alien.common.gameplay.entity.CrawlPostureTransitionListener, QueenScreamDefense.ScreamingRoyal, com.alien.common.gameplay.entity.living.alien.xenomorph.IncapacitatableRoyal, com.alien.common.gameplay.entity.living.alien.xenomorph.CarvingRoyal {

    @Override
    public int crawlPostureTransitionTicks(boolean enteringCrawl) {
        return enteringCrawl ? EmpressAnimationRefs.CRAWL_DROP_TICKS : EmpressAnimationRefs.CRAWL_RISE_TICKS;
    }

    /**
     * ⭐⭐ THE SWIM CLAWS - BOTH ARMS, and the same rule the ravager's double claw follows. [stated] "swim attack slaws
     * is both arms use the same 50% damage rule if missing 1 arm or doesnt play at all if both are gone."
     * <p>
     * ⚠ THE RULE NEEDS BOTH HALVES IN DIFFERENT PLACES, and neither alone expresses it: `requiresAnyArm()` is the "not
     * at all with no arms" half, and the 50% is priced into the damage below. Using `requiresBothArms()` would collapse
     * them into one and the half-damage case could never happen.
     * </p>
     */
    private static final float SWIM_CLAWS_MISSING_ARM_PENALTY = 0.5F;

    /** ⭐ Same shape as the queen's: 5x5x3 AOE shove AND wall break, 120s cooldown. See QueenHeadRamAttack. */
    public static final AttackType HEAD_RAM = QueenHeadRamAttack.create("empress_head_ram", 120 * 20, 22);

    /**
     * ⭐ Her bite, and her fallback. Same reasoning as the queen: every other attack she has needs an arm or a tail, so
     * losing both arms leaves the bite as the only thing the limb gate still admits.
     */
    public static final AttackType BITE = AttackType.builder("empress_bite")
        .requiresHead()
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType CRAWL_BITE = AttackType.builder("empress_crawl_bite")
        .crawlAttack()
        .requiresHead()
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType CRAWL_ATTACK = AttackType.builder("empress_crawl_attack")
        .crawlAttack()
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType SWIM_CLAWS = AttackType.builder("empress_swim_claws")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(Empress::applySwimClawsDamage)
        .build();

    private static void applySwimClawsDamage(Xenomorph xenomorph, LivingEntity target) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        var bothArms = !MirroredAttackSide.isArmDetached(xenomorph, true)
            && !MirroredAttackSide.isArmDetached(xenomorph, false);

        xenomorph.swing(InteractionHand.MAIN_HAND);
        ScaledDamage.hurtScaled(xenomorph, target, bothArms ? 1.0F : SWIM_CLAWS_MISSING_ARM_PENALTY);
    }

    public static final AttackType SWIPE_DOWN = AttackType.builder("empress_swipe_down")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BACKHAND = AttackType.builder("empress_backhand")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL_STRIKE = AttackType.builder("empress_tail_strike")
        .requiresTail()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.WIDE_TALL, Empress::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(SWIPE_DOWN)
                .addRegular(BACKHAND)
                .addRegular(TAIL_STRIKE)
                .addRegular(SWIM_CLAWS)
                .addRegular(HEAD_RAM)
                .addRegular(BITE)
                .addRegular(CRAWL_BITE)
                .addRegular(CRAWL_ATTACK)
                .build()
        )
        // ⭐ 0.8/sec rather than the 0.5 every other caste inherits.
        //
        // ⚠ THIS IS ALSO HER INCAPACITATION CLIMB. The downed bar fills at maxHealth / regen, so at the default 0.5
        // her 500 health took 1000 seconds - nearly seventeen minutes on the floor against a queen's eight. 0.8
        // brings her to about ten and a half, which is still longer than a queen as her larger pool should be.
        //
        // ⚠ It speeds her ORDINARY out-of-combat healing by the same amount, because it is the same number. That is
        // intended here, but it is the reason to change this value rather than a separate recovery multiplier.
        .healthRegenPerSecond(0.8F)
        .parallelDigCount(4)
        .pushedByFluid(false)
        .build();

    public static AttributeSupplier.Builder createEmpressAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 20.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 20.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 1.5F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 25F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 0.9F);
    }

    public final DataAccessor<Integer> screamCooldownTicks;

    public final DataAccessor<Boolean> screamedAtFirstThreshold;

    public final DataAccessor<Boolean> screamedAtSecondThreshold;

    public final DataAccessor<Integer> screamId;

    private final EmpressAnimationDispatcher animationDispatcher;

    private final EmpressOvipositorManager empressOvipositorManager;

    private final EmpressData empressData;

    public Empress(EntityType<? extends Empress> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.screamCooldownTicks = new DataAccessor<>(this, AlienDataSyncKeys.EMPRESS_SCREAM_COOLDOWN_TICKS.get());
        this.screamedAtFirstThreshold =
            new DataAccessor<>(this, AlienDataSyncKeys.EMPRESS_SCREAMED_AT_FIRST_THRESHOLD.get());
        this.screamedAtSecondThreshold =
            new DataAccessor<>(this, AlienDataSyncKeys.EMPRESS_SCREAMED_AT_SECOND_THRESHOLD.get());
        this.screamId = new DataAccessor<>(this, AlienDataSyncKeys.EMPRESS_SCREAM_ID.get());
        this.isDiggingSynced = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_IS_DIGGING.get());
        this.animationDispatcher = new EmpressAnimationDispatcher(this);
        this.empressOvipositorManager = new EmpressOvipositorManager(this);
        this.empressData = new EmpressData();
    }

    @Override
    public Agent.Builder<Empress> blib$applyGOAPAgentProperties(Agent.Builder<Empress> agentBuilder) {
        return EmpressGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Empress> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(isOnOvipositor() ? EmpressGOAP.OVIPOSITOR_GRAPH : EmpressGOAP.GRAPH);
    }

    private boolean isOnOvipositor() {
        return empressOvipositorManager != null && empressOvipositorManager.hasOvipositor();
    }

    /**
     * Registers this empress as a member of whichever hive she is currently sitting in.
     * <p>
     * 🚨🚨 AN EMPRESS TAKES OVER A LOCATION AS ITS QUEEN, so she must appear in that location's membership like any
     * other royal - [stated] "empress can now take over locations as their queen and multiple can be in a lineage now
     * so she should become a member of that location as well not just the lineage". She was only ever bound at the
     * LINEAGE level, so everything that walks loadedMembersByType could not see her: population counts, the egg-lay
     * anchor, and the restock capacity check, which skipped its cap entirely when it found no royal and piled eggs on
     * one block until the entity cram limit killed them.
     * </p>
     * <p>
     * ⭐ DONE HERE RATHER THAN AT EACH SEATING PATH, on purpose. She can arrive at a location by emergence, schism,
     * forced founding or takeover; binding on her own tick covers every one of them, including any added later, and
     * re-heals a world whose empress was seated before this existed.
     * </p>
     * <p>
     * ⚠ Throttled and idempotent - LocationMembership.join checks for an existing member before adding, so the steady
     * state is one map lookup every few seconds.
     * </p>
     */
    private void ensureSeatLocationMembership() {
        if (tickCount % SEAT_MEMBERSHIP_INTERVAL_TICKS != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var location = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
            .getByChunk(serverLevel.dimension(), new net.minecraft.world.level.ChunkPos(blockPosition()));

        if (location == null || !location.isAlive()) {
            return;
        }

        com.alien.common.gameplay.hive.faction.LocationMembership.join(location, this);
    }

    /** How often the empress re-checks that she is on the roster of the hive she is sitting in. */
    private static final int SEAT_MEMBERSHIP_INTERVAL_TICKS = 100;

    @Override
    public void tick() {
        super.tick();

        // [stated] Oct 5: a royal on her sack does not move at all, turning or otherwise - same rule as the queen.
        // Idle look control would otherwise keep turning her body against the eggsack anchored to her rotation.
        if (isRidingEmpressOvipositor()) {
            if (seatedYRotLock == null) {
                seatedYRotLock = yBodyRot;
            }

            setYRot(seatedYRotLock);
            yBodyRot = seatedYRotLock;
            yHeadRot = seatedYRotLock;
        } else if (seatedYRotLock != null) {
            seatedYRotLock = null;
        }

        tickForcedDescent();

        if (!level().isClientSide) {
            incapacitationManager.tick();
            ensureSeatLocationMembership();
        }

        // ⭐ THE SCREAM, shared with the queen through QueenScreamDefense.ScreamingRoyal - she just calls five.
        if (!level().isClientSide && QueenScreamDefense.tick(this)) {
            screamId.set(screamId.get() + 1);
        }

        // Once a second, so her regeneration repays the disturbance debt. Without this the bar would only ever climb.
        if (!level().isClientSide && tickCount % 20 == 0) {
            sampleHealth();

            // ⭐⭐ SITUATION 4 - AN EMPRESS STANDING IN A CROWNLESS HIVE TAKES IT.
            //
            // [stated] "they spawn an empress inside an existing hive with a queen but no empress in lineage yet. She
            // would take over the rule from that queen."
            //
            // ⚠ ON HER TICK RATHER THAN ON A PLAYER ACTION, because a player SPAWNS her - there is no interaction to
            // hook. The same second-cadence sample above is the right place: succession is a rare event and this
            // costs one registry lookup when nothing is happening.
            com.alien.common.gameplay.hive.empress.ForcedEmpressFounding.tryClaimCrownlessHive(this);
        }

        // !!! NOTHING GROWS INSIDE THE COCOON. The ovipositor manager runs off the ENTITY tick, not off the GOAP
        // graph, so the exclusive cocoon graph never gated it - a royal mid-molt kept growing an eggsack, which
        // pushed her out of her own cage before EMERGING. That aborted the handover chain, and since RoyalCocoon has
        // no tick of its own the abandoned cage then stood there permanently.
        //
        // ⚠ Reported as "she left the cocoon and then placed another sack ... it counts her done and she makes a sack
        // even though shes still molting". One missing check, three visible symptoms.
        if (!getCocoonManager().shouldRunCocoonAction()) {
            empressOvipositorManager.tick();
        }
        empressData.tick();
        tickCrownAdoption();
        tickLoneFounding();
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return Objects.equals(passenger.getType(), AlienEntityTypes.EMPRESS_OVIPOSITOR.get());
    }

    // Where her eggsack rides. EntityUtil.getRelativePosition takes (LATERAL, VERTICAL, FORWARD) relative to body
    // facing - confirmed from BLib's bytecode, which builds a forward vector, a perpendicular from it, and offsets
    // from the bounding-box centre.
    //
    // All three were copied verbatim from Queen.positionRider. The seat is the FIRST CUBE IN gFullSack - named
    // queenattachcube in Blockbench - and that one pivot is the only thing worth measuring. Not the hitboxes (both
    // royals share QUEEN_WIDTH/QUEEN_HEIGHT, so they carry no signal at all) and not the model bounds (the shells
    // differ in size and lean, but the seat does not move with them):
    //
    // ovipositor.geo gFullSack cube pivot [ -47.5053, 24.1, -73.87715 ]
    // empress_ovipositor.geo gFullSack cube pivot [ -0.7, 24.1, -65.46152 ]
    // delta [ +46.8053, 0.0, +8.41563 ] = [ +2.9253, 0, +0.5260 ] blocks
    //
    // The axis mapping is confirmed by the queen's own value: -(-47.5053)/16 = 2.9691, and her lateral param is 3.
    // Her seat sits nearly three blocks off her sack's centre line and the parameter cancels it almost exactly. The
    // empress's seat is all but centred (-0.7u), so she needs almost NO lateral offset - inheriting the queen's 3
    // threw the sack the better part of three blocks sideways, which is the whole bug.

    /** 3 - 2.9253. Her seat is centred where the queen's is not, so this collapses to almost nothing. */
    private static final double OVIPOSITOR_RIDE_LATERAL = 0.0747;

    /** Unchanged - the seat is at the same height on both models (Y 24.1 on each). */
    private static final double OVIPOSITOR_RIDE_LIFT = 0.01;

    /** 5.25 - 0.5260. Her seat sits slightly further back along the sack, so it rides slightly closer in. */
    private static final double OVIPOSITOR_RIDE_DISTANCE = 4.724;

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType() == AlienEntityTypes.EMPRESS_OVIPOSITOR.get()) {
            var relativePos = com.blib.api.common.entity.v1.EntityUtil.getRelativePosition(
                this,
                OVIPOSITOR_RIDE_LATERAL,
                OVIPOSITOR_RIDE_LIFT,
                OVIPOSITOR_RIDE_DISTANCE
            );
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        super.positionRider(passenger, callback);
    }

    @Override
    public float maxUpStep() {
        return 2.5F;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return AlienSoundEvents.ENTITY_EMPRESS_IDLE.get();
    }

    @Override
    protected @NotNull SoundEvent getDeathSound() {
        return AlienSoundEvents.ENTITY_EMPRESS_DEATH.get();
    }

    @Override
    protected @NotNull SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return AlienSoundEvents.ENTITY_EMPRESS_HURT.get();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (
            !empressOvipositorManager.hasOvipositor()
                || !entity.getType().is(AlienEntityTypeTags.ALIENS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    public EmpressAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    /**
     * ⭐⭐⭐ SHE DIGS. Same flag, same clips and same rules as the queen.
     * <p>
     * [stated] "added the digging animations for the empress. same rules as the queen and speeds."
     * </p>
     * <p>
     * ⚠⚠ SHE NEEDS HER OWN because she has NO LIFECYCLE PHASE MANAGER - the queen's descent lives in a GOAP action
     * gated on her LOCATION phase, which the empress never enters. A DISPLACED empress (situation 5) is already an
     * empress by the time she has to go and found, so the queen's path was never available to her, and without this her
     * six digging clips had no user at all.
     * </p>
     * <p>
     * ⚠ {@code XENOMORPH_IS_DIGGING} is shared with the queen deliberately - the animator reads the same synced flag,
     * so there is one definition of "is this royal digging" rather than two that can disagree.
     * </p>
     */
    public final DataAccessor<Boolean> isDiggingSynced;

    private boolean digging;

    private @Nullable BlockPos descentAnchor;

    public boolean isDigging() {
        return digging;
    }

    public void setDigging(boolean value) {
        // 🚨 SAME DESYNC AS THE QUEEN'S SETTER. `digging` is transient, isDiggingSynced persists, so after a reload the
        // early-out below could discard every attempt to clear the flag and leave her animating a dig forever.
        if (isDiggingSynced.get() != value) {
            isDiggingSynced.set(value);
        }

        if (this.digging == value) {
            return;
        }
        this.digging = value;
        this.noPhysics = value;
        setNoGravity(value);
        isDiggingSynced.set(value);
    }

    /**
     * ⭐⭐ Sends her digging down to an anchor. She founds when she ARRIVES.
     * <p>
     * ⚠ The anchor is held here rather than founding up front, so the spread checks run against where she actually ends
     * up. Founding immediately is the mistake the queen's forced path made and it is not repeated here.
     * </p>
     */
    public void beginForcedDescent(BlockPos anchor) {
        this.descentAnchor = anchor;
        setDigging(true);
    }

    public @Nullable BlockPos descentAnchor() {
        return descentAnchor;
    }

    public void clearDescent() {
        this.descentAnchor = null;
        setDigging(false);
    }

    /**
     * ⭐⭐⭐ CARRIES HER DOWN TO HER ANCHOR AND FOUNDS WHEN SHE GETS THERE.
     * <p>
     * ⚠⚠ WITHOUT THIS, {@code beginForcedDescent} WOULD BE ANOTHER EMPTY HOOK - a flag set, an anchor stored and
     * nothing that ever moves her or founds anything. That is precisely the shape of bug this pass exists to remove, so
     * the descent and the founding live together and neither ships without the other.
     * </p>
     * <p>
     * ⭐ SAME RULES AND SPEEDS AS THE QUEEN, deliberately - [stated] "same rules as the queen and speeds". Her dig
     * speed, her air-drop speed and her arrival tolerance are the queen's numbers, so the two royals descend
     * identically and a player cannot tell them apart by pace.
     * </p>
     * <p>
     * ⚠ AIR MEANS DROP, copied for the same reason it exists on the queen: the line to an anchor is a diagonal, and
     * over a gorge that diagonal leaves the ground entirely and reads as casual flight. Unsupported means surrender the
     * horizontal for the tick and fall straight down at air speed - still noclip, still controlled, no real gravity.
     * </p>
     */
    private void tickForcedDescent() {
        if (level().isClientSide || descentAnchor == null) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var target = new Vec3(descentAnchor.getX() + 0.5, descentAnchor.getY(), descentAnchor.getZ() + 0.5);
        var delta = target.subtract(position());

        if (delta.length() <= DESCENT_ARRIVAL_EPSILON) {
            arriveAtDescentAnchor(serverLevel);
            return;
        }

        var feetAir = level().getBlockState(blockPosition()).isAir();
        var belowAir = level().getBlockState(blockPosition().below()).isAir();
        if (feetAir && belowAir && position().y > target.y) {
            setDeltaMovement(0.0, -AIR_DESCENT_SPEED, 0.0);
            return;
        }

        setDeltaMovement(delta.normalize().scale(DESCENT_SPEED));
    }

    /**
     * She has reached the anchor. Found here, and stop digging.
     * <p>
     * ⚠ The spread check runs AT THE ANCHOR rather than where she set out, so a displaced empress cannot end up
     * founding somewhere the rules would have refused - the ground may well have been claimed while she dug.
     * </p>
     */
    private void arriveAtDescentAnchor(ServerLevel serverLevel) {
        var anchor = descentAnchor;
        clearDescent();
        if (anchor == null) {
            return;
        }

        setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);

        var result = com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.evaluate(this, anchor);
        var locationId = com.alien.common.gameplay.hive.lifecycle.HiveLocationFoundingService
            .foundFromResult(this, anchor, result);

        if (locationId == null) {
            // ⚠⚠ BACK OFF, DO NOT RETRY NEXT TICK. clearDescent() has already run above, so without this she is
            // immediately eligible to be sent digging again - refused, sent again, once a second, which is the
            // dig/idle bounce that was reported. A cooldown turns a hard loop into a slow retry.
            foundingRetryCooldown = FOUNDING_RETRY_COOLDOWN_TICKS;
            com.alien.Alien.LOGGER.info(
                "Empress {} dug to {} and was refused founding - waiting {}t before trying again",
                getUUID(),
                anchor,
                FOUNDING_RETRY_COOLDOWN_TICKS
            );
            return;
        }

        // ⭐ She is the empress of what she just founded, and if she broke away her grace period starts HERE - not
        // when she left, because the lineage did not exist until this moment.
        var location = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.get(locationId);
        if (location != null) {
            var faction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
            if (
                faction != null
                    && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
            ) {
                lineage.setEmpressId(getUUID());
                lineage.markDirty();
                location.setSittingEmpressId(getUUID());
                com.alien.common.gameplay.hive.empress.EmpressSchism.onFounded(serverLevel, getUUID(), lineage);
            }
        }

        com.alien.Alien.LOGGER.info("Empress {} founded {} after her descent", getUUID(), locationId);
    }

    /** The queen's numbers, shared on purpose - [stated] "same rules as the queen and speeds". */
    private static final double DESCENT_SPEED = 0.1;

    private static final double AIR_DESCENT_SPEED = 0.6;

    private static final double DESCENT_ARRIVAL_EPSILON = 0.3;

    public EmpressOvipositorManager getEmpressOvipositorManager() {
        return empressOvipositorManager;
    }

    /**
     * HEALTH she must be down before she will leave her eggsack — not damage dealt. Deliberately harder than the
     * queen's {@link QueenLifecyclePhaseManager#DISTURBANCE_THRESHOLD}: she is the apex of the lineage and should take
     * real commitment to shift, not a lucky swing.
     */
    private static final float DISTURBANCE_THRESHOLD = 25.0F;

    /** Net health lost since she was last calm. Repaid by her own regeneration. */
    private float disturbanceAccumulator;

    /** Her health as of the last sample, so the bar can follow the health bar in both directions. */
    private float lastKnownHealth = Float.NaN;

    private float lastKnownMaxHealth = Float.NaN;

    /** True once her hive has fallen and the lineage has left her behind. See {@link EmpressData}. */
    public boolean isExiled() {
        return empressData.isExiled();
    }

    /**
     * Send her into exile. One-way, and it takes her ovipositor with it - she guards the chamber from here on and will
     * never lay another egg.
     */
    public void exile() {
        if (empressData.isExiled()) {
            return;
        }
        empressData.setExiled();
        empressOvipositorManager.abandonOvipositor();
    }

    public EmpressData getEmpressData() {
        return empressData;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        // ⭐⭐ WHILE DOWN, DAMAGE EATS THE FINISHER BAR, NOT HER HEALTH - same as the queen. Checked BEFORE super.hurt
        // so the bar intercepts the blow rather than her health bar taking it first.
        if (!level().isClientSide && isIncapacitated()) {
            // !!! THE FINISHER USED TO DO NOTHING TO HER. onDamageWhileDown returns TRUE when the bar has been worked
            // to zero - the moment she is supposed to die - and this branch answered that by returning true and
            // walking away. She was never un-incapacitated, super.hurt was never called, and NOTHING killed her.
            // Reported as "the empress is incapacitaed and animated as such but she still does not take damage": the
            // damage was landing, the bar was draining, and the kill at the end of it was simply missing.
            //
            // WARNING: THE QUEEN HAS ALWAYS HAD THE FULL VERSION OF THIS BLOCK. Only the empress was left with the
            // stub, so every fix that went into the downed-royal system was tested on a queen and worked.
            //
            // * An operator kill ENDS her rather than grinding the bar. /kill and /damage carry no attacker entity,
            // and without this an admin with a stuck empress had no way to remove her at all - the same hole that was
            // closed on the queen.
            var downedAttacker = damageSource.getEntity();
            var downedDirect = damageSource.getDirectEntity();

            if (downedAttacker == null && downedDirect == null) {
                setIncapacitated(false);
                setNoAi(false);
                return super.hurt(damageSource, com.alien.common.util.LethalDamage.AMOUNT);
            }

            if (incapacitationManager.onDamageWhileDown(amount)) {
                setIncapacitated(false);
                setNoAi(false);
                return super.hurt(damageSource, com.alien.common.util.LethalDamage.AMOUNT); // finished off for real
            }

            return true; // absorbed by the bar
        }

        // ⚠ Lethal damage puts her DOWN rather than killing her, unless the manager refuses (headless, or worn down
        // past MAX_DOWNS). Also before super, or she would be dead before the question was asked.
        if (!level().isClientSide && amount >= getHealth() && incapacitationManager.onLethalDamage()) {
            return true;
        }

        var wasHurt = super.hurt(damageSource, amount);
        if (wasHurt && !level().isClientSide) {
            // ANY damage used to tear her off her eggsack - a syringe (0.01 damage), a stray splash potion, a snowball
            // would all do it. Same disturbance bar the queen uses: one hard blow, or enough small hits before the
            // accumulator bleeds off. Below that she takes the hit and keeps working, and the retaliation is cleared
            // with it so her sensors do not simply pull her off a tick later.
            if (registerDisturbance()) {
                empressOvipositorManager.abandonOvipositor();
            } else if (empressOvipositorManager.hasOvipositor()) {
                setLastHurtByMob(null);
                setTarget(null);
            }
        }
        return wasHurt;
    }

    /**
     * The empress has no lifecycle phase manager, so she keeps her own copy of the disturbance bar.
     * <p>
     * Her threshold is her OWN and is higher than the queen's - the two used to share the queen's constants, which made
     * the apex of the lineage no harder to shift than her daughters. There is no single-blow shortcut here either; a
     * heavy hit simply clears the bar the moment it is added.
     */
    private boolean registerDisturbance() {
        sampleHealth();

        if (disturbanceAccumulator >= DISTURBANCE_THRESHOLD) {
            resetDisturbance();
            return true;
        }

        return false;
    }

    /**
     * Follows her health bar in both directions: what she loses is added to the debt, what she regenerates repays it.
     * Called on every hit and once a second from {@link #tick} — the periodic call is what lets healing count.
     */
    /**
     * Wipes the disturbance debt and re-baselines on her current health, so the threshold measures health lost SINCE
     * SHE SETTLED rather than across her whole life. Same reasoning as the queen's - the debt is only repaid by
     * regeneration, and {@code Alien.canHeal} refuses while she holds a target, so a player stood beside her keeps the
     * bar frozen at whatever her last fight left on it.
     */
    public void resetDisturbance() {
        disturbanceAccumulator = 0.0F;
        lastKnownHealth = getHealth();
        lastKnownMaxHealth = getMaxHealth();
    }

    private void sampleHealth() {
        var health = getHealth();
        var maxHealth = getMaxHealth();

        if (Float.isNaN(lastKnownHealth)) {
            lastKnownHealth = health;
            lastKnownMaxHealth = maxHealth;
            return;
        }

        // A MOVING MAXIMUM IS NOT A WOUND - same rule as the queen's. Alien.applyDynamicAttributes scales current
        // health whenever a strain or empress buff changes the maximum, which would otherwise land on this bar as a
        // huge phantom wound and stand her up on the next scratch.
        if (maxHealth != lastKnownMaxHealth) {
            lastKnownHealth = health;
            lastKnownMaxHealth = maxHealth;
            return;
        }

        disturbanceAccumulator = Math.max(0.0F, disturbanceAccumulator + (lastKnownHealth - health));
        lastKnownHealth = health;
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        if (!level().isClientSide) {
            empressOvipositorManager.abandonOvipositor();
            incapacitationManager.onRemoved();
        }
        super.remove(removalReason);
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public boolean isEggLayCooldownReady() {
        return empressData.isEggLayCooldownReady();
    }

    @Override
    public void resetEggLayCooldown() {
        empressData.resetEggLayCooldown();
    }

    @Override
    public boolean hasOvipositor() {
        return empressOvipositorManager.hasOvipositor();
    }

    @Override
    public Vec3 getEggLayingPosition() {
        return empressOvipositorManager.getEggLayingPosition();
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        incapacitationManager.load(compoundTag);
        // ⚠ A downed empress must not come back with her AI switched on.
        incapacitationManager.onLoaded();
        this.crownedAtGameTime = compoundTag.getLong("CrownedAtGameTime");
        empressOvipositorManager.load(compoundTag);
        empressData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putLong("CrownedAtGameTime", crownedAtGameTime);
        incapacitationManager.save(compoundTag);
        empressOvipositorManager.save(compoundTag);
        empressData.save(compoundTag);
        // ⚠ Without this the LOAD above would read a tag nothing ever wrote, and a downed empress would stand back up
        // on relog with her bar reset - the down state has to round-trip in BOTH directions.
        incapacitationManager.save(compoundTag);
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.EMPRESS.get();
            case NETHER -> AlienEntityTypes.NETHER_EMPRESS.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_EMPRESS.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_EMPRESS.get();
        };
    }

    /**
     * END-STYLE TAKEOVER + duel seniority. [stated] a summoned/spawn-egged empress adopts the lineage the way a
     * summoned queen takes over ("i would say yes") - if the lineage she belongs to (finalizeSpawn auto-joined her on
     * placement) has NO empress, she becomes it; the 4-hive election remains the earned route. CROWN TIME is recorded
     * whenever she first holds a crown - the dual-empress duel uses it for seniority ([stated] "the second empress goes
     * into exile"): larger crownedAtGameTime = the junior.
     */
    private long crownedAtGameTime;

    /** Ticks of calm banked toward a lone empress founding. Transient - a reload simply starts the wait again. */
    private int loneSettleTicks;

    /** Ticks left before a refused founding may be attempted again. */
    private int foundingRetryCooldown;

    /**
     * How long she waits after a refusal before digging again.
     * <p>
     * Long enough that a permanently illegal spot costs almost nothing, short enough that ground freed up by a hive
     * dying nearby is taken within the minute.
     * </p>
     */
    private static final int FOUNDING_RETRY_COOLDOWN_TICKS = 20 * 30;

    /**
     * A lone empress - spawn-egged or summoned with no lineage at all - digs down and founds her own hive.
     * <p>
     * [stated] "what should happen is she digs to between y 40-26 and does the queens process with no hibernation."
     * </p>
     * <p>
     * !!! WITHOUT THIS SHE COULD NEVER FOUND ANYTHING. departToFound was reachable ONLY from EmpressSchism.relocate,
     * which only fires when she is displaced from SOMEBODY ELSE'S throne - so an empress with no lineage had no path to
     * a hive at all. She stood in a field being a very strong mob, which is exactly what was reported.
     * </p>
     * <p>
     * ⚠ NO HIBERNATION, BY DESIGN. The queen's process is developing -> location -> hibernation -> founding; the
     * empress skips the sleep and keeps the settle delay, so she does not found the instant she is placed.
     * </p>
     * <p>
     * ⚠ ONLY WHEN SHE BELONGS NOWHERE. If she is a member of any lineage, tickCrownAdoption handles her - she takes a
     * vacant crown, or the schism moves her off an occupied throne. Founding on top of either would be a second,
     * competing answer to the same question.
     * </p>
     */
    private void tickLoneFounding() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || tickCount % 40 != 0) {
            return;
        }

        if (foundingRetryCooldown > 0) {
            foundingRetryCooldown = Math.max(0, foundingRetryCooldown - 40);
            return;
        }

        if (isDigging() || isExiled() || isIncapacitated() || isInhibited() || descentAnchor() != null) {
            loneSettleTicks = 0;
            return;
        }

        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData
            ) {
                loneSettleTicks = 0;
                return; // she belongs somewhere; crown adoption or the schism owns her
            }
        }

        loneSettleTicks += 40;
        if (
            loneSettleTicks < com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
                .config()
                .settlementTicks()
        ) {
            return;
        }

        loneSettleTicks = 0;
        com.alien.Alien.LOGGER.info("Empress {} has no lineage - digging down to found her own", getUUID());
        com.alien.common.gameplay.hive.empress.EmpressSchism.foundAlone(serverLevel, this);
    }

    public long crownedAtGameTime() {
        return crownedAtGameTime;
    }

    /**
     * A summoned empress claims an EMPTY crown wherever she stands - End or overworld alike. [stated] option 2, Aug 1:
     * "people will want to rush it and then be like why no work" - a player who kills the queen and force-summons an
     * empress inside the hive's territory gets a working ruler, not an uncrowned squatter. She only ever takes a crown
     * that is VACANT (empressId null), so the emergence ritual's legitimate empresses and reigning adoptees are never
     * usurped; exiles can never re-crown. Queen presence is irrelevant either way - empresses rule ABOVE queens, and a
     * queenless lineage still heals through QueenlessMaturationTask underneath her. Membership comes from
     * finalizeSpawn's auto-join, so she must be summoned INSIDE claimed territory.
     */
    private void tickCrownAdoption() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || tickCount % 40 != 0) {
            return;
        }
        var alreadyCrowned = false;
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
            ) {
                if (getUUID().equals(lineage.empressId())) {
                    alreadyCrowned = true;
                    break;
                }
                if (lineage.empressId() == null && !isExiled()) {
                    lineage.setEmpressId(getUUID());
                    lineage.markDirty();
                    alreadyCrowned = true;
                    com.alien.Alien.LOGGER.info(
                        "Empress {} adopted lineage {} - the vacant crown is hers.",
                        getUUID(),
                        factionId
                    );
                    break;
                }
            }
        }
        if (alreadyCrowned && crownedAtGameTime == 0L) {
            this.crownedAtGameTime = serverLevel.getGameTime();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // An empress rules a lineage of hives; she must never despawn.
        return false;
    }

    @Override
    public DataAccessor<Integer> screamCooldownTicks() {
        return screamCooldownTicks;
    }

    @Override
    public DataAccessor<Boolean> screamedAtFirstThreshold() {
        return screamedAtFirstThreshold;
    }

    @Override
    public DataAccessor<Boolean> screamedAtSecondThreshold() {
        return screamedAtSecondThreshold;
    }

    /** [stated] "the scream summons 5 praetorians instead of just 3." */
    @Override
    public int praetoriansSummoned() {
        return 5;
    }

    /**
     * ⭐⭐⭐ SHE GOES DOWN LIKE THE QUEEN - the bar, the rescue, the finisher, all of it.
     * <p>
     * [stated] "for her the incapacited cant be chained like queen and cant be inhibited. the other features are the
     * same. the bar the rescure the finishing off all of it."
     * </p>
     * <p>
     * ⚠⚠ THE MANAGER IS SHARED, NOT COPIED. Chaining and inhibiting were never part of it - they are a separate capture
     * arc that happens to use the same moment - so the empress gets the whole mechanic simply by holding one.
     * </p>
     */
    private final com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenIncapacitationManager<Empress> incapacitationManager =
        new com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenIncapacitationManager<>(this);

    public com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenIncapacitationManager<Empress> getIncapacitationManager() {
        return incapacitationManager;
    }

    public final DataAccessor<Boolean> isIncapacitatedSynced =
        new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_INCAPACITATED.get());

    @Override
    public boolean isIncapacitated() {
        return isIncapacitatedSynced.get();
    }

    @Override
    public void setIncapacitated(boolean value) {
        isIncapacitatedSynced.set(value);
    }

    /** \u26a0 Her sack manager is a different type from the queen's, which is the only reason this bridge exists. */
    @Override
    public void abandonOvipositorForIncapacitation() {
        getEmpressOvipositorManager().abandonOvipositor();
    }

    /**
     * \u2b50\u2b50 SHE CARVES HER OWN CHAMBER - [stated] "the empress now carves too if shes forced in early."
     * <p>
     * \u26a0 Shares QUEEN_IS_STAND_DIGGING with the queen deliberately, so there is ONE definition of "this royal is
     * carving" and the two animators cannot disagree about it.
     * </p>
     */
    public final DataAccessor<Boolean> standDiggingSynced =
        new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_STAND_DIGGING.get());

    @Override
    public boolean isStandDigging() {
        return standDiggingSynced.get();
    }

    @Override
    public void setStandDigging(boolean value) {
        standDiggingSynced.set(value);
    }

    /** \u26a0 Always. She cannot be inhibited and cannot be chained, so nothing can restrain her out of carving. */
    @Override
    public boolean canCarve() {
        return true;
    }

    /**
     * ⚠ Her own copy of the queen's crew spawn - she had none, because until now she never founded anything and so
     * never needed to staff a carve site. Same size, same drone type resolution, same persistence.
     */
    @Override
    public void spawnFoundingCrewForCarve() {
        if (level().isClientSide) {
            return;
        }
        var droneType = com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone.getType(getVariant());
        if (droneType == null) {
            return;
        }
        for (var i = 0; i < FOUNDING_CREW_SIZE; i++) {
            var drone = droneType.spawn(
                (ServerLevel) level(),
                blockPosition(),
                net.minecraft.world.entity.MobSpawnType.NATURAL
            );
            if (drone != null) {
                drone.setPersistenceRequired();
            }
        }
    }

    /** Matches the queen's founding crew exactly. */
    private static final int FOUNDING_CREW_SIZE = 4;

    @Override
    public net.minecraft.world.entity.Mob asMob() {
        return this;
    }

    /** Her facing while seated on her eggsack (null when not seated). */
    private @org.jetbrains.annotations.Nullable Float seatedYRotLock;

    /**
     * Whether she is riding her eggsack. Safe on the CLIENT: the sack is her passenger, and passengers are synced -
     * unlike the server-only EmpressOvipositorManager.
     */
    public boolean isRidingEmpressOvipositor() {
        return getPassengers()
            .stream()
            .anyMatch(passenger -> passenger.getType() == AlienEntityTypes.EMPRESS_OVIPOSITOR.get());
    }

    /** [stated] Oct 5: no movement at all on the sack. Vertical velocity is kept so gravity still settles her. */
    @Override
    public void travel(@org.jetbrains.annotations.NotNull net.minecraft.world.phys.Vec3 vec3) {
        if (isRidingEmpressOvipositor()) {
            var velocity = getDeltaMovement();
            setDeltaMovement(0.0, velocity.y, 0.0);
            super.travel(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }

        super.travel(vec3);
    }
}
