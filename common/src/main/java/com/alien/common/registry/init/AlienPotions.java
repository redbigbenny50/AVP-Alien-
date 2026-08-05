package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.registry.init.item.AlienItems;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
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

    /**
     * The irradiated strain bottled: a minute of immunity to radiation and a minute of handing it out.
     * <p>
     * Both halves are the same idea from opposite ends - Radiation Resistance pins your own exposure at zero while
     * Glowing Talons doses everything you hit, so you walk through a hot zone untouched and leave everything in it
     * sick. Unlike the other two alien potions this one is aimed squarely at the PLAYER; it has nothing to say to a
     * xenomorph, which was already immune and already dosing on contact.
     */
    public static final BLibHolder<Potion> IRRADIATION = REGISTRY.createHolder(
        "irradiation",
        () -> new Potion(
            "irradiation",
            new MobEffectInstance(AlienMobEffects.getRadiationResistanceHolder(), ONE_MINUTE_IN_TICKS),
            new MobEffectInstance(AlienMobEffects.getGlowingTalonsHolder(), ONE_MINUTE_IN_TICKS)
        )
    );

    // Scourge is deliberately untiered, for the same reason Metamorphosis is: ScourgeStatusEffect carries no tick
    // logic and reads no amplifier - it is a pure marker that a growth stage checks for, so ONE application is all
    // that is ever needed to send an alien to its raid form. Longer and stronger bottles changed nothing and only
    // widened the brewing tree. Existing long/strong bottles in old worlds become "uncraftable potion" items.

    /**
     * The OUTPUTS here are BLibHolders, deliberately, and they must stay that way.
     * <p>
     * They cannot be unwrapped at this point: {@code initialize()} calls {@code REGISTRY.registerAll()} immediately
     * before this, but that only QUEUES the potions for the loader's registry event - they are not in
     * {@code BuiltInRegistries.POTION} yet, so {@code getBackingHolder()} binds, finds nothing, and returns NULL. A
     * null output reaches {@code PotionBrewing.Builder.addMix}, which dereferences it, and the game crashes on world
     * load. That was a real regression; do not "fix" this by unwrapping here again.
     * <p>
     * The wrapper must not survive into vanilla's brewing table either, because it breaks recipe networking - see
     * {@code MixinPotionBrewing_UnwrapHolder}, which unwraps at {@code addMix} time, by which point every holder IS
     * bound.
     */
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

        brewingRegistry.registerMix(
            Potions.AWKWARD,
            AlienItems.RAW_IRRADIATED_JELLY,
            IRRADIATION
        );

        // Awkward + Nether Chitin -> Fire Resistance
        // ONE INGREDIENT PER STRAIN: chitin. The resin balls used to brew the same potions as their chitin, giving
        // each strain two interchangeable routes - but resin balls smelt into slime balls, and an item with two uses
        // should not also be the cheaper half of a brewing pair. Brewing is chitin's job now; the resin ball keeps
        // the furnace.
        //
        // Plain chitin is ARMOUR PLATING, so it brews the only defensive potion vanilla has. Turtle Master's
        // slowness is not a drawback bolted on for balance here - it is the point: raw chitin makes you hard to hurt
        // and hard to move, which is exactly what wearing a xenomorph's shell should feel like.
        //
        // It also closes a gap: nether and aberrant chitin both brewed something and the base strain brewed nothing at
        // all, which left plain chitin with no use anywhere once the blood loss potions were removed.
        brewingRegistry.registerMix(
            Potions.AWKWARD,
            AlienItems.CHITIN,
            Potions.TURTLE_MASTER
        );

        brewingRegistry.registerMix(
            Potions.TURTLE_MASTER,
            AlienItems.CHITIN,
            Potions.LONG_TURTLE_MASTER
        );

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
        // Fire Resistance + Nether Resin Ball -> Long Fire Resistance
        // Awkward + Aberrant Chitin -> Weakness
        // The irradiated strain carries a 1.2x stat multiplier, so its chitin brews the OFFENSIVE line where the
        // others brew utility or defence: nether resists fire, aberrant saps strength, plain chitin turtles up, and
        // this one hits harder. Strength is vanilla's only straight offensive buff - Harming is a thrown weapon rather
        // than something you drink, and it has no LONG form to extend into.
        brewingRegistry.registerMix(
            Potions.AWKWARD,
            AlienItems.IRRADIATED_CHITIN,
            Potions.STRENGTH
        );

        brewingRegistry.registerMix(
            Potions.STRENGTH,
            AlienItems.IRRADIATED_CHITIN,
            Potions.LONG_STRENGTH
        );

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
        // Weakness + Aberrant Resin Ball -> Long Weakness
    }

    public static void initialize() {
        REGISTRY.registerAll();
        registerBrewingRecipes();
    }
}
