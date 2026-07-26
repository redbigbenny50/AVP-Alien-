package com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.ai.RazorClawGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RazorClaw extends Xenomorph implements GOAPUser<RazorClaw> {

    public static final AttackType CLAW = AttackType.builder("razor_claw_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("razor_claw_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("razor_claw_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType SWIM_ATTACK = AttackType.builder("razor_claw_swim_attack")
        .requiresTail()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.LARGE, RazorClaw::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(TAIL)
                .addTriggered(RazorClawSweepAttack.ATTACK)
                .build()
        )
        .parallelDigCount(2)
        .pushedByFluid(false)
        .build();

    private static final int BLOOD_LOSS_DURATION_IN_TICKS = 20 * 15;

    public static AttributeSupplier.Builder createRazorClawAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 10.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 12.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.6F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 6F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    private final RazorClawAnimationDispatcher animationDispatcher;

    public RazorClaw(EntityType<? extends RazorClaw> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new RazorClawAnimationDispatcher(this);
    }

    @Override
    public Agent.Builder<RazorClaw> blib$applyGOAPAgentProperties(Agent.Builder<RazorClaw> agentBuilder) {
        return RazorClawGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<RazorClaw> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(RazorClawGOAP.GRAPH);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity entity) {
        var result = super.doHurtTarget(entity);

        if (result && entity instanceof LivingEntity livingEntity) {
            livingEntity.addEffect(
                new MobEffectInstance(
                    AlienMobEffects.getBloodLossHolder(),
                    BLOOD_LOSS_DURATION_IN_TICKS,
                    0
                )
            );
        }

        return result;
    }

    public RazorClawAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.RAZOR_CLAW.get();
            case NETHER -> AlienEntityTypes.NETHER_RAZOR_CLAW.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_RAZOR_CLAW.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_RAZOR_CLAW.get();
        };
    }
}
