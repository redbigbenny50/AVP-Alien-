package com.alien.common.gameplay.effect;

import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.compatibility.avp_human.AVPHuman;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * A standalone copy of AVP: Human's radiation sickness, for when AVP: Human is not installed.
 * <h2>Why this exists</h2> Irradiated hives, irradiated resin and irradiated xenomorphs are avp_alien's own content and
 * keep working with AVP: Human uninstalled - but the SICKNESS those things are supposed to cause lives in that mod.
 * Without a fallback, removing AVP: Human from a world silently guts the irradiated strain: the blocks stay, the hive
 * stays, and nothing they do to you means anything any more. This keeps the teeth in.
 * <p>
 * When AVP: Human IS present its own exposure counter is used instead, through {@code RadiationCompat} - a shared
 * counter that decays properly and stacks across every source in the pack. This effect is strictly the understudy.
 * <h2>What it copies</h2> The numbers are taken from AVP: Human's {@code RadiationStatusEffect} so the two feel the
 * same, and are named the same way so a future divergence is easy to spot:
 * <ul>
 * <li>Damage is {@code 1.0 + 0.5 * amplifier} per hit.</li>
 * <li>The interval follows a bell: it starts SLOW during incubation, tightens to one hit every
 * {@value #MIN_DAMAGE_INTERVAL_TICKS} ticks at 80% elapsed, then eases back off - the step down. Sickness that ramps,
 * peaks and fades rather than ticking flatly.</li>
 * <li>Weakness and Hunger throughout; Slowness from rank II; Blindness from rank III.</li>
 * </ul>
 * <h2>Death message</h2> Its own damage type, {@code avp_alien:radiation_sickness} - "was welcomed to the wasteland".
 * There is no collision risk with AVP: Human's {@code avp_human:radiation}: separate namespaces, separate registry
 * entries, and this one is only ever dealt while that mod is absent anyway.
 */
public class RadiationSicknessStatusEffect extends MobEffect {

    /** Sickly green, the colour AVP: Human uses for the same thing. */
    private static final int RADIATION_GREEN = 0x4C8C2B;

    public static final int SHORT_DURATION_TICKS = 20 * 60;

    /** Two and a half minutes - AVP: Human's middle tier. */
    public static final int MEDIUM_DURATION_TICKS = 20 * 150;

    /**
     * What a raw irradiated jelly hands out: forty seconds at rank I, which the schedule below works out to seventeen
     * hits for seventeen damage - punishing, and survivable if you react. Deliberately shorter than AVP: Human's own
     * SHORT tier, because a jelly is a bad decision rather than a clinical dose.
     */
    public static final int JELLY_DOSE_DURATION_TICKS = 20 * 40;

    public static final int LONG_DURATION_TICKS = 20 * 300;

    private static final float INCUBATION_RATIO = 0.2F;

    private static final float PEAK_DAMAGE_RATIO = 0.8F;

    private static final float BASE_DAMAGE = 1.0F;

    private static final float DAMAGE_PER_AMPLIFIER = 0.5F;

    private static final int MAX_DAMAGE_INTERVAL_TICKS = 80;

    private static final int MIN_DAMAGE_INTERVAL_TICKS = 20;

    private static final int SIDE_EFFECT_DURATION_TICKS = 100;

    /**
     * The duration the curve is measured against.
     * <p>
     * The MobEffect API only ever hands us the REMAINING ticks, never the total, so progress has to be measured against
     * something fixed. A per-victim map of totals was the obvious alternative and was rejected: MobEffect has no
     * removal hook to clear it with, so it would leak an entry per victim forever. Every dose this mod hands out uses
     * {@link #MEDIUM_DURATION_TICKS}, and a dose applied at some other length simply spends longer at one end of the
     * curve rather than breaking.
     */
    private static final int CURVE_REFERENCE_TICKS = JELLY_DOSE_DURATION_TICKS;

    /**
     * Which ticks-remaining values land a hit, precomputed once.
     * <p>
     * The obvious test - {@code remaining % calculateDamageInterval(progress) == 0} - is WRONG when the interval is
     * itself changing: values of {@code remaining} keep falling divisible by whatever the interval happens to be, so it
     * fired far too often and, worse, almost the same number of times regardless of duration. A twenty second dose and
     * a two and a half minute one both landed about eighty hits. The curve has to be walked forward properly - fire,
     * then wait {@code interval} ticks - which needs state the MobEffect API cannot carry, so it is baked into a lookup
     * instead. Same answer every time, no per-victim bookkeeping.
     */
    private static final boolean[] HIT_SCHEDULE = buildHitSchedule();

    private static boolean[] buildHitSchedule() {
        var schedule = new boolean[CURVE_REFERENCE_TICKS + 1];
        var elapsed = 0;
        while (elapsed < CURVE_REFERENCE_TICKS) {
            schedule[CURVE_REFERENCE_TICKS - elapsed] = true;
            elapsed += calculateDamageInterval((float) elapsed / CURVE_REFERENCE_TICKS);
        }
        return schedule;
    }

    public RadiationSicknessStatusEffect() {
        super(MobEffectCategory.HARMFUL, RADIATION_GREEN);
    }

    /**
     * AVP: Human's curve, reproduced exactly: ramp in from {@value #INCUBATION_RATIO} of the way through, peak at
     * {@value #PEAK_DAMAGE_RATIO}, then ease back off over the tail.
     */
    public static int calculateDamageInterval(float progress) {
        float intensity;
        if (progress < PEAK_DAMAGE_RATIO) {
            intensity = (progress - INCUBATION_RATIO) / (PEAK_DAMAGE_RATIO - INCUBATION_RATIO);
        } else {
            intensity = 1.0F - (progress - PEAK_DAMAGE_RATIO) / (1.0F - PEAK_DAMAGE_RATIO);
        }

        var interval = (int) (MAX_DAMAGE_INTERVAL_TICKS - intensity * (MAX_DAMAGE_INTERVAL_TICKS - MIN_DAMAGE_INTERVAL_TICKS));
        return Math.max(MIN_DAMAGE_INTERVAL_TICKS, interval);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        // Cannot see the victim here, so the cadence is evaluated in applyEffectTick against the remembered total.
        return true;
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        // Stands down completely whenever AVP: Human is installed. Their radiation is the real system - a shared,
        // decaying counter that every source in the pack feeds - and two independent sicknesses ticking at once would
        // double the damage and stack contradictory side effects. Anything holding this effect from before the mod
        // was added simply goes quiet.
        if (livingEntity.level().isClientSide || AVPHuman.MOD.isLoaded()) {
            return true;
        }

        // Radiation Resistance stops an already-running sickness dead rather than letting it tick out underneath.
        if (livingEntity.hasEffect(AlienMobEffects.getRadiationResistanceHolder())) {
            return true;
        }

        var instance = livingEntity.getEffect(AlienMobEffects.getRadiationSicknessHolder());
        if (instance == null) {
            return true;
        }

        var remaining = instance.getDuration();

        applySideEffects(livingEntity, amplifier);

        if (remaining >= 0 && remaining < HIT_SCHEDULE.length && HIT_SCHEDULE[remaining]) {
            livingEntity.hurt(
                livingEntity.damageSources().source(AlienDamageTypeKeys.RADIATION_SICKNESS),
                BASE_DAMAGE + DAMAGE_PER_AMPLIFIER * amplifier
            );
        }

        return true;
    }

    /** Weakness and hunger always; slowness from rank II; blindness from rank III. AVP: Human's tiering. */
    private static void applySideEffects(LivingEntity livingEntity, int amplifier) {
        addSideEffect(livingEntity, MobEffects.WEAKNESS);
        addSideEffect(livingEntity, MobEffects.HUNGER);

        if (amplifier >= 1) {
            addSideEffect(livingEntity, MobEffects.MOVEMENT_SLOWDOWN);
        }

        if (amplifier >= 2) {
            addSideEffect(livingEntity, MobEffects.BLINDNESS);
        }
    }

    private static void addSideEffect(LivingEntity livingEntity, net.minecraft.core.Holder<MobEffect> effect) {
        livingEntity.addEffect(new MobEffectInstance(effect, SIDE_EFFECT_DURATION_TICKS, 0, true, true));
    }
}
