package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.SpitterGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Spitter extends Xenomorph implements GOAPUser<Spitter> {

    /**
     * ⭐⭐ THE PRONE ATTACKS. [stated] "the spitter loses one leg and still stands to attack with arms and tail attack it
     * should only resort to crawl attacks."
     * <p>
     * ⚠⚠ THE CLIPS AND THE DISPATCHER METHODS ALREADY EXISTED - what was missing was the ATTACK TYPES. The crawl
     * preference in {@code XenomorphAttackConfig} restricts a crawling caste to crawl attacks ONLY IF it has any; with
     * none registered there was nothing to restrict to and it fell straight through to the standing set. A one-legged
     * spitter stood up to swing because there was literally nothing prone to pick.
     * </p>
     */
    /** ⚠ Slightly softer than a standing swing, matching the predalien's existing crawl claw. */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "spitter_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        16
    );

    /** ⚠ HEAD, NOT ARM - so a crawling spitter that has also lost both arms still has a bite. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "spitter_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        14
    );

    public static final AttackType CLAW = AttackType.builder("spitter_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("spitter_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("spitter_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType SPIT = AttackType.builder("spitter_spit")
        .requiresHead()
        .defaultDurationInTicks(20)
        .damageApplicator((xenomorph, target) -> {})
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.MEDIUM_TALL, Spitter::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(CRAWL_CLAW)
                .addRegular(CRAWL_BITE)
                .addRegular(TAIL)
                .build()
        )
        .build();

    private final SpitterAnimationDispatcher animationDispatcher;

    private final SpitterData spitterData;

    /**
     * ⭐⭐ TRUE WHILE THE SPITTER IS DOWN ON ALL FOURS. Read by SpitterAnimator to choose the idle and the attack set,
     * and by StandardXenomorphLimbHitboxes to replay the matching clip server-side. See {@link #updateQuadPosture()}.
     */
    public final DataAccessor<Boolean> isQuadPosture;

    public Spitter(EntityType<? extends Spitter> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new SpitterAnimationDispatcher(this);
        this.spitterData = new SpitterData();
        this.isQuadPosture = new DataAccessor<>(this, AlienDataSyncKeys.SPITTER_IS_QUAD_POSTURE.get());
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            updateQuadPosture();
        }
    }

    /**
     * ⭐⭐ THE POSTURE STATE MACHINE. [stated] "if its walking and attacks an enemy close to it then it would use the
     * normal arm attacks bite etc. if its running at an enemy or closeing the distance it would use run and the quad
     * attacks. if its spitting and an enemy attacks it or gets close then it would use the normal attacks."
     * <p>
     * It is a LATCH, not a per-tick derivation, and that is the whole point. Running and lunging DROP it onto all
     * fours; walking and spitting STAND IT UP; standing still HOLDS whatever it was. Without the hold, a spitter that
     * charged in would rear up the instant it stopped moving and every swing after the arrival swing would be biped -
     * which is exactly what the old art forced, because there was no quad idle to stand in.
     * </p>
     * <p>
     * ⚠ FROZEN WHILE AN ATTACK IS RUNNING. {@code StandardXenomorphLimbHitboxes.animationFor} asks which clip is
     * playing EVERY TICK of a swing so it can place the limb hitboxes; the dispatcher asks ONCE at the start. If the
     * posture could move mid-swing the two would disagree and the hitboxes would sit in a different pose from the body.
     * This is the same trap {@code MirroredAttackSide.alternationSeed} had to solve for left-vs-right, so the answer is
     * the same shape: hold the value still for the duration of the swing.
     * </p>
     */
    private void updateQuadPosture() {
        // Frozen mid-swing, and the swing itself was started from whatever posture was current.
        if (isAttacking()) {
            return;
        }

        // Crawling and swimming are postures of their own with their own clip sets, and the airborne pair is biped.
        // None of them can be stood in on all fours, so all of them stand the spitter back up.
        if (getCrawlingManager().isCrawling() || isUnderWater() || !onGround()) {
            isQuadPosture.set(false);
            return;
        }

        // Running or pouncing puts it down. Both are QUAD clips, so the posture already matches what is on screen.
        if (isMovingQuickly.get() || isLunging.get()) {
            isQuadPosture.set(true);
            return;
        }

        // Walking stands it up - there is no quad walk clip, so a walking spitter is upright by definition.
        if (isMovingHorizontally.get()) {
            isQuadPosture.set(false);
            return;
        }

        // Standing still: HOLD. quad.idle exists precisely so this branch does not have to force a posture.
    }

    /**
     * Stands the spitter up for its signature attack.
     * <p>
     * [stated] the spit is a BIPED clip, and there is no quad version of it. {@code startAttack} sets {@code
     * attackType} before calling this, so by the time we are here the type of the swing about to play is known - and
     * because {@link #updateQuadPosture()} freezes on {@code isAttacking()}, whatever is set here is what the whole
     * swing runs in, on both sides.
     * </p>
     */
    @Override
    public void beginAttack(int durationInTicks) {
        if (!level().isClientSide && attackType.get() == SPIT) {
            isQuadPosture.set(false);
        }

        super.beginAttack(durationInTicks);
    }

    public static AttributeSupplier.Builder createSpitterAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 12.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 6.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.4F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 2F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.1F);
    }

    @Override
    public Agent.Builder<Spitter> blib$applyGOAPAgentProperties(Agent.Builder<Spitter> agentBuilder) {
        return SpitterGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Spitter> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(SpitterGOAP.GRAPH);
    }

    public SpitterData getSpitterData() {
        return spitterData;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        spitterData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        spitterData.save(compoundTag);
    }

    public SpitterAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.SPITTER.get();
            case NETHER -> AlienEntityTypes.NETHER_SPITTER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_SPITTER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_SPITTER.get();
        };
    }
}
