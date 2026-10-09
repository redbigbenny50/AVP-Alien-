package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.QueenGOAP;
import com.alien.common.gameplay.hive.lifecycle.QueenInhibitionService;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Queen extends Xenomorph implements GOAPUser<Queen>, EggLayer, com.alien.common.gameplay.entity.CrawlPostureTransitionListener, QueenScreamDefense.ScreamingRoyal, com.alien.common.gameplay.entity.living.alien.xenomorph.IncapacitatableRoyal, com.alien.common.gameplay.entity.living.alien.xenomorph.CarvingRoyal {

    @Override
    public int crawlPostureTransitionTicks(boolean enteringCrawl) {
        return enteringCrawl ? QueenAnimationRefs.CRAWL_DROP_TICKS : QueenAnimationRefs.CRAWL_RISE_TICKS;
    }

    /**
     * ⭐⭐ THE BITE. [stated] "a standard bite it can be mixed in with regular attacks if the target is infront of her.
     * same for the crawl bite and also incase she loses both arms this would become the default attack."
     * <p>
     * ⚠⚠ THE "DEFAULT WHEN ARMLESS" HALF NEEDED NO CODE - and that is worth knowing rather than adding a second
     * mechanism for it. Every other attack she has requires an arm ({@code SWIPE_DOWN}, {@code BACKHAND},
     * {@code CRAWL_ATTACK}) or a tail ({@code TAIL_STRIKE}), so a queen who loses both arms is left with the bite and
     * the head ram as the only things the limb gate still admits. It becomes her default by elimination.
     * </p>
     * <p>
     * ⚠ THE FACING CONE IS RELAXED WHEN SHE IS ARMLESS. Otherwise the one attack she has left could be refused because
     * a target slipped behind her, and she would stand there doing nothing at all - the failure mode the "default
     * attack" clause exists to prevent.
     * </p>
     */
    private static final double BITE_FACING_DOT = 0.5; // ~120 degree cone in front

    public static final AttackType BITE = AttackType.builder("queen_bite")
        .requiresHead()
        .activationCondition(Queen::canBite)
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /** The prone bite. Same rules; the posture gate confines it to the ground. */
    public static final AttackType CRAWL_BITE = AttackType.builder("queen_crawl_bite")
        .crawlAttack()
        .requiresHead()
        .activationCondition(Queen::canBite)
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * Is her target in front of her - or is the bite all she has left?
     * <p>
     * ⚠ NO TARGET MEANS YES. The router may score an attack before a target is resolved, and refusing on a null target
     * would make the bite unpickable rather than merely unlucky.
     * </p>
     */
    private static boolean canBite(Xenomorph xenomorph) {
        if (hasNoArms(xenomorph)) {
            return true;
        }

        var target = xenomorph.getTarget();

        if (target == null) {
            return true;
        }

        var toTarget = target.position().subtract(xenomorph.position());
        var flat = new Vec3(toTarget.x, 0.0, toTarget.z);

        if (flat.lengthSqr() < 1.0E-4) {
            return true; // stood on top of her - there is no "behind" to speak of
        }

        var look = xenomorph.getLookAngle();
        var facing = new Vec3(look.x, 0.0, look.z).normalize();

        return facing.dot(flat.normalize()) >= BITE_FACING_DOT;
    }

    private static boolean hasNoArms(Xenomorph xenomorph) {
        return MirroredAttackSide.isArmDetached(xenomorph, true)
            && MirroredAttackSide.isArmDetached(xenomorph, false);
    }

    public static final AttackType SWIPE_DOWN = AttackType.builder("queen_swipe_down")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BACKHAND = AttackType.builder("queen_backhand")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * Her ground game: [stated] a crawling xenomorph fights with crawl attacks, and she has the clip
     * ({@code crawl_attack}, 0.5s). Marked {@code crawlAttack()} so the posture gate confines it to the ground and the
     * config's crawl preference makes it her ONLY pick while crawling - a legless queen is still a queen.
     */
    public static final AttackType CRAWL_ATTACK = AttackType.builder("queen_crawl_attack")
        .crawlAttack()
        .requiresAnyArm()
        .defaultDurationInTicks(QueenAnimationRefs.CRAWL_ATTACK_DURATION_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⭐⭐ THE HEAD RAM. [stated] "its a knock back if it hits mobs or a player an aoe knockback to anything 5x5x3
     * infront of her so up to 3 blocks out. also if she rams a wall she breaks blocks in that pattern if its in the
     * xeno break list. it does medium damage if hit has a cool down of 120s."
     * <p>
     * The 120s cooldown is enforced by the existing {@code AttackCooldownTracker} through
     * {@code AttackType.cooldownInTicks} - no new timer, GOAP action or sensor.
     * </p>
     */
    private static final int HEAD_RAM_COOLDOWN_TICKS = 120 * 20;

    public static final AttackType HEAD_RAM = QueenHeadRamAttack.create(
        "queen_head_ram",
        HEAD_RAM_COOLDOWN_TICKS,
        22
    );

    public static final AttackType TAIL_STRIKE = AttackType.builder("queen_tail_strike")
        .requiresTail()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.WIDE_TALL, Queen::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(SWIPE_DOWN)
                .addRegular(BACKHAND)
                .addRegular(TAIL_STRIKE)
                .addRegular(HEAD_RAM)
                .addRegular(BITE)
                .addRegular(CRAWL_BITE)
                .addRegular(CRAWL_ATTACK)
                .build()
        )
        .parallelDigCount(4)
        .pushedByFluid(false)
        // A queen ducks too. She is 3.8 x 5.0 and needs a FIVE-block opening standing, which no ordinary corridor
        // gives her - crawling scales her to 2.0 and drops that to two. Her crawl set was fully animated all along
        // (crawl, crawl.idle, crawl.rise, crawl.drop, crawl_attack) and wired in QueenAnimationDispatcher; only
        // this flag kept any of it from ever playing.
        .canCrawl(true)
        .canCrawlAfterLegLoss(true)
        .build();

    /**
     * Must match {@code QueenLifecyclePhaseManager}'s phase tag — its absence in a save marks a pre-lifecycle queen.
     */
    private static final String LIFECYCLE_PHASE_TAG = "lifecyclePhase";

    private static final String LEGACY_DORMANT_TAG = "legacyDormant";

    private static final String LEGACY_DORMANT_COMPAT_TAG = "LegacyDormant";

    public static AttributeSupplier.Builder createQueenAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 20.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 1F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 12.5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.1F);
    }

    private final QueenAnimationDispatcher animationDispatcher;

    private final OvipositorManager ovipositorManager;

    private final QueenIncapacitationManager<Queen> incapacitationManager;

    private final QueenData queenData;

    private final QueenLifecyclePhaseManager lifecyclePhaseManager;

    /**
     * Synced chain count for the client (shackle reveal + chain render). The bind manager keeps it in lockstep with the
     * anchor list each server tick.
     */
    public final DataAccessor<Integer> bindChainCount;

    private final QueenBindManager bindManager;

    private final QueenRescueManager rescueManager;

    /**
     * Synced + persisted: whether the inhibitor device is attached. Drives the {@code gInhibitor} bone reveal and (in
     * later slices) the contained-breeder behaviour — hive autonomy off, claim capped at one chunk.
     */
    public final DataAccessor<Boolean> hasInhibitor;

    public final DataAccessor<Boolean> tracked;

    /** Synced + persisted: the involuntary, defeat-induced downed state. Drives the incapacitated animations. */
    /** ⭐ The defensive scream: its cooldown, and one latch per health threshold. See QueenScreamDefense. */
    public final DataAccessor<Integer> screamCooldownTicks;

    public final DataAccessor<Boolean> screamedAtFirstThreshold;

    public final DataAccessor<Boolean> screamedAtSecondThreshold;

    /** Bumped on every scream; the animator edge-detects it so the clip plays exactly once. */
    public final DataAccessor<Integer> screamId;

    public final DataAccessor<Boolean> incapacitated;

    /**
     * Synced, transient: true while she is carving her founding chamber (construction economy step 6). Set by the carve
     * tick server-side; the client QueenAnimator drives the stand-dig animation triptych off its edges.
     */
    public final DataAccessor<Boolean> standDiggingSynced;

    /**
     * Transient: true while clip-digging to her location anchor (Stage 2b). Not saved — a reload never stays noclip.
     */
    private boolean digging;

    /**
     * Facing captured the moment she becomes a pacified captive breeder; held so she doesn't turn under the eggsack.
     */
    private Float containedYRotLock = null;

    /**
     * Legacy-recovery state. A queen saved before the lifecycle system existed loads without a {@code lifecyclePhase}
     * tag; {@link #wasLoadedWithoutLifecycleState()} reports that so {@code LegacyHiveRecovery} can treat her as a
     * legacy queen. She is parked {@link #isLegacyDormant() legacy-dormant} until recovery wakes her via
     * {@link #wakeFromLegacyDormantRecovery()}, which hands her back to the normal lifecycle (LOCATION phase).
     */
    private boolean legacyDormant;

    /** Transient: set at load time when the save carried no lifecycle-phase state (a pre-lifecycle-system queen). */
    private boolean loadedWithoutLifecycleState;

    public Queen(EntityType<? extends Queen> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new QueenAnimationDispatcher(this);
        this.ovipositorManager = new OvipositorManager(this);
        this.incapacitationManager = new QueenIncapacitationManager<>(this);
        this.queenData = new QueenData();
        this.lifecyclePhaseManager = new QueenLifecyclePhaseManager(this);
        this.bindChainCount = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_BIND_CHAIN_COUNT.get());
        this.bindManager = new QueenBindManager(this);
        this.rescueManager = new QueenRescueManager(this);
        this.hasInhibitor = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_HAS_INHIBITOR.get());
        this.tracked = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_TRACKED.get());
        this.incapacitated = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_INCAPACITATED.get());
        this.screamCooldownTicks = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_SCREAM_COOLDOWN_TICKS.get());
        this.screamedAtFirstThreshold =
            new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_SCREAMED_AT_FIRST_THRESHOLD.get());
        this.screamedAtSecondThreshold =
            new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_SCREAMED_AT_SECOND_THRESHOLD.get());
        this.screamId = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_SCREAM_ID.get());
        this.standDiggingSynced = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_STAND_DIGGING.get());
    }

    @Override
    public Agent.Builder<Queen> blib$applyGOAPAgentProperties(Agent.Builder<Queen> agentBuilder) {
        return QueenGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Queen> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(isOnOvipositor() ? QueenGOAP.OVIPOSITOR_GRAPH : QueenGOAP.GRAPH);
    }

    private boolean isOnOvipositor() {
        return ovipositorManager != null && ovipositorManager.hasOvipositor();
    }

    /** How far from her throne she tolerates being before drifting back. Generous - this is duty, not a tether. */
    private static final double LEASH_RADIUS_BLOCKS = 24.0;

    private static final double LEASH_RADIUS_SQUARED = LEASH_RADIUS_BLOCKS * LEASH_RADIUS_BLOCKS;

    /** Unhurried. She is going home, not responding to anything. */
    private static final double LEASH_RETURN_SPEED = 0.8;

    /** Checked rarely; a boss ambling home does not need per-tick pathing. */
    private static final int LEASH_CHECK_INTERVAL_TICKS = 40;

    /**
     * An irradiated queen keeps to her broken throne, but is not chained to it.
     * <p>
     * [stated] "she will try to stay in her chamber out of duty and a boss like fight, but shes NOT LOCKED TO THE
     * CENTRE trying to make an eggsack." Her chamber is the hive's CORE CHUNKS - [stated] "thats her broken throne".
     * <p>
     * Deliberately a PULL and not a pin. She only drifts home when she has nothing to fight, so a player cannot park
     * outside her chamber and plink at her while a leash drags her back out of reach - if she has a target she goes and
     * gets it, wherever it stands. And it only applies while she HAS a hive: off her slab she is [stated] "just a
     * roaming weapon of radioactive teeth and claws" with nowhere to be.
     */
    private void tickIrradiatedChamberLeash() {
        if (level().isClientSide || tickCount % LEASH_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        if (!com.alien.common.gameplay.hive.economy.IrradiatedHiveRules.isIrradiated(this) || getTarget() != null) {
            return;
        }

        var location = com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver.reserveReturnLocation(this);
        if (location == null || !location.isAlive()) {
            return;
        }

        var throne = location.centerPos();
        if (blockPosition().distSqr(throne) <= LEASH_RADIUS_SQUARED) {
            return;
        }

        getNavigation().moveTo(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, LEASH_RETURN_SPEED);
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

    /** [stated] "3 praetoraians are summoned to her defense". The empress calls five. */
    @Override
    public int praetoriansSummoned() {
        return 3;
    }

    @Override
    public void tick() {
        super.tick();

        // Oct 5 - profiler v3 laps; free while no session runs.
        var perfLap = com.blib.api.common.perf.v1.BLibPerf.start();
        tickSuspension();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.suspension", perfLap);

        // ⭐⭐ SHE CANNOT BE DISMEMBERED WHILE SEATED ON THE EGGSACK. [stated] "make it a rule the queen cant lose
        // any limbs while riding the eggsack."
        //
        // ⚠ IT CLEARS ACCUMULATED LIMB DAMAGE; IT DOES NOT BLOCK DAMAGE. She still takes health damage normally
        // and can still be killed on the sack - what she cannot do is have a limb reach its detach threshold. A
        // shooter is not made to waste ammunition, they are made to drive her off the sack first.
        //
        // ⚠ WHY CLEARING RATHER THAN GATING THE DETACH: the threshold accrual lives inside BLib's limb system,
        // which avp_alien cannot intercept. Zeroing the pool each tick is the one lever on this side, and it has
        // the right shape anyway - punishment landed while she is seated simply does not persist toward a limb.
        //
        // ⚠ IT ALSO REMOVES THE SITUATION I FLAGGED AS MY LEADING SUSPECT FOR THE QUEEN-LEG CRASH: a limb coming
        // off while she is a VEHICLE CARRYING A PASSENGER is the one interaction no other caste can produce. This
        // is not a fix for that crash - if the cause lies elsewhere it will still happen off the sack - but it
        // takes the riskiest version of it off the table.
        // ⚠ Dismemberable is an interface Alien implements conditionally - go through it rather than assuming.
        if (
            !level().isClientSide
                && isRidingOvipositor()
                && this instanceof com.blib.api.common.dismemberment.v1.Dismemberable dismemberable
        ) {
            dismemberable.getDismembermentManager().healLimbDamage(Float.MAX_VALUE);
        }

        // !!! NOTHING GROWS INSIDE THE COCOON. The ovipositor manager runs off the ENTITY tick, not off the GOAP
        // graph, so the exclusive cocoon graph never gated it - a royal mid-molt kept growing an eggsack, which
        // pushed her out of her own cage before EMERGING. That aborted the handover chain, and since RoyalCocoon has
        // no tick of its own the abandoned cage then stood there permanently.
        //
        // ⚠ Reported as "she left the cocoon and then placed another sack ... it counts her done and she makes a sack
        // even though shes still molting". One missing check, three visible symptoms.
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.seatedLimbHeal", perfLap);
        if (!getCocoonManager().shouldRunCocoonAction()) {
            ovipositorManager.tick();
        }
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.ovipositorManager", perfLap);
        queenData.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.data", perfLap);
        lifecyclePhaseManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.lifecycle", perfLap);
        bindManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.bind", perfLap);
        rescueManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.rescue", perfLap);
        incapacitationManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.incapacitation", perfLap);

        // 🚨🚨 THE QUEEN NEVER SCREAMED. QueenScreamDefense is written for both royals through ScreamingRoyal, and the
        // queen implements it, has her own QUEEN_SCREAM_ID sync key, her own screamAttack() dispatcher entry and a
        // special.attack.scream clip in her animation file - but NOTHING EVER CALLED QueenScreamDefense.tick FOR HER.
        // Only Empress did. So her sound, her stun, her praetorian summon and her animation were all unreachable.
        //
        // ⚠ Reported as "the few times ive seen it activate i dont hear the scream ... i see the guards appear
        // though" - those were EMPRESS screams; the queen's had never fired at all.
        //
        // ⭐ Bumping screamId is what the animator watches, exactly as the empress does. [stated] the queen calls
        // three praetorians, the empress five - that difference is already in praetoriansSummoned().
        if (!level().isClientSide && QueenScreamDefense.tick(this)) {
            screamId.set(screamId.get() + 1);
        }

        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.scream", perfLap);
        tickIrradiatedChamberLeash();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.irradiatedLeash", perfLap);

        // A pacified captive breeder holds still AND holds her FACING: idle look control would keep turning her body
        // in place, twisting her against the eggsack that is anchored to her rotation. Capture her facing once when
        // she enters the state and pin body + head to it every tick; release it when she is no longer contained.
        // [stated] Oct 5: "she shouldnt move at all while on the sack turning or otherwise" - captive OR founding. Her
        // clutch zone is laid out from her facing, so every idle turn swung it round and eggs rooted before the turn
        // ended up under her. The lock now holds for any queen riding her sack, not only a captive one.
        if (isRidingOvipositor()) {
            if (containedYRotLock == null) {
                // Capture the settled facing (yBodyRot is what the eggsack copied at creation) so body and
                // eggsack hold the exact same angle.
                containedYRotLock = yBodyRot;
            }
            setYRot(containedYRotLock);
            yBodyRot = containedYRotLock;
            yHeadRot = containedYRotLock;
        } else if (containedYRotLock != null) {
            containedYRotLock = null;
        }

        // ⭐ Oct 3 - CAPTIVITY BOOKKEEPING. Starts her release grace the tick she goes from captive to free, and clears
        // the old one-chunk inhibitor claim out of worlds saved before captives stopped holding claims. The
        // follow-chunk
        // claim that used to tick here is gone: a captive holds no claim at all now. See QueenCaptivity.
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.rotationLock", perfLap);
        com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.tick(this);
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.captivity", perfLap);
        // \u2b50 Oct 3 - a convoy-delivered daughter is sited and given her queen chamber like any queen on foot.
        com.alien.common.gameplay.hive.lifecycle.DaughterHiveSiting.tick(this);
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.daughterSiting", perfLap);

        if (isTracked() && level() instanceof ServerLevel trackedLevel) {
            TrackedQueenRegistry.getOrCreate(trackedLevel).ifSome(registry -> {
                if (registry.consumePendingDestroy(getUUID())) {
                    // "Destroy tracker" was requested while she was unloaded; clear the tag now instead of re-adding.
                    setTracked(false);
                } else if (tickCount % 40 == 0) {
                    registry.updatePosition(
                        getUUID(),
                        blockPosition(),
                        trackedLevel.dimension(),
                        trackedLevel.getGameTime()
                    );
                }
            });
        }

        com.blib.api.common.perf.v1.BLibPerf.lap(this, "queen.trackedRegistry", perfLap);
    }

    @Override
    public void startAttack(AttackType attack, @Nullable LivingEntity target) {
        int attackIdBefore = attackId.get();
        super.startAttack(attack, target);
        // A real attack just began (attackId advanced) — give her capture chains a chance to snap (1-3 chains only).
        if (attackId.get() != attackIdBefore) {
            bindManager.onQueenAttack();
        }
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return Objects.equals(passenger.getType(), AlienEntityTypes.OVIPOSITOR.get());
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType() == AlienEntityTypes.OVIPOSITOR.get()) {
            var relativePos = com.blib.api.common.entity.v1.EntityUtil.getRelativePosition(this, 3, 0.01, 5.25);
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        super.positionRider(passenger, callback);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        @NotNull ServerLevelAccessor serverLevelAccessor,
        @NotNull DifficultyInstance difficulty,
        @NotNull MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnType == MobSpawnType.NATURAL) {
            applyNaturalSpawnEffects();
        }

        // PLAYER-PLACED marker for the hibernation rule. finalizeSpawn's auto-join makes any queen spawned inside
        // a claimed chunk a hive-location member instantly, which made a spawn-egged queen indistinguishable from
        // a dispatched daughter - and she skipped her sleep ([stated] tester report: "wild queen hibernation is
        // still ending in 1 second"; the log showed "sleeping 0 ticks"). Spawn TYPE is the true discriminator:
        // transitions never call finalizeSpawn, so a promoted daughter can never carry this flag, while an egg,
        // command, or dispenser queen always does. The lifecycle manager reads it alongside the membership test.
        if (
            spawnType == MobSpawnType.SPAWN_EGG
                || spawnType == MobSpawnType.COMMAND
                || spawnType == MobSpawnType.BUCKET
                || spawnType == MobSpawnType.DISPENSER
        ) {
            this.playerPlaced = true;
            // ⭐ Oct 3 - THE ARRIVAL GRACE. [stated] a queen who "freshly appear[s] in overlapping hive territory" gets
            // 5 minutes before she may be adopted, found or dig - but ONLY outside a hive's slab ("any new queens born
            // or summoned in a hive slab follow the same rules currently"). Set BEFORE super, because super is where
            // the spawn auto-join runs, and that join asks the grace. Inside a slab the grace does not bite and she
            // joins exactly as before.
            com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.startArrivalGrace(this);
        }

        return super.finalizeSpawn(serverLevelAccessor, difficulty, spawnType, spawnGroupData);
    }

    /** See finalizeSpawn - persisted so a relog cannot turn a placed queen into a "dispatched daughter". */
    private boolean playerPlaced;

    // ---- Oct 3: fishing-rod pull on a chained-sack captive (see MixinFishingHook_NoRoyalPull) ----

    /** Ticks the seated-captive travel freeze stands aside for a rod's pull. Transient. */
    private int externalPullTicks;

    /**
     * How long one reel-in is allowed to carry her before the freeze resumes - long enough for friction to spend it.
     */
    private static final int EXTERNAL_PULL_WINDOW_TICKS = 10;

    public void allowExternalPull() {
        this.externalPullTicks = EXTERNAL_PULL_WINDOW_TICKS;
    }

    // ---- Oct 3: daughter hive siting (see DaughterHiveSiting) ----

    /** The daughter location a founder convoy delivered her to, until it is sited. Persisted. */
    private @Nullable String pendingDaughterLocationId;

    /** Siting is settled for this queen this session (done, or not applicable). Transient. */
    private boolean daughterSitingDone;

    /** Earliest game time the next siting search may run, and how many have failed. Transient. */
    private long daughterSitingRetryAt;

    private int daughterSitingFailures;

    public long getDaughterSitingRetryAt() {
        return daughterSitingRetryAt;
    }

    public int getDaughterSitingFailures() {
        return daughterSitingFailures;
    }

    public void recordDaughterSitingFailure(long retryAt) {
        this.daughterSitingRetryAt = retryAt;
        this.daughterSitingFailures++;
    }

    public @Nullable String getPendingDaughterLocationId() {
        return pendingDaughterLocationId;
    }

    public void setPendingDaughterLocationId(@Nullable String id) {
        this.pendingDaughterLocationId = id;
        if (id != null) {
            this.daughterSitingDone = false;
        }
    }

    public boolean isDaughterSitingDone() {
        return daughterSitingDone;
    }

    public void setDaughterSitingDone(boolean value) {
        this.daughterSitingDone = value;
    }

    // ---- Oct 3: captivity grace (see QueenCaptivity) ----

    /** Game tick her release/arrival grace ends. 0 = none. Persisted so a relog can neither skip nor restart it. */
    private long captiveGraceUntil;

    /** True for the 30s kin-rescue grace, which bites inside a slab too; false for the 5-minute grace. Persisted. */
    private boolean captiveGraceIgnoresSlab;

    /**
     * Whether she was captive last tick - the edge that starts the grace. Persisted so a release while unloaded counts.
     */
    private boolean wasCaptive;

    /** Her kin broke a chain since she was last chained. Transient: a reload simply gives her the longer grace. */
    private boolean freedByKin;

    /** Old-save cleanup already ran this session. Transient on purpose - it is cheap and idempotent. */
    private boolean legacyCaptivityChecked;

    public long getCaptiveGraceUntil() {
        return captiveGraceUntil;
    }

    public boolean captiveGraceIgnoresSlab() {
        return captiveGraceIgnoresSlab;
    }

    /** Starts (or replaces) her grace: {@code ticks} from now. */
    public void startCaptiveGrace(int ticks, boolean ignoresSlab) {
        this.captiveGraceUntil = level().getGameTime() + ticks;
        this.captiveGraceIgnoresSlab = ignoresSlab;
    }

    public boolean wasCaptive() {
        return wasCaptive;
    }

    public void setWasCaptive(boolean value) {
        this.wasCaptive = value;
    }

    public void markFreedByKin() {
        this.freedByKin = true;
    }

    public void clearFreedByKin() {
        this.freedByKin = false;
    }

    /** Reads and clears the kin-rescue credit. */
    public boolean consumeFreedByKin() {
        var value = freedByKin;
        freedByKin = false;
        return value;
    }

    public boolean isLegacyCaptivityChecked() {
        return legacyCaptivityChecked;
    }

    public void setLegacyCaptivityChecked(boolean value) {
        this.legacyCaptivityChecked = value;
    }

    public boolean isPlayerPlaced() {
        return playerPlaced;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Queens are persistent by nature -- they anchor a hive and may be tracked, so they must never despawn.
        return false;
    }

    private void applyNaturalSpawnEffects() {
        var level = level();

        if (level.isClientSide) {
            return;
        }

        // NO SPAWN ANNOUNCEMENT HERE. The world-genesis line is broadcast by
        // QueenLifecyclePhaseManager.beginWildImmediateFounding, which already plays ENTITY_QUEEN_SCREAM
        // itself (broadcastToNearbyPlayers withQueenScream = true). This call duplicated both the scream and
        // the message for the first wild queen, and announced every LATER wild queen too - which contradicts
        // the design note on beginWildImmediateFounding: later wild queens are DISCOVERED, not announced.
        spawnGuards();
        resetQueenSpawnCooldown();

        StrainLeakData.getOrCreate(level)
            .ifSome(strainLeakData -> strainLeakData.add(getVariant(), -1));
    }

    /** Escort for a freshly spawned wild queen - she has a long walk ahead and nothing else to defend her. */
    private static final int SPAWN_ESCORT_SIZE = 4;

    /** Workforce handed to a queen the moment she wakes to found. See {@link #spawnFoundingCrew()}. */
    private static final int FOUNDING_CREW_SIZE = 4;

    private void spawnGuards() {
        spawnDrones(SPAWN_ESCORT_SIZE);
    }

    /**
     * Give a waking queen a founding crew.
     * <p>
     * [stated] "this queen when i woke her from hibernation she didnt spawn with any helper drones." She would not
     * have: the only drone spawn was {@link #spawnGuards()}, fired from finalizeSpawn and ONLY for
     * {@code MobSpawnType.NATURAL}. Those four appear at spawn time, and she then spends five minutes developing, walks
     * to her anchor and sleeps three Minecraft days - so they have long scattered by the time she founds. A spawn-egged
     * or summoned queen never had any at all.
     * <p>
     * This matters beyond flavour: the founding core is queen-dug, but every piece AFTER it needs drone diggers, and a
     * hive with none logs "carve site is unstaffed - no free drones, nothing in reserve. Build paused." indefinitely. A
     * queen who wakes alone cannot dig her way out of that.
     */
    public void spawnFoundingCrew() {
        spawnDrones(FOUNDING_CREW_SIZE);
    }

    private void spawnDrones(int count) {
        if (level().isClientSide) {
            return;
        }

        var droneType = Drone.getType(getVariant());

        for (var i = 0; i < count; i++) {
            var drone = droneType.spawn((ServerLevel) level(), blockPosition(), MobSpawnType.NATURAL);

            if (drone != null) {
                drone.setPersistenceRequired();
            }
        }
    }

    private void resetQueenSpawnCooldown() {
        QueenSpawnChunkData.getOrCreate(level())
            .ifSome(queenSpawnChunkData -> queenSpawnChunkData.getSpawnCooldown().reset());
    }

    @Override
    public float maxUpStep() {
        return 2.5F;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return AlienSoundEvents.ENTITY_QUEEN_IDLE.get();
    }

    @Override
    protected @NotNull SoundEvent getDeathSound() {
        return AlienSoundEvents.ENTITY_QUEEN_DEATH.get();
    }

    @Override
    protected @NotNull SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return AlienSoundEvents.ENTITY_QUEEN_HURT.get();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (
            !ovipositorManager.hasOvipositor()
                || !entity.getType().is(AlienEntityTypeTags.ALIENS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    public QueenAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public OvipositorManager getOvipositorManager() {
        return ovipositorManager;
    }

    public QueenData getQueenData() {
        return queenData;
    }

    public QueenLifecyclePhaseManager getLifecyclePhaseManager() {
        return lifecyclePhaseManager;
    }

    /**
     * True if this queen was loaded from a save that predates the lifecycle system (no lifecycle-phase tag was
     * present). Used by {@code LegacyHiveRecovery} to identify queens that need migrating into the current lifecycle.
     */
    /**
     * Called by {@link QueenLifecyclePhaseManager#load} when the save carried no lifecycle-phase tag - i.e. a queen
     * from the original AVP mod or avp_alien 0.1.4 and earlier.
     * <p>
     * ⚠ Transient by design: it describes how this queen ARRIVED, not what she is, and it is cleared by
     * {@link #wakeFromLegacyDormantRecovery()} once recovery hands her back to the normal lifecycle.
     * </p>
     */
    public void markLoadedWithoutLifecycleState() {
        this.loadedWithoutLifecycleState = true;
    }

    public boolean wasLoadedWithoutLifecycleState() {
        return loadedWithoutLifecycleState;
    }

    /** True while this queen is parked as a legacy-dormant queen awaiting recovery. */
    public boolean isLegacyDormant() {
        return legacyDormant;
    }

    /**
     * Marks (or clears) this queen's legacy-dormant state. Persisted so she stays parked across reloads until woken.
     */
    public void setLegacyDormant(boolean dormant) {
        this.legacyDormant = dormant;
    }

    /**
     * Wakes a legacy-dormant queen and hands her back to the normal lifecycle: clears the dormant flag and the
     * loaded-without-state marker, then restarts her LOCATION phase so she resumes founding/holding a hive under the
     * current system. Safe to call on an already-awake queen (the flags simply clear and the phase restarts).
     */
    public void wakeFromLegacyDormantRecovery() {
        this.legacyDormant = false;
        this.loadedWithoutLifecycleState = false;
        lifecyclePhaseManager.restartLocationPhase();
    }

    public QueenBindManager getBindManager() {
        return bindManager;
    }

    /**
     * Whether she is riding her ovipositor (the chained eggsack). Safe to call on the CLIENT: the ovipositor is a
     * passenger of the queen, and passengers are vanilla-synced - unlike the server-only OvipositorManager.
     */
    public boolean isRidingOvipositor() {
        return getPassengers()
            .stream()
            .anyMatch(passenger -> Objects.equals(passenger.getType(), AlienEntityTypes.OVIPOSITOR.get()));
    }

    /**
     * A CAPTIVE breeder is pacified and stays put: an inhibited queen riding her chained eggsack must not shuffle
     * around under idle AI, or her body drifts and rotates against the static eggsack that is anchored to her, leaving
     * her off-centre and contorted. Freeze her movement in that state only. A FOUNDING/reproductive queen (rides an
     * eggsack but is NOT inhibited) is untouched and can still shuffle to lay.
     */
    @Override
    public void travel(@NotNull Vec3 vec3) {
        // [stated] Oct 5: no movement at all on the sack - captive OR founding (it used to freeze only a captive).
        if (isRidingOvipositor()) {
            // \u2b50 Oct 3 - A FISHING ROD MAY MOVE HER ([stated] the rod restriction "is lifted for the chained sack
            // queen
            // only"). The rod hands her velocity; for a few ticks it is left alone instead of being zeroed below.
            // No movement INPUT is ever given - she still does not walk, she is only dragged. CAPTIVE ONLY, as ruled.
            if (isInhibited() && externalPullTicks > 0) {
                externalPullTicks--;
                super.travel(Vec3.ZERO);
                return;
            }
            // Freeze horizontal drift only - keep vertical velocity so gravity still settles her onto the ground
            // if she was caught mid-air or on uneven terrain (a hard Vec3.ZERO would leave her hanging).
            var v = getDeltaMovement();
            setDeltaMovement(0.0, v.y, 0.0);

            // ⭐ Oct 5 perf - SETTLED ON THE SACK, SHE SKIPS THE MOVE. She cannot walk or turn here, yet every tick
            // vanilla still applied gravity and swept her 2.6 x 5.5 box against the blocks under her just to land her
            // where she already was - ~33 us per tick by /blib perf. Once she is on the ground and only gravity is
            // acting, the step is skipped. Support is re-checked every tick (one block read) because onGround is only
            // refreshed by move() - the same lesson as the floating-egg bug - so mining the floor out from under her
            // still drops her.
            // Not in water or lava (their physics must keep running) and nothing but air at her feet, so a fire or
            // other "inside" block placed under her still reaches her through the normal step.
            if (
                onGround()
                    && v.y <= 0.0
                    && v.y > -0.1
                    && !isInWater()
                    && !isInLava()
                    && level().getBlockState(blockPosition()).isAir()
                    && hasSolidSupportBelow()
            ) {
                return;
            }

            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(vec3);
    }

    /** Whether the block under her centre can hold her up (re-checked while the settled travel skip is in use). */
    private boolean hasSolidSupportBelow() {
        var below = blockPosition().below();

        return level().getBlockState(below).entityCanStandOn(level(), below, this);
    }

    /** Whether the inhibitor device is attached (synced + persisted). */
    @Override
    public void die(@NotNull DamageSource damageSource) {
        // She is leaving the world - never leave her incapacitation bar stuck on a player's screen.
        incapacitationManager.onRemoved();
        super.die(damageSource);

        if (level() instanceof ServerLevel serverLevel) {
            TrackedQueenRegistry.markLostAndAnnounce(serverLevel, getUUID(), TrackedQueenRegistry.REASON_DECEASED);
        }
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        // ⭐⭐⭐ FEED HER A BLOCK OF ROYAL JELLY: she founds an empress hive on the spot.
        //
        // [stated] "players keep making empress and trying to get her to found a hiuve and get an eggsack ... these
        // idiots dont have paitence for it." SITUATIONS 1 AND 2 ONLY - she must be in the open, outside any live
        // claim and clear of every hive's spread zone. A queen standing INSIDE a hive is situations 3/4/5
        // (succession and rival empresses), staged separately and deliberately doing nothing here yet.
        //
        // ⚠ THE ITEM IS ONLY CONSUMED IF SHE ACTUALLY FOUNDS. A refused attempt must not eat a block of royal
        // jelly - it is expensive, and silently swallowing it is exactly what gets reported as "the feature is
        // broken" when the real answer is "you were standing in a claim".
        if (
            !isInhibited()
                && !isIncapacitated()
                && stack.is(com.alien.common.registry.init.item.block.AlienBlockItems.ROYAL_JELLY_BLOCK.get())
        ) {
            // !!! THE ARM STILL DID NOT SWING, AND THIS IS WHY. The sidedSuccess(isClientSide()) at the bottom of
            // this branch is correct - but it was UNREACHABLE ON THE CLIENT, because the branch itself used to
            // require 'level() instanceof ServerLevel'. On the client level() is a ClientLevel, so the whole block
            // was skipped and the client fell through to the generic result, never returning SUCCESS. Fixing the
            // return value could never have worked while the guard above it excluded the client.
            //
            // * The client answers SUCCESS immediately and PREDICTS the swing. It cannot know whether she will
            // accept - that decision needs the server's hive state - so on a refusal the arm swings and the red
            // 'She will not take it' message arrives a tick later. Vanilla behaves the same way; a predicted swing
            // followed by a stated reason reads far better than the dead click this replaces.
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(level() instanceof ServerLevel jellyLevel)) {
                return InteractionResult.PASS;
            }
            // \u2b50\u2b50 IN HER OWN HIVE she evolves in place (situations 3 and 6); OUT IN THE OPEN she founds first
            // and then emerges (situations 1 and 2). Whether she ends up sovereign is decided by the lineage, not
            // here - addPretender crowns her if the throne is empty and files her behind the oldest if it is not.
            var evolved = com.alien.common.gameplay.hive.empress.ForcedEmpressFounding
                .evolveInPlace(jellyLevel, this);

            if (!evolved) {
                // ⚠ She is SENT, not founded. The emergence starts when she reaches her anchor and founds - see
                // ForcedEmpressFounding.onFounded. Starting it here would have crowned an empress standing on the
                // surface next to a hive she had not dug yet.
                if (!com.alien.common.gameplay.hive.empress.ForcedEmpressFounding.forceFounding(jellyLevel, this)) {
                    // ⭐⭐⭐ SAY WHY. THIS IS THE FIX FOR "I CANT FEED HER THE JELLY BLOCK".
                    //
                    // ⚠⚠ A BARE InteractionResult.FAIL IS INDISTINGUISHABLE FROM A DEAD CLICK. Both paths can
                    // refuse for six different reasons and the player was shown NONE of them, so a working
                    // feature and a broken one looked identical. That is why this has been reported for days
                    // and guessed at four times without anyone being able to narrow it.
                    var why = com.alien.common.gameplay.hive.empress.ForcedEmpressFounding.lastRefusal();
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component
                            .literal(why.isEmpty() ? "She will not take it." : "She will not take it - " + why)
                            .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                    );
                    return InteractionResult.FAIL;
                }
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            // 🚨🚨🚨 sidedSuccess(false) RETURNS *CONSUME*, AND CONSUME DOES NOT SWING THE ARM.
            //
            // ⚠⚠ REPORTED AS "i right click nothing happens no notice no arm movement" - WHILE THE FEATURE WAS
            // WORKING. Verified in the 1.21.1 bytecode: sidedSuccess returns SUCCESS when passed true and CONSUME
            // when passed false, and InteractionResult.shouldSwing() is true ONLY for SUCCESS and
            // SUCCESS_NO_ITEM_USED. The literal false meant the client never got SUCCESS, so the arm never moved -
            // on a success there is also no refusal message, so a working interaction and a dead click looked
            // EXACTLY the same. That is what sent me chasing hitboxes, eggsacks and item registration for days.
            //
            // ⭐ level().isClientSide() is the standard idiom: SUCCESS on the client so it swings and predicts, CONSUME
            // on the server so the action is not run twice.
            //
            // ⭐ AND SAY SO OUT LOUD. She takes several seconds to change; without a word the player assumes nothing
            // happened and clicks again - which is precisely how one queen collected 37 attempts.
            player.displayClientMessage(
                net.minecraft.network.chat.Component
                    .literal("She swallows the jelly and begins to change...")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                true
            );
            // Server side only from here - the client returned SUCCESS above. CONSUME stops the server running the
            // interaction a second time.
            return InteractionResult.CONSUME;
        }

        // Pry the inhibitor off: sneak + right-click an inhibited queen with a sword or axe. Re-enables her autonomy
        // (her release grace starts once nothing else holds her) and drops the inhibitor so it's recoverable. Costs the
        // tool some durability.
        if (
            isInhibited()
                && player.isShiftKeyDown()
                && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem)
        ) {
            if (level() instanceof ServerLevel serverLevel) {
                setInhibited(false);
                QueenInhibitionService.onReleased(serverLevel, this);
                spawnAtLocation(AlienItems.INHIBITOR.get());
                stack.hurtAndBreak(
                    5,
                    player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND
                );
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    public boolean isInhibited() {
        return hasInhibitor.get();
    }

    /** Attach or remove the inhibitor device. Server-authoritative; syncs and persists automatically. */
    public void setInhibited(boolean inhibited) {
        hasInhibitor.set(inhibited);
    }

    public boolean isTracked() {
        return tracked.get();
    }

    /** Attach or remove the tracker tag. Server-authoritative; syncs and persists automatically. */
    public void setTracked(boolean value) {
        tracked.set(value);
    }

    /**
     * Whether she is in the involuntary, defeat-induced incapacitated state (Part 2). Not yet implemented — the
     * incapacitation state machine (incap HP bar, kill/heal/self-recovery/capture exits) is a deferred feature, so this
     * hook returns {@code false} for now. When that state lands it should read its flag here, and the inhibitor gate
     * below picks it up automatically.
     */
    public boolean isIncapacitated() {
        return incapacitated.get();
    }

    /** Server-authoritative. Set by {@link QueenIncapacitationManager}; syncs and persists automatically. */
    public void setIncapacitated(boolean value) {
        incapacitated.set(value);
    }

    public QueenIncapacitationManager<Queen> getIncapacitationManager() {
        return incapacitationManager;
    }

    /**
     * Whether the inhibitor may be applied to her right now. Per design she must be helpless in one of three ways:
     * incapacitated, in the {@link QueenLifecyclePhase#HIBERNATION} phase, or already secured with all four chains.
     */
    /**
     * PLAYER-PLACED QUEENS ARE EXEMPT ([stated]): "people are clearly trying to fast track and we dont want her digging
     * or building a hive if they want her captured." A queen from a spawn egg, command, bucket or dispenser can be
     * clamped in ANY state - the whole point of spawning one is to keep her, and forcing a fight first just means she
     * founds a hive in the meantime. {@link #isPlayerPlaced()} already exists for the hibernation rule and is exactly
     * the right discriminator: it is persisted, and transitions never call finalizeSpawn, so a hive-promoted daughter
     * can never carry it.
     * <p>
     * Everyone else has to be unable to resist: beaten down, asleep, or fully chained.
     */
    public boolean canBeInhibited() {
        return isPlayerPlaced()
            || isIncapacitated()
            || lifecyclePhaseManager.getPhase() == QueenLifecyclePhase.HIBERNATION
            || bindManager.isFullyBound()
            // STILL GROWING COUNTS AS HELPLESS ([stated]): "if you burst a praetorian then make her molt into a
            // queen you should be able to inhibit her as its a vulnerable phase like the chained and
            // incapacitated/hibernating are." A queen who came up the burst line is never isPlayerPlaced -
            // transitions do not call finalizeSpawn - so without this the ONLY window on her was the hibernation
            // she may skip entirely as a dispatched daughter. hasReachedTargetScale() is false from the molt
            // until her profile tops out at endScale, which is exactly that vulnerable stretch.
            || !getMoltingManager().hasReachedTargetScale();
    }

    /**
     * Whether she is contained — subdued enough to be a captive breeder that grows a chained eggsack. For now this is
     * the four-chain full bind; a human titanium enclosure becomes a second containment source later.
     */
    public boolean isContained() {
        // Synced chain count (mirrors the bind anchors), so this is correct on both server and client
        // — the chained-eggsack renderer reads it off the vehicle queen.
        return bindChainCount.get() >= QueenBindManager.FULLY_BOUND_CHAINS;
    }

    public boolean isDigging() {
        return digging;
    }

    /**
     * Toggles the location-phase spectator dig. While digging she clips through blocks (noPhysics) and ignores gravity
     * so she can travel straight to her committed anchor; noPhysics also suppresses suffocation, and
     * {@link #isInvulnerableTo} adds fire/lava immunity. She stays an ordinary, attackable entity in every other
     * respect. Owned by {@code QueenLifecyclePhaseManager}, which reconciles it every tick.
     */
    /**
     * ⭐⭐⭐ A CHAINED QUEEN HANGS FROM HER ANCHORS INSTEAD OF STANDING ON A FLOOR.
     * <p>
     * [stated] "we want to let it to allow her to hang without a floor ... allow her to float 4 blocks under the
     * anchors y level ... and only if theres 4 anchors above her."
     * </p>
     * <p>
     * ⚠⚠ THE 4-BLOCK GAP IS TO THE TOP OF HER HITBOX, NOT HER FEET - he corrected me on exactly this. She is 5.5 blocks
     * tall, so anchors at Y 40 put her head at 36 and her feet at 30.5, with four empty blocks of chain showing above
     * her. Measuring from her feet would have tucked her head up between the anchors instead.
     * </p>
     * <p>
     * ⚠ THE EGGSACK COMES WITH HER FOR FREE, and that is worth knowing rather than rediscovering: the ovipositor RIDES
     * HER (she is the vehicle, it is the passenger), so a suspended queen carries her sack up with her. If the
     * relationship were the other way round this would need the sack lifted separately or it would sit on the floor
     * dragging her pose down with it.
     * </p>
     * <p>
     * ⚠ RECONCILED EVERY TICK RATHER THAN SET ONCE. Anchors are blocks: a player can break one at any moment, and a
     * one-shot flag would leave her floating over a rig that no longer exists. Recomputing is cheap - it is a walk of
     * at most eight positions - and it means she falls the instant the fourth chain of a row goes.
     * </p>
     * <p>
     * ⚠ DIGGING WINS. {@code setDigging} owns no-gravity for its own reasons and reconciles on transition, so this
     * never touches gravity while she is digging - a suspended queen who somehow began a dig would otherwise have two
     * systems fighting over the same flag.
     * </p>
     */
    private void tickSuspension() {
        if (level().isClientSide || digging) {
            return;
        }

        var anchorY = bindManager.suspensionAnchorY();
        if (anchorY.isEmpty()) {
            if (suspended) {
                suspended = false;
                setNoGravity(false);
            }
            return;
        }

        // Top of her hitbox sits SUSPENSION_GAP below the anchor row; her position is her feet.
        var targetFeetY = anchorY.getAsInt() - SUSPENSION_GAP - getBbHeight();

        if (!suspended) {
            suspended = true;
            setNoGravity(true);
        }

        setDeltaMovement(getDeltaMovement().multiply(1.0, 0.0, 1.0));
        setPos(getX(), targetFeetY, getZ());
    }

    /** [stated] "a gap of 4 full blocks" between the anchor row and the top of her hitbox. */
    private static final int SUSPENSION_GAP = 4;

    /** Whether she is currently hanging. Derived state - see {@link #tickSuspension()} - so it is not persisted. */
    private boolean suspended;

    public void setDigging(boolean digging) {
        // 🚨 THE EARLY-OUT COULD LEAVE THE SYNCED FLAG STUCK ON. `digging` is a TRANSIENT field - its own comment says
        // "not saved - a reload never stays noclip" - but isDiggingSynced is a DataAccessor and DOES persist. After a
        // reload the field reads false while the synced flag is still true, so every later setDigging(false) returned
        // here without ever clearing the flag, and the client kept playing the dig animation forever.
        //
        // ⚠⚠ REPORTED as a queen playing her dig animation while CHAINED AND HANGING FROM ANCHORS. The chain guard in
        // QueenLifecyclePhaseManager.tick was calling setDigging(false) every tick exactly as intended - and it was
        // being thrown away right here.
        //
        // ⭐ Reconcile the synced flag before the early-out, so a desync heals on the first call either way.
        if (isDiggingSynced.get() != digging) {
            isDiggingSynced.set(digging);
        }

        if (this.digging == digging) {
            return;
        }
        this.digging = digging;
        this.noPhysics = digging;
        setNoGravity(digging);
        isDiggingSynced.set(digging);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (digging && source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // ⭐⭐ REMEMBER WHETHER A PLAYER IS DOING THIS. Read when her disturbance threshold is crossed: an ambient mob
        // that wears her down gets a fight and she resettles, a PLAYER who does ends the hibernation for good.
        //
        // ⚠ RECORDED HERE RATHER THAN INFERRED LATER, because the disturbance bar is fed by HEALTH SAMPLING - it
        // watches her bar fall and has no idea what took it off. By the time the threshold is crossed the damage
        // source is long gone, so the only place that knows is the moment of the hit.
        //
        // ⚠ Creative and spectator players do not count, exactly as they do not for aggro or claiming - an admin
        // inspecting a sleeping queen must not evict her.
        if (
            !level().isClientSide
                && damageSource.getEntity() instanceof net.minecraft.world.entity.player.Player attacker
                && !com.alien.common.util.AlienPredicates.isIgnoredByHive(attacker)
        ) {
            disturbedByPlayer = true;
        }

        // While DOWN, damage eats the incapacitation bar instead of her health - that bar IS the finisher. She
        // only truly dies when it is drained to zero.
        if (!level().isClientSide && isIncapacitated()) {
            // ONLY players and rival xenomorphs can work the finisher bar. The bar opens at 1, and in the nether a
            // downed queen is instantly mobbed by piglins - ambient mobs drained it the same tick she fell, so the
            // downed state was over before anyone saw it ([stated] "nether queens dont get incapacitated" - they
            // did, for a frame). Kin mercy already shields her from her own strain; vanilla wildlife chewing on a
            // downed queen wounds her pride, not the bar. Execution stays with players and rival strains.
            // \u2b50\u2b50\u2b50 SOURCELESS DAMAGE ALWAYS FINISHES HER. /kill, /damage, the void and anything a plugin
            // deals carry NO attacker entity, and the old test read that as "not allowed to work the bar" - so a
            // downed queen shrugged off /kill itself. An admin with a stuck queen had no way to remove her at all.
            //
            // \u26a0 THIS CANNOT WEAKEN THE GUARD IT SITS IN. That guard exists to stop AMBIENT MOBS draining the bar
            // (nether piglins mobbed a downed queen and ended the state before anyone saw it), and an ambient mob
            // always HAS an entity. "No entity at all" is never a piglin; it is an operator or the world itself.
            //
            // \u26a0 getDirectEntity() is checked too, so a projectile counts as its shooter. avp_human's hitscan
            // already attributes bullets to the shooter via getEntity(), but an arrow, a thrown item or any future
            // projectile weapon would otherwise present the PROJECTILE as the attacker and be shrugged off.
            var downedAttacker = damageSource.getEntity();
            var downedDirect = damageSource.getDirectEntity();
            // ⭐⭐⭐ EVERY BLOW LANDS NOW. THE FILTER IS GONE, ON PURPOSE.
            //
            // [stated] "revoke the block that stops mobs from killing the incapacitated queen i dont care anymore if
            // piglins cant kill her or better yet big brain moment make is so while shes down they just dont attack
            // her. have them consider her already dead."
            //
            // ⚠⚠ THE FILTER WAS THE WRONG SHAPE AND IT MADE HER INVINCIBLE. Deciding WHO may hurt her meant every
            // source we failed to anticipate was silently absorbed - and combined with her healing back up from 1 HP
            // (fixed separately in Alien.canHeal), the result was a royal nothing could finish. Refusing damage is a
            // guess about intent; refusing to TARGET her is a statement of it, and that now lives in
            // MixinMob_IgnoreDownedRoyal where ambient mobs simply do not see her.
            //
            // ⭐ So the bar is now purely the player's finisher: anything that reaches her works it, and nothing
            // reaches her by accident because nothing hostile is aiming at her in the first place.

            // \u2b50 An operator kill does not grind the bar down, it ENDS her. Working the finisher is something a
            // fight earns; a command should not have to be run five times.
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

        // A blow that WOULD kill her puts her down instead - unless her strain cannot be incapacitated, or she
        // has been worn down too many times inside the window, in which case it is a real death.
        if (!level().isClientSide && amount >= getHealth() && incapacitationManager.onLethalDamage()) {
            return true;
        }

        var wasHurt = super.hurt(damageSource, amount);
        if (wasHurt && !level().isClientSide) {
            // A hit only pulls her off her duty once she is enough HEALTH down - and her regeneration repays that
            // debt, so it has to come off faster than she heals. See QueenLifecyclePhaseManager.registerDisturbance.
            // Called AFTER super.hurt so her health already reflects this blow.
            //
            // SHE STILL TOOK THE DAMAGE. This is about her ATTENTION, not her health: vanilla's hurt marks the
            // attacker as her last-hurt-by, and her sensors turn that into a target, and a queen with a target stops
            // tending her eggsack. So when the disturbance does not clear the bar, the retaliation is cleared with it
            // - otherwise a syringe (0.01 health) or a stray splash ends her egg-laying as surely as an axe.
            if (getLifecyclePhaseManager().registerDisturbance()) {
                // Roused for real: she leaves the eggsack the same way the empress does - DESTRUCTIVELY. The
                // ovipositor cannot exist off a royal (it self-discards the next tick without a living vehicle), so
                // there is no dismounting it and no sitting back down. She grows a fresh one later through the normal
                // creation path once she is calm and the cooldown allows, which is the real cost of getting her up.
                ovipositorManager.abandonOvipositor();
            } else if (isOnDuty()) {
                setLastHurtByMob(null);
                setTarget(null);
            }
        }
        return wasHurt;
    }

    /**
     * Whether she is doing something a light knock should not interrupt: riding her eggsack, or asleep.
     * <p>
     * A queen who is already up and walking about retaliates normally - the whole point is protecting the states where
     * standing up COSTS her something.
     */
    private boolean isOnDuty() {
        return isRidingOvipositor() || Boolean.TRUE.equals(isHibernating.get());
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public boolean isEggLayCooldownReady() {
        return queenData.isEggLayCooldownReady();
    }

    @Override
    public void resetEggLayCooldown() {
        queenData.resetEggLayCooldown();
    }

    @Override
    public boolean hasOvipositor() {
        return ovipositorManager.hasOvipositor();
    }

    @Override
    public Vec3 getEggLayingPosition() {
        return ovipositorManager.getEggLayingPosition();
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        // A pre-lifecycle-system save carries neither the lifecycle-phase tag nor the legacy-dormant marker. Detect
        // that
        // BEFORE loading the managers so LegacyHiveRecovery can migrate her.
        this.loadedWithoutLifecycleState =
            !compoundTag.contains(LIFECYCLE_PHASE_TAG)
                && !compoundTag.contains(LEGACY_DORMANT_TAG)
                && !compoundTag.contains(LEGACY_DORMANT_COMPAT_TAG);
        this.legacyDormant = compoundTag.getBoolean(LEGACY_DORMANT_TAG)
            || compoundTag.getBoolean(LEGACY_DORMANT_COMPAT_TAG);
        this.playerPlaced = compoundTag.getBoolean("PlayerPlaced");
        this.captiveGraceUntil = compoundTag.getLong("CaptiveGraceUntil");
        this.captiveGraceIgnoresSlab = compoundTag.getBoolean("CaptiveGraceIgnoresSlab");
        this.wasCaptive = compoundTag.getBoolean("WasCaptive");
        this.pendingDaughterLocationId = compoundTag.contains("PendingDaughterLocation")
            ? compoundTag.getString("PendingDaughterLocation")
            : null;
        ovipositorManager.load(compoundTag);
        queenData.load(compoundTag);
        lifecyclePhaseManager.load(compoundTag);
        bindManager.load(compoundTag);
        rescueManager.load(compoundTag);
        bindManager.onLoaded(); // drop any chain whose anchor was broken while she was unloaded (phantom bind)
        incapacitationManager.load(compoundTag);
        incapacitationManager.onLoaded(); // a downed queen must not come back with her AI switched on
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putBoolean("PlayerPlaced", playerPlaced);
        compoundTag.putLong("CaptiveGraceUntil", captiveGraceUntil);
        compoundTag.putBoolean("CaptiveGraceIgnoresSlab", captiveGraceIgnoresSlab);
        compoundTag.putBoolean("WasCaptive", wasCaptive);
        if (pendingDaughterLocationId != null) {
            compoundTag.putString("PendingDaughterLocation", pendingDaughterLocationId);
        }
        compoundTag.putBoolean(LEGACY_DORMANT_TAG, legacyDormant);
        ovipositorManager.save(compoundTag);
        queenData.save(compoundTag);
        lifecyclePhaseManager.save(compoundTag);
        bindManager.save(compoundTag);
        rescueManager.save(compoundTag);
        incapacitationManager.save(compoundTag);
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.QUEEN.get();
            case NETHER -> AlienEntityTypes.NETHER_QUEEN.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_QUEEN.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_QUEEN.get();
        };
    }

    /**
     * Whether a PLAYER has hurt her since she last settled. See the note in {@link #hurt}.
     * <p>
     * ⚠ Not persisted: it only matters between a hit and the threshold being crossed, which is seconds. Surviving a
     * reload would mean a queen attacked days ago being evicted by a skeleton finishing the job.
     * </p>
     */
    private boolean disturbedByPlayer;

    public boolean wasDisturbedByPlayer() {
        return disturbedByPlayer;
    }

    public void clearDisturbedByPlayer() {
        disturbedByPlayer = false;
    }

    /**
     * ⚠ Bridges the shared incapacitation manager to HER ovipositor manager. The two royals hold different manager
     * types, which is the only reason this indirection exists.
     */
    @Override
    public void abandonOvipositorForIncapacitation() {
        getOvipositorManager().abandonOvipositor();
    }

    @Override
    public boolean isStandDigging() {
        return standDiggingSynced.get();
    }

    @Override
    public void setStandDigging(boolean value) {
        standDiggingSynced.set(value);
    }

    /**
     * \u26a0 A restrained queen does not carve. Oct 3: ANY chain stops her, not just the full four - [stated] a captive
     * "cant start a hive, create territory, try to carve anything" - and so does her release grace while she is outside
     * every hive's slab.
     */
    @Override
    public boolean canCarve() {
        return !com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.blocksFrontEnd(this);
    }

    @Override
    public void spawnFoundingCrewForCarve() {
        spawnFoundingCrew();
    }

    @Override
    public net.minecraft.world.entity.Mob asMob() {
        return this;
    }
}
