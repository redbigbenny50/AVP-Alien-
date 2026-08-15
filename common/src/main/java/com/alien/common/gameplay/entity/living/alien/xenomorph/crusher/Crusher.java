package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.HeadbuttAttack;
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
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("crusher_tail")
        .requiresTail()
        .defaultDurationInTicks(17)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⭐ THE HEADBUTT. [stated] "the headbutt attack is meant to be a high knockback attack. it does medium damage and
     * it throws the player back and up."
     * <p>
     * It is the only attack in the mod that lifts as well as shoves. `HeadbuttAttack` reuses the backhand's scaled
     * damage and away-vector maths but with a much larger vertical component - the throw is the point, the damage is
     * secondary. 0.7 of the crusher's own 10 damage reads as medium against its own bite.
     * </p>
     */
    private static final float HEADBUTT_DAMAGE_FRACTION = 0.7F;

    private static final double HEADBUTT_KNOCKBACK_STRENGTH = 0.85;

    private static final double HEADBUTT_VERTICAL_BOOST = 0.62;

    public static final AttackType HEADBUTT = HeadbuttAttack.create(
        "crusher_headbutt",
        HEADBUTT_DAMAGE_FRACTION,
        HEADBUTT_KNOCKBACK_STRENGTH,
        HEADBUTT_VERTICAL_BOOST,
        20
    );

    public static AttributeSupplier.Builder createCrusherAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 14.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.5F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 7F)
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
                        .addRegular(HEADBUTT)
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
