package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
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
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("predalien_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("predalien_tail")
        .requiresTail()
        .defaultDurationInTicks(12)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.LARGE, Predalien::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(TAIL)
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
            .add(Attributes.ARMOR_TOUGHNESS, 12.0F)
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
