package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.Host;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.util.AlienEmbryoUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The business end of the Growth Suppression potion (brewed from poison jelly). Instantaneous, so drinking and splash
 * both land here. Two audiences:
 * <ul>
 * <li><b>Xenomorphs</b> - sets the persistent poisoned/suppressed flag: the alien is frozen in its current caste and
 * cannot molt or grow until a Metamorphosis potion clears the flag (see
 * {@code MetamorphosisStatusEffect#onEffectStarted}).</li>
 * <li><b>Hosts carrying a chestburster</b> - each dose RESETS the burst clock to {@link #DELAY_IN_TICKS} (five
 * Minecraft days) remaining. It never stacks, so the optimal play is dosing at the last moment. Every dose gambles
 * with jelly sickness: {@link #BASE_SICKNESS_CHANCE} on the first dose, climbing {@link #SICKNESS_CHANCE_PER_DOSE}
 * per dose taken this implantation. Each hit ratchets the host's hidden toxicity one tier (Jelly Sickness I-IV; IV is
 * wither-grade). Once toxicity sits at IV, the NEXT dose grants no time and instead flips the coin:
 * <ul>
 * <li><b>Death sentence</b> - the embryo is marked withered on the spot and Wither II runs for 30 seconds as the
 * execution. Cheating death (milk, heavy healing) is possible - but the potion is spent for this implantation, and
 * the withered burster still comes on whatever clock remains.</li>
 * <li><b>Mercy</b> - the burster dies instead; the host lives, but must last through the same withering.</li>
 * </ul>
 * All the hidden counters live on the {@link Host} and reset only when the embryo leaves the body.</li>
 * </ul>
 */
public class GrowthSuppressionStatusEffect extends MobEffect {

    private static final int JELLY_PARTICLE_COLOR = 0x7B5CA8;

    /** Five Minecraft days: what every successful dose resets the remaining gestation to. */
    public static final int DELAY_IN_TICKS = 5 * 24000;

    private static final double BASE_SICKNESS_CHANCE = 0.32;

    private static final double SICKNESS_CHANCE_PER_DOSE = 0.08;

    private static final int MAX_TOXICITY = 4;

    /** Jelly Sickness durations per tier (I-IV): brief by design - the danger is the ratchet, not the tick damage. */
    private static final int[] SICKNESS_DURATION_TICKS = { 200, 240, 300, 400 };

    /** The withering: Wither II for 30 seconds, both as the death sentence and the survivor's ordeal. */
    private static final int WITHERING_DURATION_TICKS = 30 * 20;

    private static final int WITHERING_AMPLIFIER = 1;

    public GrowthSuppressionStatusEffect() {
        super(MobEffectCategory.HARMFUL, JELLY_PARTICLE_COLOR);
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    @Override
    public void applyInstantenousEffect(
        @Nullable Entity source,
        @Nullable Entity indirectSource,
        @NotNull LivingEntity target,
        int amplifier,
        double health
    ) {
        if (target.level().isClientSide) {
            return;
        }

        if (target instanceof Alien alien) {
            alien.setPoisoned(true);
            return;
        }

        if (target instanceof Host host && host.getEmbryoType().isSome()) {
            handleHostDose(target, host);
        }
    }

    private static void handleHostDose(LivingEntity hostEntity, Host host) {
        // A survivor of a cheated death sentence: the potion is spent for this implantation.
        if (host.isSuppressionSpent()) {
            if (hostEntity instanceof Player player) {
                player.displayClientMessage(
                    Component.literal("You feel movement in your chest, the potions no longer effective")
                        .withStyle(ChatFormatting.RED),
                    false
                );
            }
            return;
        }

        // Toxicity maxed: this dose grants nothing and flips the coin instead.
        if (host.getJellyToxicity() >= MAX_TOXICITY) {
            host.setSuppressionSpent(true);

            if (hostEntity.getRandom().nextBoolean()) {
                // Death sentence: the embryo soaked in the wither - the demon is made at the flip, not at the death.
                host.setEmbryoWithered(true);
            } else {
                // Mercy: the burster dies; the host endures the withering.
                host.removeEmbryo();
            }

            hostEntity.addEffect(
                new MobEffectInstance(MobEffects.WITHER, WITHERING_DURATION_TICKS, WITHERING_AMPLIFIER)
            );
            return;
        }

        // Normal dose: reset the clock to five days remaining (never stacks), then gamble.
        var doses = host.getSuppressionDoseCount() + 1;
        host.setSuppressionDoseCount(doses);
        host.setEmbryoGrowthTimeInTicks(AlienEmbryoUtil.BURST_TIME_IN_TICKS - DELAY_IN_TICKS);

        var sicknessChance = Math.min(BASE_SICKNESS_CHANCE + SICKNESS_CHANCE_PER_DOSE * (doses - 1), 1.0);

        if (hostEntity.getRandom().nextDouble() < sicknessChance) {
            var toxicity = Math.min(host.getJellyToxicity() + 1, MAX_TOXICITY);
            host.setJellyToxicity(toxicity);
            hostEntity.addEffect(
                new MobEffectInstance(
                    AlienMobEffects.getJellySicknessHolder(),
                    SICKNESS_DURATION_TICKS[toxicity - 1],
                    toxicity - 1
                )
            );
        }
    }
}
