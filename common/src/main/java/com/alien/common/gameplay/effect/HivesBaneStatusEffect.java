package com.alien.common.gameplay.effect;

import com.alien.AlienResources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

/**
 * What poison jelly does to anything that was never going to become a xenomorph. The hive brews it to hold its own
 * young back; in a body with no growth to suppress, it is simply venom.
 * <p>
 * Rank III on all three counts, self-contained rather than stacking three vanilla effects on the victim's HUD:
 * <ul>
 * <li><b>Harming III</b> the moment it lands - vanilla's {@code 6 << amplifier}, so <b>24</b> damage.</li>
 * <li><b>Poison III</b> for the duration - vanilla's interval is {@code 25 >> amplifier}, so a point of damage every
 * six ticks, and like vanilla poison it will not land the killing blow.</li>
 * <li><b>Slowness III</b> for the duration - vanilla's {@code -0.15} per rank, so a 45% cut to movement speed, applied
 * through the attribute system so it cleans itself up when the effect ends.</li>
 * </ul>
 * Drinking it is as stupid as drinking a Harming potion. Throwing it is the point: anything that survives the opening
 * burst spends the minute pinned near death, slowed, and easy to finish.
 * <p>
 * The opening burst is vanilla's own {@code HARM}, which means it obeys vanilla's rule of HEALING the undead. A zombie
 * was never a host, so the hive's venom failing on it is arguably right - but it is a one-line change to uniform magic
 * damage if that reads wrong in play.
 */
public class HivesBaneStatusEffect extends MobEffect {

    private static final int VENOM_GREEN_COLOR = 0x3F6B1E;

    /** Rank III across the board: vanilla amplifier 2. */
    public static final int RANK = 2;

    /**
     * The opening burst is rank I, NOT rank III like the poison and slowness beside it.
     * <p>
     * Rank III harming is {@code 6 << 2} = 24 damage, more than a player's entire health bar, so drinking this was a
     * guaranteed death rather than a bad decision. Rank I is 6 - exactly a vanilla Potion of Harming, which is what it
     * was always described as. Survivable at full health, lethal if you were already hurt, and still a real opening
     * blow when the bottle is thrown at something.
     */
    private static final int OPENING_BURST_RANK = 0;

    /** Vanilla poison's cadence, {@code 25 >> amplifier}, evaluated at rank III. */
    private static final int POISON_INTERVAL_TICKS = 25 >> RANK;

    /** Vanilla slowness is {@code -0.15} per rank; rank III is a 45% cut. */
    private static final double SLOWNESS_III = -0.15 * (RANK + 1);

    public HivesBaneStatusEffect() {
        super(MobEffectCategory.HARMFUL, VENOM_GREEN_COLOR);
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            AlienResources.location("hives_bane_slowness"),
            SLOWNESS_III,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public void onEffectStarted(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide) {
            return;
        }

        // Vanilla HARM, so this is a Potion of Harming in every respect - including that it HEALS the undead, which
        // reads right for a venom brewed to ruin living tissue. It is instantaneous, so it costs no second HUD icon.
        livingEntity.addEffect(new MobEffectInstance(MobEffects.HARM, 1, OPENING_BURST_RANK));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return tickCount % POISON_INTERVAL_TICKS == 0;
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        // Poison's own rule: it brings you to the edge and leaves you there.
        if (livingEntity.getHealth() > 1.0F) {
            livingEntity.hurt(livingEntity.damageSources().magic(), 1.0F);
        }

        return true;
    }
}
