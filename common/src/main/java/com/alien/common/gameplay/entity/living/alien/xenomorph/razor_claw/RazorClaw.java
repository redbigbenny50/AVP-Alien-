package com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.FlurryAttackExecutor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ScaledDamage;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.ai.RazorClawGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

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

    // ─── THE DODGE KIT ────────────────────────────────────────────────────────────────────────────────────────────
    // [stated] "dodge plays when the player/mob is making a melee attack on it or a close range attack with a gun. it
    // dodges the attack taking no damage and gets a speed boost lasting for 60 seconds. this increases its speed by
    // 2.5x for attacks movement etc... while it has this buff is when it can use attack quick. once attack quick is
    // used is succession theres a 30s cooldown. dodge itself has a 180s cool down."

    public static final int DODGE_BUFF_TICKS = 60 * 20;

    public static final int DODGE_COOLDOWN_TICKS = 180 * 20;

    /** ⭐ 2.5x. Applied as a MULTIPLY_TOTAL modifier of +1.5, which is what "two and a half times" means. */
    public static final double DODGE_SPEED_MULTIPLIER = 2.5;

    /**
     * ⚠⚠ THE "CLOSE RANGE" LINE IS THE WHOLE TRIGGER, and it is what separates a melee swing or a point-blank shot from
     * a rifle round across a field. He named both cases by their RANGE, not their damage type, so the rule is distance
     * to the attacker - a bullet fired from 3 blocks dodges, the same bullet from 30 does not.
     */
    private static final double DODGE_TRIGGER_RANGE = 5.0;

    private static final ResourceLocation DODGE_SPEED_MODIFIER_ID = AlienResources.location("razor_claw_dodge_speed");

    /**
     * ⭐⭐ THE TELL, while the speed buff is up. [stated] "have light blue and white colored particles playing to show
     * some effect is happening."
     * <p>
     * ⚠ ONE PARTICLE TYPE, NOT TWO. `DustColorTransitionOptions` fades each mote from the first colour to the second
     * over its own life, so every particle IS light-blue-and-white rather than the field being a mix of separate blue
     * ones and white ones. Two `DustParticleOptions` calls would have given the same palette but a noisier, speckled
     * look - this reads as a single shimmer.
     * </p>
     * <p>
     * ⚠ DELIBERATELY UNLIKE THE CHRYSALIS TELL, which is flat red dust. Two buffs that both mean "do not just walk up
     * and hit this" need to be told apart at a glance, so one is a colour and the other is a fade.
     * </p>
     */
    private static final Vector3f DODGE_PARTICLE_FROM_COLOUR = new Vector3f(0.45F, 0.78F, 1.0F);

    private static final Vector3f DODGE_PARTICLE_TO_COLOUR = new Vector3f(1.0F, 1.0F, 1.0F);

    private static final float DODGE_PARTICLE_SCALE = 1.0F;

    /** Slightly denser and faster than the chrysalis curl - this one is a SPEED buff, so it should look quick. */
    private static final int DODGE_PARTICLE_COUNT = 5;

    private static final int DODGE_PARTICLE_INTERVAL_TICKS = 3;

    /**
     * ⭐ THE FLURRY. [stated] "its a quick attack with both arms if an arm is missing it will still play but do 50% less
     * damage. the animation plays 4 times is succession."
     * <p>
     * ⚠⚠ IT NEEDS AT LEAST ONE ARM - `requiresAnyArm()`. [stated] "if both arms are missing flurry wouldnt be able to
     * play at all." His "if an arm is missing" was ONE arm, not both: one gone halves the damage, both gone means there
     * is nothing left to swing with and the attack is refused outright.
     * <p>
     * ⚠ SO THE LIMB STATE IS BOTH A GATE AND A PRICE: the gate is `requiresAnyArm()`, the price is the 50% penalty
     * applied in {@code applyQuickDamage}. Neither alone expresses the rule.
     * </p>
     */
    private static final int QUICK_FLURRY_REPEATS = 4;

    /** ⚠ PUBLIC: the animator needs it to know where each repeat boundary falls. */
    public static final int QUICK_TICKS_PER_REPEAT = 8;

    private static final float QUICK_DAMAGE_FRACTION = 0.45F;

    private static final float QUICK_MISSING_ARM_PENALTY = 0.5F;

    private static final int QUICK_COOLDOWN_TICKS = 30 * 20;

    /**
     * ⭐ THE ARMOUR-PIERCING CHARGE. [stated] "a hard hitting attack that bypasses armor and defense. it has a 90s
     * cooldown."
     */
    private static final float CHARGE_DAMAGE_FRACTION = 1.6F;

    private static final int CHARGE_COOLDOWN_TICKS = 90 * 20;

    /**
     * ⚠ THE FLURRY IS GATED ON THE BUFF, not on a cooldown alone. [stated] "while it has this buff is when it can use
     * attack quick" - so `activationCondition` refuses it outright the rest of the time, and the 30s cooldown only
     * governs how often it may repeat WITHIN a buff window.
     */
    public static final AttackType QUICK = AttackType.builder("razor_claw_quick")
        .requiresAnyArm()
        .defaultDurationInTicks(QUICK_FLURRY_REPEATS * QUICK_TICKS_PER_REPEAT)
        .cooldownInTicks(QUICK_COOLDOWN_TICKS)
        .activationCondition(xenomorph -> xenomorph instanceof RazorClaw razorClaw && razorClaw.hasDodgeBuff())
        .executorFactory(() -> new FlurryAttackExecutor(QUICK_FLURRY_REPEATS, QUICK_TICKS_PER_REPEAT))
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(RazorClaw::applyQuickDamage)
        .build();

    public static final AttackType CHARGE = AttackType.builder("razor_claw_charge")
        .requiresAnyArm()
        .defaultDurationInTicks(24)
        .cooldownInTicks(CHARGE_COOLDOWN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(RazorClaw::applyChargeDamage)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.LARGE, RazorClaw::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(CLAW)
                .addRegular(BITE)
                .addRegular(TAIL)
                .addRegular(QUICK)
                .addTriggered(RazorClawSweepAttack.ATTACK)
                .addTriggered(CHARGE)
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

    public final DataAccessor<Integer> dodgeBuffTicks;

    public final DataAccessor<Integer> dodgeCooldownTicks;

    /** Bumped on every dodge; the animator edge-detects it so the clip plays exactly once. */
    public final DataAccessor<Integer> dodgeId;

    public RazorClaw(EntityType<? extends RazorClaw> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.dodgeBuffTicks = new DataAccessor<>(this, AlienDataSyncKeys.RAZOR_CLAW_DODGE_BUFF_TICKS.get());
        this.dodgeCooldownTicks = new DataAccessor<>(this, AlienDataSyncKeys.RAZOR_CLAW_DODGE_COOLDOWN_TICKS.get());
        this.dodgeId = new DataAccessor<>(this, AlienDataSyncKeys.RAZOR_CLAW_DODGE_ID.get());
        this.animationDispatcher = new RazorClawAnimationDispatcher(this);
    }

    public boolean hasDodgeBuff() {
        return dodgeBuffTicks.get() > 0;
    }

    /**
     * ⭐⭐ THE NINJA HALF OF THE BUFF. [stated] "2.5x for attacks would be prefered think of it like a ninja."
     * <p>
     * Every attack takes 1/2.5 of its authored ticks while the buff is up, and the ANIMATION FOLLOWS AUTOMATICALLY:
     * animators derive clip speed from {@code animation.length() / attackDurationInTicks}, reading the synced duration,
     * so a shortened attack plays its clip proportionally faster with no per-clip change. Swing and damage tick stay
     * locked together, which is exactly what scaling the clip directly would have broken.
     * </p>
     * <p>
     * ⚠ SAME NUMBER AS THE MOVEMENT BUFF, deliberately - one dial, `DODGE_SPEED_MULTIPLIER`, so "2.5x" cannot come to
     * mean two different things depending on which half of the buff you look at.
     * </p>
     */
    @Override
    public float attackSpeedMultiplier() {
        return hasDodgeBuff() ? (float) DODGE_SPEED_MULTIPLIER : 1.0F;
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            tickDodgeState();
        }
    }

    private void tickDodgeState() {
        var cooldown = dodgeCooldownTicks.get();

        if (cooldown > 0) {
            dodgeCooldownTicks.set(cooldown - 1);
        }

        var buff = dodgeBuffTicks.get();

        if (buff > 0) {
            dodgeBuffTicks.set(buff - 1);
            spawnDodgeBuffParticles();

            if (buff - 1 <= 0) {
                removeDodgeSpeedModifier();
            }
        }
    }

    /**
     * The blue-to-white shimmer that says the speed buff is up.
     * <p>
     * ⚠ SERVER-SIDE `sendParticles`, not a client spawn: this is only reached from the server half of `tick()`, and
     * `sendParticles` broadcasts to everyone in range - so every player sees the tell, not just whoever is simulating.
     * </p>
     */
    private void spawnDodgeBuffParticles() {
        if (tickCount % DODGE_PARTICLE_INTERVAL_TICKS != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(
            new DustColorTransitionOptions(
                DODGE_PARTICLE_FROM_COLOUR,
                DODGE_PARTICLE_TO_COLOUR,
                DODGE_PARTICLE_SCALE
            ),
            getX(),
            getY(0.5),
            getZ(),
            DODGE_PARTICLE_COUNT,
            getBbWidth() * 0.5,
            getBbHeight() * 0.4,
            getBbWidth() * 0.5,
            0.0
        );
    }

    /**
     * ⚠⚠ THE DODGE LIVES IN `hurt`, NOT IN A GOAP ACTION OR A GOAL.
     * <p>
     * It has to REFUSE damage that is already being dealt, and the only place that decision exists is here. A sensor
     * would run on its own schedule and could only react AFTER the hit landed, which is the opposite of a dodge.
     * </p>
     * <p>
     * ⚠ RANGE, NOT DAMAGE TYPE. [stated] "a melee attack on it or a close range attack with a gun" - both are named by
     * their range, so the gate is distance to the attacker. A bullet from 3 blocks dodges; the same bullet from 30 does
     * not. That also means it cannot dodge fire, fall damage, acid or anything with no attacker at all, which is right:
     * there is nothing to sidestep.
     * </p>
     */
    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float damage) {
        if (tryDodge(damageSource)) {
            return false;
        }

        return super.hurt(damageSource, damage);
    }

    private boolean tryDodge(DamageSource damageSource) {
        if (level().isClientSide || !isAlive() || dodgeCooldownTicks.get() > 0) {
            return false;
        }

        // ⚠ Nothing to sidestep if nobody swung: environmental damage, acid pools and DoT effects all fall through.
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return false;
        }

        // ⚠ BYPASS-INVULNERABILITY SOURCES ARE NOT DODGEABLE - /kill and the void are not attacks.
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        if (distanceToSqr(attacker) > DODGE_TRIGGER_RANGE * DODGE_TRIGGER_RANGE) {
            return false;
        }

        beginDodge();
        return true;
    }

    private void beginDodge() {
        dodgeBuffTicks.set(DODGE_BUFF_TICKS);
        dodgeCooldownTicks.set(DODGE_COOLDOWN_TICKS);
        dodgeId.set(dodgeId.get() + 1);
        applyDodgeSpeedModifier();
    }

    /**
     * ⚠ TRANSIENT AND ID-KEYED. A transient modifier is not saved, and re-adding the same id is a no-op rather than a
     * stack - so a dodge landing while the buff is already up refreshes the timer without compounding the speed. If it
     * stacked, three dodges in a minute would make it faster than the pathfinder can steer.
     */
    private void applyDodgeSpeedModifier() {
        var attribute = getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute == null || attribute.getModifier(DODGE_SPEED_MODIFIER_ID) != null) {
            return;
        }

        attribute.addTransientModifier(
            new AttributeModifier(
                DODGE_SPEED_MODIFIER_ID,
                DODGE_SPEED_MULTIPLIER - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
        );
    }

    private void removeDodgeSpeedModifier() {
        var attribute = getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute != null) {
            attribute.removeModifier(DODGE_SPEED_MODIFIER_ID);
        }
    }

    /**
     * ⚠ HALF DAMAGE ON **ONE** MISSING ARM. [stated] "if an arm is missing it will still play but do 50% less damage."
     * It is a both-arms attack, so losing either halves it. BOTH arms gone never reaches here at all -
     * `requiresAnyArm()` on the attack type refuses the swing first.
     */
    private static void applyQuickDamage(Xenomorph xenomorph, LivingEntity target) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        var bothArms = !MirroredAttackSide.isArmDetached(xenomorph, true)
            && !MirroredAttackSide.isArmDetached(xenomorph, false);
        var fraction = bothArms ? QUICK_DAMAGE_FRACTION : QUICK_DAMAGE_FRACTION * QUICK_MISSING_ARM_PENALTY;

        xenomorph.swing(InteractionHand.MAIN_HAND);
        ScaledDamage.hurtScaled(xenomorph, target, fraction);
    }

    /**
     * ⚠⚠ ARMOUR BYPASS IS THE DAMAGE **TYPE**, NOT A NUMBER. [stated] "bypasses armor and defense." Rolling the
     * reduction by hand and inflating the damage to compensate would be wrong against every different armour value; a
     * source in the {@code bypasses_armor} tag is refused by the armour maths outright.
     * <p>
     * ⚠ IT USES VANILLA `MAGIC`, which is in that tag, rather than a new avp_alien damage type - a new type is a
     * datagen file, and this needed none. ✅ [stated] "it should bypass armor and toughness you can let protection and
     * resistance still play their roles" - which is EXACTLY what magic does, so this is the finished behaviour and not
     * a compromise: `bypasses_armor` only, never `bypasses_effects` or `bypasses_enchantments`.
     * </p>
     */
    private static void applyChargeDamage(Xenomorph xenomorph, LivingEntity target) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        xenomorph.swing(InteractionHand.MAIN_HAND);

        var registry = xenomorph.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var piercing = new DamageSource(registry.getHolderOrThrow(DamageTypes.MAGIC), xenomorph);
        var damage = (float) xenomorph.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE_FRACTION;

        target.hurt(piercing, damage);
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
