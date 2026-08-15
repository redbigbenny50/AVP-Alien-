package com.alien.common.gameplay.entity.living.alien.xenomorph.prowler;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.ai.ProwlerGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class Prowler extends Xenomorph implements GOAPUser<Prowler> {

    /**
     * ⭐⭐ THE PRONE ATTACKS. [stated] "when either leg is shot off it has to crawl there should be no other
     * alternatives... the attacks they can do are only the crawl ones."
     * <p>
     * ⚠⚠ THE CLIPS AND THE DISPATCHER METHODS ALREADY EXISTED - what was missing was the ATTACK TYPES, so the crawl
     * preference in {@code XenomorphAttackConfig} had nothing to restrict to and fell through to the standing set. A
     * one-legged prowler stood up to swing because there was literally nothing prone to pick.
     * </p>
     */
    /** ⚠ Slightly softer than a standing swing, matching the predalien's existing crawl claw. */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "prowler_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        16
    );

    /** ⚠ HEAD, NOT ARM - so a crawling prowler that has also lost both arms still has a bite. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "prowler_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        14
    );

    public static final AttackType CLAW = AttackType.builder("prowler_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("prowler_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL_QUAD = AttackType.builder("prowler_tail_quad")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.SMALL_DOOR, Prowler::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(CRAWL_CLAW)
                .addRegular(CRAWL_BITE)
                .addRegular(TAIL_QUAD)
                .build()
        )
        .build();

    private final ProwlerAnimationDispatcher animationDispatcher;

    public Prowler(EntityType<? extends Prowler> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new ProwlerAnimationDispatcher(this);
    }

    public static AttributeSupplier.Builder createProwlerAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 8.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 4.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.4F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 3F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    @Override
    public Agent.Builder<Prowler> blib$applyGOAPAgentProperties(Agent.Builder<Prowler> agentBuilder) {
        return ProwlerGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Prowler> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(ProwlerGOAP.GRAPH);
    }

    public ProwlerAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.PROWLER.get();
            case NETHER -> AlienEntityTypes.NETHER_PROWLER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_PROWLER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_PROWLER.get();
        };
    }
}
