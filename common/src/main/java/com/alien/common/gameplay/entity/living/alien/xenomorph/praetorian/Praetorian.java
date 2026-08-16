package com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.BackhandAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.ai.PraetorianGOAP;
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

public class Praetorian extends Xenomorph implements GOAPUser<Praetorian> {

    public static final AttackType CLAW = AttackType.builder("praetorian_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(16)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("praetorian_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("praetorian_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⭐ [stated] "a praetorians back hand is light-medium damage with a 4-5 block knock back".
     * <p>
     * **THE DIALS.** 0.6 of its own base 9 damage = ~5.4, clearly under its 9-damage claw; 0.5 blocks/tick lands around
     * 4-5 blocks with the shared vertical lift. Both are meant to be tuned from playtest.
     * </p>
     */
    private static final float BACKHAND_DAMAGE_FRACTION = 0.6F;

    private static final double BACKHAND_KNOCKBACK_STRENGTH = 0.5;

    public static final AttackType BACKHAND = BackhandAttack.create(
        "praetorian_backhand",
        BACKHAND_DAMAGE_FRACTION,
        BACKHAND_KNOCKBACK_STRENGTH,
        20
    );

    /**
     * ⭐ THE CRAWL SET. [stated] "they should be the same as arm/claw attacks but maybe a bit weaker because of the
     * posture." Same limb requirements and the same mirrored art as standing, at 80% damage.
     * <p>
     * `.crawlAttack()` is what makes these usable while crawling AND, via the config's crawl preference, what makes
     * them the ONLY picks once the caste is on the ground. ⚠ That preference is DERIVED, not a flag - the moment a
     * usable crawl attack exists in the regular list the standing set stops being offered while crawling. Which is
     * exactly why these two had to ship together with nothing in between.
     * </p>
     */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "praetorian_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        18
    );

    /** ⚠ PRAETORIAN-ONLY: the predalien has no `crawl.attack.bite` clip. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "praetorian_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        12
    );

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.LARGE_DOOR, Praetorian::getType)
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
        .pushedByFluid(false)
        .build();

    private final PraetorianAnimationDispatcher animationDispatcher;

    public Praetorian(EntityType<? extends Praetorian> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new PraetorianAnimationDispatcher(this);
    }

    public static AttributeSupplier.Builder createPraetorianAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 14.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.45F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    @Override
    public Agent.Builder<Praetorian> blib$applyGOAPAgentProperties(Agent.Builder<Praetorian> agentBuilder) {
        return PraetorianGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Praetorian> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(PraetorianGOAP.GRAPH);
    }

    public PraetorianAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.PRAETORIAN.get();
            case NETHER -> AlienEntityTypes.NETHER_PRAETORIAN.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_PRAETORIAN.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_PRAETORIAN.get();
        };
    }
}
