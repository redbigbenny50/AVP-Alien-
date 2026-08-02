package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.ai.EmpressGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Empress extends Xenomorph implements GOAPUser<Empress>, EggLayer, com.alien.common.gameplay.entity.CrawlPostureTransitionListener {

    @Override
    public int crawlPostureTransitionTicks(boolean enteringCrawl) {
        return enteringCrawl ? EmpressAnimationRefs.CRAWL_DROP_TICKS : EmpressAnimationRefs.CRAWL_RISE_TICKS;
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
                .build()
        )
        .parallelDigCount(4)
        .pushedByFluid(false)
        .build();

    public static AttributeSupplier.Builder createEmpressAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 20.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 16.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 1.5F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 25F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 0.9F);
    }

    private final EmpressAnimationDispatcher animationDispatcher;

    private final EmpressOvipositorManager empressOvipositorManager;

    private final EmpressData empressData;

    public Empress(EntityType<? extends Empress> entityType, Level level) {
        super(entityType, level, CONFIG);
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

    @Override
    public void tick() {
        super.tick();
        empressOvipositorManager.tick();
        empressData.tick();
        tickEndAdoption();
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

    public EmpressOvipositorManager getEmpressOvipositorManager() {
        return empressOvipositorManager;
    }

    /** Damage accumulated toward being pulled off the eggsack, and when it was last added to. */
    private float disturbanceAccumulator;

    private int lastDisturbanceTick = Integer.MIN_VALUE;

    /**
     * Gap after which the accumulator is considered cold. Matches the queen's decay closely enough to feel the same.
     */
    private static final int DISTURBANCE_WINDOW_TICKS = 120;

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
        var wasHurt = super.hurt(damageSource, amount);
        if (wasHurt && !level().isClientSide) {
            // ANY damage used to tear her off her eggsack - a syringe (0.01 damage), a stray splash potion, a snowball
            // would all do it. Same disturbance bar the queen uses: one hard blow, or enough small hits before the
            // accumulator bleeds off. Below that she takes the hit and keeps working, and the retaliation is cleared
            // with it so her sensors do not simply pull her off a tick later.
            if (registerDisturbance(amount)) {
                empressOvipositorManager.abandonOvipositor();
            } else if (empressOvipositorManager.hasOvipositor()) {
                setLastHurtByMob(null);
                setTarget(null);
            }
        }
        return wasHurt;
    }

    /**
     * The empress has no lifecycle phase manager, so she keeps her own copy of the queen's disturbance bar. Thresholds
     * are shared deliberately - the two should feel identical to hit.
     */
    private boolean registerDisturbance(float amount) {
        if (amount >= QueenLifecyclePhaseManager.HIBERNATION_DISTURBANCE_DAMAGE) {
            disturbanceAccumulator = 0.0F;
            return true;
        }

        if (tickCount - lastDisturbanceTick > DISTURBANCE_WINDOW_TICKS) {
            disturbanceAccumulator = 0.0F;
        }
        lastDisturbanceTick = tickCount;

        disturbanceAccumulator += amount;
        if (disturbanceAccumulator >= QueenLifecyclePhaseManager.SUSTAINED_DISTURBANCE_DAMAGE) {
            disturbanceAccumulator = 0.0F;
            return true;
        }

        return false;
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        if (!level().isClientSide) {
            empressOvipositorManager.abandonOvipositor();
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
        this.crownedAtGameTime = compoundTag.getLong("CrownedAtGameTime");
        empressOvipositorManager.load(compoundTag);
        empressData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putLong("CrownedAtGameTime", crownedAtGameTime);
        empressOvipositorManager.save(compoundTag);
        empressData.save(compoundTag);
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

    public long crownedAtGameTime() {
        return crownedAtGameTime;
    }

    private void tickEndAdoption() {
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
                if (
                    com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel)
                        && lineage.empressId() == null
                        && !isExiled()
                ) {
                    lineage.setEmpressId(getUUID());
                    lineage.markDirty();
                    alreadyCrowned = true;
                    com.alien.Alien.LOGGER.info(
                        "End: summoned empress {} adopted lineage {} - the crown is hers.",
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
}
