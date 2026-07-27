package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.registry.init.item.AlienItems;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

public class AlienPotions {

    private static final BLibRegistry<Potion> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.POTION);

    private static final int ONE_MINUTE_IN_TICKS = 20 * 60;

    // Blood Loss is NOT brewable and deliberately has no potion: it is a wound, inflicted by a razor claw's claws
    // and by nothing else. Awkward + Chitin, and its redstone/glowstone tiers, are gone. The effect, its mixin and
    // RazorClaw.doHurtTarget are untouched. Existing bottles in old worlds become "uncraftable potion" items.

    public static final BLibHolder<Potion> METAMORPHOSIS = REGISTRY.createHolder(
            "metamorphosis",
            () -> new Potion(
                    "metamorphosis",
                    new MobEffectInstance(AlienMobEffects.getMetamorphosisHolder(), ONE_MINUTE_IN_TICKS)
            )
    );

    // Metamorphosis is deliberately untiered: the effect is an on/off growth accelerant, so longer or stronger
    // variants would be meaningless. Existing long/strong bottles in old worlds become "uncraftable potion" items.

    /**
     * Single-tier by design, like Metamorphosis. Instant effect: on a xenomorph it freezes growth (reversed only by
     * Metamorphosis); on a host carrying a chestburster it resets the burst clock to five days out, with an escalating
     * jelly-sickness gamble - see {@code GrowthSuppressionStatusEffect}.
     */
    public static final BLibHolder<Potion> GROWTH_SUPPRESSION = REGISTRY.createHolder(
            "growth_suppression",
            () -> new Potion(
                    "growth_suppression",
                    new MobEffectInstance(AlienMobEffects.getGrowthSuppressionHolder(), 1)
            )
    );

    public static final BLibHolder<Potion> SCOURGE = REGISTRY.createHolder(
            "scourge",
            () -> new Potion(
                    "scourge",
                    new MobEffectInstance(AlienMobEffects.getScourgeHolder(), ONE_MINUTE_IN_TICKS)
            )
    );

    // Scourge is deliberately untiered, for the same reason Metamorphosis is: ScourgeStatusEffect carries no tick
    // logic and reads no amplifier - it is a pure marker that a growth stage checks for, so ONE application is all
    // that is ever needed to send an alien to its raid form. Longer and stronger bottles changed nothing and only
    // widened the brewing tree. Existing long/strong bottles in old worlds become "uncraftable potion" items.

    private static void registerBrewingRecipes() {
        var brewingRegistry = Alien.MOD.registries().createBrewingRegistry();

        // Awkward + Raw Royal Jelly -> Metamorphosis
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.RAW_ROYAL_JELLY,
                METAMORPHOSIS
        );

        // Awkward + Poison Jelly -> Growth Suppression
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.POISON_JELLY,
                GROWTH_SUPPRESSION
        );

        // Awkward + Raw Scourge Jelly -> Scourge
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.RAW_SCOURGE_JELLY,
                SCOURGE
        );

        // Awkward + Nether Chitin -> Fire Resistance
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.NETHER_CHITIN,
                Potions.FIRE_RESISTANCE
        );

        // Fire Resistance + Nether Chitin -> Long Fire Resistance
        brewingRegistry.registerMix(
                Potions.FIRE_RESISTANCE,
                AlienItems.NETHER_CHITIN,
                Potions.LONG_FIRE_RESISTANCE
        );

        // Awkward + Nether Resin Ball -> Fire Resistance
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.NETHER_RESIN_BALL,
                Potions.FIRE_RESISTANCE
        );

        // Fire Resistance + Nether Resin Ball -> Long Fire Resistance
        brewingRegistry.registerMix(
                Potions.FIRE_RESISTANCE,
                AlienItems.NETHER_RESIN_BALL,
                Potions.LONG_FIRE_RESISTANCE
        );

        // Awkward + Aberrant Chitin -> Weakness
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.ABERRANT_CHITIN,
                Potions.WEAKNESS
        );

        // Weakness + Aberrant Chitin -> Long Weakness
        brewingRegistry.registerMix(
                Potions.WEAKNESS,
                AlienItems.ABERRANT_CHITIN,
                Potions.LONG_WEAKNESS
        );

        // Awkward + Aberrant Resin Ball -> Weakness
        brewingRegistry.registerMix(
                Potions.AWKWARD,
                AlienItems.ABERRANT_RESIN_BALL,
                Potions.WEAKNESS
        );

        // Weakness + Aberrant Resin Ball -> Long Weakness
        brewingRegistry.registerMix(
                Potions.WEAKNESS,
                AlienItems.ABERRANT_RESIN_BALL,
                Potions.LONG_WEAKNESS
        );
    }

    public static void initialize() {
        REGISTRY.registerAll();
        registerBrewingRecipes();
    }
}