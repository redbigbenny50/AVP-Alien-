package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.BackhandAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.ai.PredalienGOAP;
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

public class Predalien extends Xenomorph implements GOAPUser<Predalien> {

    public static final AttackType CLAW = AttackType.builder("predalien_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("predalien_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("predalien_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⭐ [stated] "the predalien is supposed to be stronger... a predaliens is medium damage with a 6-7 block knock
     * back".
     * <p>
     * **THE DIALS.** 0.8 of its own base 15 = ~12 - a HARDER absolute hit than the praetorian's ~5.4 twice over, which
     * is the "stronger" he asked for, while still reading as medium against its own 15-damage claw. 0.7 blocks/tick
     * lands around 6-7 blocks.
     * </p>
     */
    private static final float BACKHAND_DAMAGE_FRACTION = 0.8F;

    private static final double BACKHAND_KNOCKBACK_STRENGTH = 0.7;

    public static final AttackType BACKHAND = BackhandAttack.create(
        "predalien_backhand",
        BACKHAND_DAMAGE_FRACTION,
        BACKHAND_KNOCKBACK_STRENGTH,
        20
    );

    /**
     * ⭐ THE CRAWL SET. [stated] "the same as arm/claw attacks but maybe a bit weaker because of the posture." 80%
     * damage, same mirrored art, same limb requirement.
     * <p>
     * ⭐ IT NOW HAS A CRAWL BITE - his re-export added `crawl.attack.bite`, so a prone bite plays its own clip but it is
     * also load-bearing: lose both arms while crawling and it has NOTHING, because the config's crawl preference will
     * not fall back to the standing set.
     * </p>
     */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "predalien_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        18
    );

    /** ⚠ HEAD, NOT ARM - so a crawling predalien that has also lost both arms still has a bite. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "predalien_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        14
    );

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.LARGE, Predalien::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(TAIL)
                .addRegular(BACKHAND)
                .addRegular(CRAWL_CLAW)
                .addRegular(CRAWL_BITE)
                .build()
        )
        .parallelDigCount(2)
        .build();

    private final PredalienAnimationDispatcher animationDispatcher;

    public Predalien(EntityType<? extends Predalien> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new PredalienAnimationDispatcher(this);
    }

    public static AttributeSupplier.Builder createPredalienAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 18.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.75F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 10F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    @Override
    public Agent.Builder<Predalien> blib$applyGOAPAgentProperties(Agent.Builder<Predalien> agentBuilder) {
        return PredalienGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Predalien> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(PredalienGOAP.GRAPH);
    }

    public PredalienAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.PREDALIEN.get();
            case NETHER -> AlienEntityTypes.NETHER_PREDALIEN.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_PREDALIEN.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_PREDALIEN.get();
        };
    }
}
