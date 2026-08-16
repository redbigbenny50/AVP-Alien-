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
import net.minecraft.nbt.CompoundTag;
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

public class Empress extends Xenomorph implements GOAPUser<Empress>, EggLayer, com.alien.common.gameplay.entity.CrawlPostureTransitionListener, QueenScreamDefense.ScreamingRoyal {

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

        // ⭐ THE SCREAM, shared with the queen through QueenScreamDefense.ScreamingRoyal - she just calls five.
        if (!level().isClientSide && QueenScreamDefense.tick(this)) {
            screamId.set(screamId.get() + 1);
        }

        // Once a second, so her regeneration repays the disturbance debt. Without this the bar would only ever climb.
        if (!level().isClientSide && tickCount % 20 == 0) {
            sampleHealth();
        }

        empressOvipositorManager.tick();
        empressData.tick();
        tickCrownAdoption();
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
}
