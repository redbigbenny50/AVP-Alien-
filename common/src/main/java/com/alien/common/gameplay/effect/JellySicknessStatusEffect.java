package com.alien.common.gameplay.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The Growth Suppression potion's toll, in four tiers (amplifiers 0-3 shown as I-IV). Tiers I-III behave like poison -
 * quickening magic damage that cannot kill. Tier IV is wither-grade: it damages like the withering and CAN kill. The
 * durations are deliberately brief (see {@code GrowthSuppressionStatusEffect}) - the real threat is the hidden
 * toxicity ratchet on the host, not the tick damage itself.
 */
public class JellySicknessStatusEffect extends MobEffect {

    private static final int SICKLY_JELLY_COLOR = 0x4E6B2A;

    /** Damage intervals per amplifier 0-3: I 25t, II 20t, III 15t (poison-like), IV 20t (wither-like, lethal). */
    private static final int[] DAMAGE_INTERVAL_TICKS = { 25, 20, 15, 20 };

    public JellySicknessStatusEffect() {
        super(MobEffectCategory.HARMFUL, SICKLY_JELLY_COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        var interval = DAMAGE_INTERVAL_TICKS[Math.min(Math.max(amplifier, 0), DAMAGE_INTERVAL_TICKS.length - 1)];
        return tickCount % interval == 0;
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        if (amplifier >= 3) {
            // Tier IV: wither-grade - this one can kill.
            livingEntity.hurt(livingEntity.damageSources().wither(), 1.0F);
        } else if (livingEntity.getHealth() > 1.0F) {
            // Tiers I-III: poison-like - leaves the victim at half a heart, never finishes them.
            livingEntity.hurt(livingEntity.damageSources().magic(), 1.0F);
        }

        return true;
    }
}
