package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.SpitterGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
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
                .addRegular(TAIL)
                .build()
        )
        .build();

    private final SpitterAnimationDispatcher animationDispatcher;

    private final SpitterData spitterData;

    public Spitter(EntityType<? extends Spitter> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new SpitterAnimationDispatcher(this);
        this.spitterData = new SpitterData();
    }

    public static AttributeSupplier.Builder createSpitterAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 12.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
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
            case IRRADIATED -> null;
        };
    }
}
