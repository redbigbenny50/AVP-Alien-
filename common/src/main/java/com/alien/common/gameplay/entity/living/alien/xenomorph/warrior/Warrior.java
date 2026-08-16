package com.alien.common.gameplay.entity.living.alien.xenomorph.warrior;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.warrior.ai.WarriorGOAP;
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

public class Warrior extends Xenomorph implements GOAPUser<Warrior> {

    /**
     * ⭐⭐ THE PRONE ATTACKS. [stated] "when either leg is shot off it has to crawl there should be no other
     * alternatives... the attacks they can do are only the crawl ones."
     * <p>
     * ⚠⚠ THE CLIPS AND THE DISPATCHER METHODS ALREADY EXISTED - what was missing was the ATTACK TYPES, so the crawl
     * preference in {@code XenomorphAttackConfig} had nothing to restrict to and fell through to the standing set. A
     * one-legged warrior stood up to swing because there was literally nothing prone to pick.
     * </p>
     */
    /** ⚠ Slightly softer than a standing swing, matching the predalien's existing crawl claw. */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "warrior_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        16
    );

    /** ⚠ HEAD, NOT ARM - so a crawling warrior that has also lost both arms still has a bite. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "warrior_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        14
    );

    public static final AttackType CLAW = AttackType.builder("warrior_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("warrior_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("warrior_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.MEDIUM_DOOR, Warrior::getType)
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

    private final WarriorAnimationDispatcher animationDispatcher;

    public Warrior(EntityType<? extends Warrior> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new WarriorAnimationDispatcher(this);
    }

    public static AttributeSupplier.Builder createWarriorAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 8.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 4.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.4F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 3F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.1F);
    }

    @Override
    public Agent.Builder<Warrior> blib$applyGOAPAgentProperties(Agent.Builder<Warrior> agentBuilder) {
        return WarriorGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Warrior> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(WarriorGOAP.GRAPH);
    }

    @Override
    public void runDigAnimation() {
        playAttackSound();
        attackType.set(CLAW);
        beginAttack(CLAW.defaultDurationInTicks());
    }

    public WarriorAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.WARRIOR.get();
            case NETHER -> AlienEntityTypes.NETHER_WARRIOR.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_WARRIOR.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_WARRIOR.get();
        };
    }
}
