package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ScaledDamage;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.ai.HarbingerGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class Harbinger extends Xenomorph implements GOAPUser<Harbinger>, com.alien.common.gameplay.entity.CrawlPostureTransitionListener {

    @Override
    public int crawlPostureTransitionTicks(boolean enteringCrawl) {
        return enteringCrawl ? HarbingerAnimationRefs.CRAWL_DOWN_TICKS : HarbingerAnimationRefs.CRAWL_UP_TICKS;
    }

    private static final int FRENZY_AURA_INTERVAL_TICKS = 20 * 30;

    private static final int FRENZY_DURATION_TICKS = 20 * 30;

    private static final double FRENZY_AURA_RADIUS_BLOCKS = 32.0;

    public static final AttackType CLAW = AttackType.builder("harbinger_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("harbinger_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("harbinger_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * Ground game ([stated] Aug 1). The bite reuses the model's only bite clip (already ground-authored as
     * {@code attack.crawlbite}); the whipstabs are per-arm - each requires ITS OWN whip via the sided arm requirements,
     * so a harbinger that loses the left whip keeps stabbing with the right, and losing both silences the stabs while
     * the bite fights on. All three are {@code crawlAttack()}: the posture gate keeps them on the ground and the
     * config's crawl preference makes them her whole moveset while down there.
     */
    /**
     * ⭐⭐ THE SWIM CLAWS - BOTH ARMS, and the same two-part rule the ravager and empress use. [stated] "uses the usually
     * two limb if one is lost 50% damage if both lost only bite works."
     * <p>
     * ⚠ THE RULE NEEDS BOTH HALVES IN DIFFERENT PLACES and neither alone expresses it: {@code requiresAnyArm()} is the
     * "both lost, does not play" half, and the 50% is priced into the damage. {@code requiresBothArms()} would collapse
     * them into one and the half-damage case could never happen - the exact bug found in the ravager.
     * </p>
     */
    private static final float SWIM_CLAWS_MISSING_ARM_PENALTY = 0.5F;

    public static final AttackType SWIM_CLAWS = AttackType.builder("harbinger_swim_claws")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(Harbinger::applySwimClawsDamage)
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

    /** ⚠ HEAD-only, so it is what a fully disarmed harbinger still has in the water. */
    public static final AttackType SWIM_BITE = AttackType.builder("harbinger_swim_bite")
        .requiresHead()
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType CRAWL_BITE = AttackType.builder("harbinger_crawl_bite")
        .crawlAttack()
        .requiresHead()
        .defaultDurationInTicks(HarbingerAnimationRefs.CRAWL_BITE_DURATION_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType CRAWL_WHIPSTAB_LEFT = AttackType.builder("harbinger_crawl_left_whipstab")
        .crawlAttack()
        .requiresLeftArm()
        .defaultDurationInTicks(HarbingerAnimationRefs.CRAWL_WHIPSTAB_DURATION_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType CRAWL_WHIPSTAB_RIGHT = AttackType.builder("harbinger_crawl_right_whipstab")
        .crawlAttack()
        .requiresRightArm()
        .defaultDurationInTicks(HarbingerAnimationRefs.CRAWL_WHIPSTAB_DURATION_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.HUGE, Harbinger::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(SWIM_CLAWS)
                .addRegular(SWIM_BITE)
                .addRegular(TAIL)
                .addRegular(CRAWL_BITE)
                .addRegular(CRAWL_WHIPSTAB_LEFT)
                .addRegular(CRAWL_WHIPSTAB_RIGHT)
                .addRegular(HarbingerBackhandAttack.ATTACK)
                .addTriggered(HarbingerGroundSlamAttack.ATTACK)
                .addTriggered(HarbingerKickAttack.ATTACK)
                .addTriggered(HarbingerFrontKickAttack.ATTACK)
                .build()
        )
        .parallelDigCount(2)
        .pushedByFluid(false)
        .build();

    private final HarbingerAnimationDispatcher animationDispatcher;

    public Harbinger(EntityType<? extends Harbinger> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new HarbingerAnimationDispatcher(this);
    }

    public static AttributeSupplier.Builder createHarbingerAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 18.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 20.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 1.25F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 15F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    @Override
    public void tick() {
        super.tick();
        applyFrenzyAura();
    }

    private void applyFrenzyAura() {
        if (level().isClientSide || tickCount % FRENZY_AURA_INTERVAL_TICKS != 0) {
            return;
        }

        var center = position();
        var auraBounds = new AABB(
            center.x - FRENZY_AURA_RADIUS_BLOCKS,
            center.y - FRENZY_AURA_RADIUS_BLOCKS,
            center.z - FRENZY_AURA_RADIUS_BLOCKS,
            center.x + FRENZY_AURA_RADIUS_BLOCKS,
            center.y + FRENZY_AURA_RADIUS_BLOCKS,
            center.z + FRENZY_AURA_RADIUS_BLOCKS
        );
        var nearbyXenomorphs = level().getEntitiesOfClass(
            Xenomorph.class,
            auraBounds,
            xenomorph -> xenomorph != this
                && xenomorph.isAlive()
                && AlienPredicates.areAliensSameHive(this, xenomorph)
        );

        for (var xenomorph : nearbyXenomorphs) {
            xenomorph.addEffect(
                new MobEffectInstance(
                    AlienMobEffects.getFrenzyHolder(),
                    FRENZY_DURATION_TICKS,
                    0,
                    true,
                    false,
                    true
                )
            );
        }
    }

    @Override
    public Agent.Builder<Harbinger> blib$applyGOAPAgentProperties(Agent.Builder<Harbinger> agentBuilder) {
        return HarbingerGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Harbinger> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(HarbingerGOAP.GRAPH);
    }

    public HarbingerAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.HARBINGER.get();
            case NETHER -> AlienEntityTypes.NETHER_HARBINGER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_HARBINGER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_HARBINGER.get();
        };
    }
}
