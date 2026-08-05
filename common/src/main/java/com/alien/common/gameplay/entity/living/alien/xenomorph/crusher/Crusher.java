package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.ai.CrusherGOAP;
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

public class Crusher extends Xenomorph implements GOAPUser<Crusher> {

    public static final AttackType BITE = AttackType.builder("crusher_bite")
        .requiresHead()
        .defaultDurationInTicks(12)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("crusher_tail")
        .requiresTail()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static AttributeSupplier.Builder createCrusherAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 12.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 12.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.75F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    private final CrusherAnimationDispatcher animationDispatcher;

    public Crusher(EntityType<? extends Crusher> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.WIDE, Crusher::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(BITE)
                        .addRegular(TAIL)
                        .addTriggered(CrusherChargeAttack.ATTACK)
                        .build()
                )
                .parallelDigCount(2)
                .build()
        );
        this.animationDispatcher = new CrusherAnimationDispatcher(this);
    }

    @Override
    public Agent.Builder<Crusher> blib$applyGOAPAgentProperties(Agent.Builder<Crusher> agentBuilder) {
        return CrusherGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Crusher> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(CrusherGOAP.GRAPH);
    }

    public CrusherAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.CRUSHER.get();
            case NETHER -> AlienEntityTypes.NETHER_CRUSHER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_CRUSHER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_CRUSHER.get();
        };
    }
}
