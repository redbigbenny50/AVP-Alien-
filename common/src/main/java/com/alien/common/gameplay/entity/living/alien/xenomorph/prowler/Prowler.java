package com.alien.common.gameplay.entity.living.alien.xenomorph.prowler;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
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
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
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
