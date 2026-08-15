package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.gameplay.effect.BloodLossStatusEffect;
import com.alien.common.gameplay.effect.ChitinousAuraStatusEffect;
import com.alien.common.gameplay.effect.FrenzyStatusEffect;
import com.alien.common.gameplay.effect.GlowingTalonsStatusEffect;
import com.alien.common.gameplay.effect.GrowthSuppressionStatusEffect;
import com.alien.common.gameplay.effect.HivesBaneStatusEffect;
import com.alien.common.gameplay.effect.ImmovableStatusEffect;
import com.alien.common.gameplay.effect.JellySicknessStatusEffect;
import com.alien.common.gameplay.effect.MarkedForDeathStatusEffect;
import com.alien.common.gameplay.effect.MetamorphosisStatusEffect;
import com.alien.common.gameplay.effect.RadiationResistanceStatusEffect;
import com.alien.common.gameplay.effect.RadiationSicknessStatusEffect;
import com.alien.common.gameplay.effect.ScourgeStatusEffect;
import com.alien.common.gameplay.effect.StunnedStatusEffect;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import java.util.function.Supplier;

public class AlienMobEffects {

    private static final BLibRegistry<MobEffect> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.MOB_EFFECT);

    private static final BLibHolder<MobEffect> BLOOD_LOSS = create("blood_loss", BloodLossStatusEffect::new);

    private static final BLibHolder<MobEffect> CHITINOUS_AURA = create("chitinous_aura", ChitinousAuraStatusEffect::new);

    private static final BLibHolder<MobEffect> RADIATION_SICKNESS =
        create("radiation_sickness", RadiationSicknessStatusEffect::new);

    private static final BLibHolder<MobEffect> RADIATION_RESISTANCE =
        create("radiation_resistance", RadiationResistanceStatusEffect::new);

    private static final BLibHolder<MobEffect> GLOWING_TALONS =
        create("glowing_talons", GlowingTalonsStatusEffect::new);

    private static final BLibHolder<MobEffect> FRENZY = create("frenzy", FrenzyStatusEffect::new);

    private static final BLibHolder<MobEffect> GROWTH_SUPPRESSION = create(
        "growth_suppression",
        GrowthSuppressionStatusEffect::new
    );

    private static final BLibHolder<MobEffect> HIVES_BANE = create("hives_bane", HivesBaneStatusEffect::new);

    private static final BLibHolder<MobEffect> JELLY_SICKNESS = create("jelly_sickness", JellySicknessStatusEffect::new);

    private static final BLibHolder<MobEffect> METAMORPHOSIS = create("metamorphosis", MetamorphosisStatusEffect::new);

    private static final BLibHolder<MobEffect> SCOURGE = create("scourge", ScourgeStatusEffect::new);

    private static final BLibHolder<MobEffect> IMMOVABLE = create("immovable", ImmovableStatusEffect::new);

    private static final BLibHolder<MobEffect> MARKED_FOR_DEATH = create(
        "marked_for_death",
        MarkedForDeathStatusEffect::new
    );

    private static final BLibHolder<MobEffect> STUNNED = create("stunned", StunnedStatusEffect::new);

    public static Holder<MobEffect> getRadiationResistanceHolder() {
        return RADIATION_RESISTANCE.getBackingHolder();
    }

    public static Holder<MobEffect> getGlowingTalonsHolder() {
        return GLOWING_TALONS.getBackingHolder();
    }

    public static Holder<MobEffect> getRadiationSicknessHolder() {
        return RADIATION_SICKNESS.getBackingHolder();
    }

    public static Holder<MobEffect> getChitinousAuraHolder() {
        return CHITINOUS_AURA.getBackingHolder();
    }

    public static Holder<MobEffect> getStunnedHolder() {
        return STUNNED.getBackingHolder();
    }

    public static Holder<MobEffect> getBloodLossHolder() {
        return BLOOD_LOSS.getBackingHolder();
    }

    public static Holder<MobEffect> getFrenzyHolder() {
        return FRENZY.getBackingHolder();
    }

    public static Holder<MobEffect> getGrowthSuppressionHolder() {
        return GROWTH_SUPPRESSION.getBackingHolder();
    }

    public static Holder<MobEffect> getHivesBaneHolder() {
        return HIVES_BANE.getBackingHolder();
    }

    public static Holder<MobEffect> getJellySicknessHolder() {
        return JELLY_SICKNESS.getBackingHolder();
    }

    public static Holder<MobEffect> getMetamorphosisHolder() {
        return METAMORPHOSIS.getBackingHolder();
    }

    public static Holder<MobEffect> getImmovableHolder() {
        return IMMOVABLE.getBackingHolder();
    }

    public static Holder<MobEffect> getScourgeHolder() {
        return SCOURGE.getBackingHolder();
    }

    public static Holder<MobEffect> getMarkedForDeathHolder() {
        return MARKED_FOR_DEATH.getBackingHolder();
    }

    private static <T extends MobEffect> BLibHolder<T> create(String name, Supplier<T> supplier) {
        return REGISTRY.createHolder(name, supplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
