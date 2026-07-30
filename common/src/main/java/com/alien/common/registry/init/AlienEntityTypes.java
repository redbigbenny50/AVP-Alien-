package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.gameplay.entity.acid.Acid;
import com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent;
import com.alien.common.gameplay.entity.living.alien.chestburster.Chestburster;
import com.alien.common.gameplay.entity.living.alien.ovipositor.EmpressOvipositor;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.parasite.facehugger.Facehugger;
import com.alien.common.gameplay.entity.living.alien.predalien_adolescent.PredalienAdolescent;
import com.alien.common.gameplay.entity.living.alien.predalien_chestburster.PredalienChestburster;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.Boiler;
import com.alien.common.gameplay.entity.living.alien.xenomorph.burster.Burster;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.Chrysalis;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.Harbinger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.Predalien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.Prowler;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.RazorClaw;
import com.alien.common.gameplay.entity.living.alien.xenomorph.runner.Runner;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.alien.common.gameplay.entity.living.alien.xenomorph.warrior.Warrior;
import com.alien.common.gameplay.entity.projectile.AcidSpit;
import com.blib.api.common.entity.v1.SilencedEntityTypeBuilder;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.blib.api.common.registry.v1.impl.BLibEntityAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class AlienEntityTypes {

    private static final BLibEntityAttributeRegistry ATTRIBUTE_REGISTRY = Alien.MOD.registries().createEntityAttributeRegistry();

    private static final BLibRegistry<EntityType<?>> TYPE_REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ENTITY_TYPE);

    // Acid
    public static final float ACID_WIDTH = 0.66F;

    public static final float ACID_HEIGHT = 0.05F;

    // Acid Spit
    public static final float ACID_SPIT_WIDTH = 0.25F;

    public static final float ACID_SPIT_HEIGHT = 0.25F;

    // Adolescent
    public static final float ADOLESCENT_WIDTH = 0.7F;

    public static final float ADOLESCENT_HEIGHT = 0.7F;

    // Boiler
    public static final float BOILER_WIDTH = 0.8F;

    public static final float BOILER_HEIGHT = 1.98F;

    // Carrier
    public static final float CARRIER_WIDTH = 0.98F;

    public static final float CARRIER_HEIGHT = 3.98F;

    // Chestburster
    public static final float CHESTBURSTER_WIDTH = 0.35F;

    public static final float CHESTBURSTER_HEIGHT = 0.35F;

    // Chrysalis
    public static final float CHRYSALIS_WIDTH = 0.98F;

    public static final float CHRYSALIS_HEIGHT = 2.98F;

    // Crusher
    public static final float CRUSHER_WIDTH = 1.8F;

    public static final float CRUSHER_HEIGHT = 1.98F;

    // Drone
    public static final float DRONE_WIDTH = 0.8F;

    public static final float DRONE_HEIGHT = 1.98F;

    // Facehugger
    public static final float FACEHUGGER_WIDTH = 0.8F;

    public static final float FACEHUGGER_HEIGHT = 0.25F;

    // Harbinger
    public static final float HARBINGER_WIDTH = 0.98F;

    public static final float HARBINGER_HEIGHT = 3.98F;

    // Ovipositor
    public static final float OVIPOSITOR_WIDTH = 5.0F;

    public static final float OVIPOSITOR_HEIGHT = 3.25F;

    public static final float ROYAL_COCOON_WIDTH = 4.0F;

    public static final float ROYAL_COCOON_HEIGHT = 6.0F;

    // Ovomorph
    public static final float OVOMORPH_WIDTH = 0.65F;

    public static final float OVOMORPH_HEIGHT = 0.8F;

    // Praetorian
    public static final float PRAETORIAN_WIDTH = 0.98F;

    public static final float PRAETORIAN_HEIGHT = 3.98F;

    // Predalien
    public static final float PREDALIEN_WIDTH = 0.98F;

    public static final float PREDALIEN_HEIGHT = 3.98F;

    // Prowler
    public static final float PROWLER_WIDTH = 0.8F;

    public static final float PROWLER_HEIGHT = 0.98F;

    // Queen
    public static final float QUEEN_WIDTH = 1.98F;

    public static final float QUEEN_HEIGHT = 3.98F;

    // Ravager
    public static final float RAVAGER_WIDTH = 0.98F;

    public static final float RAVAGER_HEIGHT = 2.98F;

    // Razor Claw
    public static final float RAZOR_CLAW_WIDTH = 0.98F;

    public static final float RAZOR_CLAW_HEIGHT = 2.98F;

    // Burster
    public static final float BURSTER_WIDTH = 0.8F;

    public static final float BURSTER_HEIGHT = 0.98F;

    // Runner
    public static final float RUNNER_WIDTH = 0.8F;

    public static final float RUNNER_HEIGHT = 0.98F;

    // Spitter
    public static final float SPITTER_WIDTH = 0.8F;

    public static final float SPITTER_HEIGHT = 2.5F;

    // Warrior
    public static final float WARRIOR_WIDTH = 0.8F;

    public static final float WARRIOR_HEIGHT = 1.98F;

    public static final BLibHolder<EntityType<Adolescent>> ABERRANT_ADOLESCENT = create(
        "aberrant_adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Boiler>> ABERRANT_BOILER = create(
        "aberrant_boiler",
        EntityType.Builder.of(Boiler::new, MobCategory.MONSTER)
            .sized(BOILER_WIDTH, BOILER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> ABERRANT_CHESTBURSTER = create(
        "aberrant_chestburster",
        EntityType.Builder.of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Carrier>> ABERRANT_CARRIER = create(
        "aberrant_carrier",
        EntityType.Builder.of(Carrier::new, MobCategory.MONSTER)
            .sized(CARRIER_WIDTH, CARRIER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chrysalis>> ABERRANT_CHRYSALIS = create(
        "aberrant_chrysalis",
        EntityType.Builder.of(Chrysalis::new, MobCategory.MONSTER)
            .sized(CHRYSALIS_WIDTH, CHRYSALIS_HEIGHT)
    );

    public static final BLibHolder<EntityType<Crusher>> ABERRANT_CRUSHER = create(
        "aberrant_crusher",
        EntityType.Builder.of(Crusher::new, MobCategory.MONSTER)
            .sized(CRUSHER_WIDTH, CRUSHER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Drone>> ABERRANT_DRONE = create(
        "aberrant_drone",
        EntityType.Builder.of(Drone::new, MobCategory.MONSTER)
            .sized(DRONE_WIDTH, DRONE_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> ABERRANT_FACEHUGGER = create(
        "aberrant_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Harbinger>> ABERRANT_HARBINGER = create(
        "aberrant_harbinger",
        EntityType.Builder.of(Harbinger::new, MobCategory.MONSTER)
            .sized(HARBINGER_WIDTH, HARBINGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> ABERRANT_OVOMORPH = create(
        "aberrant_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Praetorian>> ABERRANT_PRAETORIAN = create(
        "aberrant_praetorian",
        EntityType.Builder.of(Praetorian::new, MobCategory.MONSTER)
            .sized(PRAETORIAN_WIDTH, PRAETORIAN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Predalien>> ABERRANT_PREDALIEN = create(
        "aberrant_predalien",
        EntityType.Builder.of(Predalien::new, MobCategory.MONSTER)
            .sized(PREDALIEN_WIDTH, PREDALIEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienAdolescent>> ABERRANT_PREDALIEN_ADOLESCENT = create(
        "aberrant_predalien_adolescent",
        EntityType.Builder.of(PredalienAdolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienChestburster>> ABERRANT_PREDALIEN_CHESTBURSTER = create(
        "aberrant_predalien_chestburster",
        EntityType.Builder.of(PredalienChestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Prowler>> ABERRANT_PROWLER = create(
        "aberrant_prowler",
        EntityType.Builder.of(Prowler::new, MobCategory.MONSTER)
            .sized(PROWLER_WIDTH, PROWLER_HEIGHT)
    );

    public static final BLibHolder<EntityType<RazorClaw>> ABERRANT_RAZOR_CLAW = create(
        "aberrant_razor_claw",
        EntityType.Builder.of(RazorClaw::new, MobCategory.MONSTER)
            .sized(RAZOR_CLAW_WIDTH, RAZOR_CLAW_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ravager>> ABERRANT_RAVAGER = create(
        "aberrant_ravager",
        EntityType.Builder.of(Ravager::new, MobCategory.MONSTER)
            .sized(RAVAGER_WIDTH, RAVAGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Queen>> ABERRANT_QUEEN = create(
        "aberrant_queen",
        EntityType.Builder.of(Queen::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Empress>> ABERRANT_EMPRESS = create(
        "aberrant_empress",
        EntityType.Builder.of(Empress::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Burster>> ABERRANT_BURSTER = create(
        "aberrant_burster",
        EntityType.Builder.of(Burster::new, MobCategory.MONSTER)
            .sized(BURSTER_WIDTH, BURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Runner>> ABERRANT_RUNNER = create(
        "aberrant_runner",
        EntityType.Builder.of(Runner::new, MobCategory.MONSTER)
            .sized(RUNNER_WIDTH, RUNNER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Spitter>> ABERRANT_SPITTER = create(
        "aberrant_spitter",
        EntityType.Builder.of(Spitter::new, MobCategory.MONSTER)
            .sized(SPITTER_WIDTH, SPITTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Warrior>> ABERRANT_WARRIOR = create(
        "aberrant_warrior",
        EntityType.Builder.of(Warrior::new, MobCategory.MONSTER)
            .sized(WARRIOR_WIDTH, WARRIOR_HEIGHT)
    );

    public static final BLibHolder<EntityType<Acid>> ACID = create(
        "acid",
        EntityType.Builder.of(Acid::new, MobCategory.MISC)
            .sized(ACID_WIDTH, ACID_HEIGHT)
    );

    public static final BLibHolder<EntityType<AcidSpit>> ACID_SPIT = create(
        "acid_spit",
        EntityType.Builder.<AcidSpit>of(AcidSpit::new, MobCategory.MISC)
            .sized(ACID_SPIT_WIDTH, ACID_SPIT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Adolescent>> ADOLESCENT = create(
        "adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Boiler>> BOILER = create(
        "boiler",
        EntityType.Builder.of(Boiler::new, MobCategory.MONSTER)
            .sized(BOILER_WIDTH, BOILER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> CHESTBURSTER = create(
        "chestburster",
        EntityType.Builder.of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Carrier>> CARRIER = create(
        "carrier",
        EntityType.Builder.of(Carrier::new, MobCategory.MONSTER)
            .sized(CARRIER_WIDTH, CARRIER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chrysalis>> CHRYSALIS = create(
        "chrysalis",
        EntityType.Builder.of(Chrysalis::new, MobCategory.MONSTER)
            .sized(CHRYSALIS_WIDTH, CHRYSALIS_HEIGHT)
    );

    public static final BLibHolder<EntityType<Crusher>> CRUSHER = create(
        "crusher",
        EntityType.Builder.of(Crusher::new, MobCategory.MONSTER)
            .sized(CRUSHER_WIDTH, CRUSHER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Drone>> DRONE = create(
        "drone",
        EntityType.Builder.of(Drone::new, MobCategory.MONSTER)
            .sized(DRONE_WIDTH, DRONE_HEIGHT)
    );

    public static final BLibHolder<EntityType<Empress>> EMPRESS = create(
        "empress",
        EntityType.Builder.of(Empress::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Harbinger>> HARBINGER = create(
        "harbinger",
        EntityType.Builder.of(Harbinger::new, MobCategory.MONSTER)
            .sized(HARBINGER_WIDTH, HARBINGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> FACEHUGGER = create(
        "facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Carrier>> IRRADIATED_CARRIER = create(
        "irradiated_carrier",
        EntityType.Builder.of(Carrier::new, MobCategory.MONSTER)
            .sized(CARRIER_WIDTH, CARRIER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chrysalis>> IRRADIATED_CHRYSALIS = create(
        "irradiated_chrysalis",
        EntityType.Builder.of(Chrysalis::new, MobCategory.MONSTER)
            .sized(CHRYSALIS_WIDTH, CHRYSALIS_HEIGHT)
    );

    public static final BLibHolder<EntityType<Crusher>> IRRADIATED_CRUSHER = create(
        "irradiated_crusher",
        EntityType.Builder.of(Crusher::new, MobCategory.MONSTER)
            .sized(CRUSHER_WIDTH, CRUSHER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Drone>> IRRADIATED_DRONE = create(
        "irradiated_drone",
        EntityType.Builder.of(Drone::new, MobCategory.MONSTER)
            .sized(DRONE_WIDTH, DRONE_HEIGHT)
    );

    public static final BLibHolder<EntityType<Praetorian>> IRRADIATED_PRAETORIAN = create(
        "irradiated_praetorian",
        EntityType.Builder.of(Praetorian::new, MobCategory.MONSTER)
            .sized(PRAETORIAN_WIDTH, PRAETORIAN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Predalien>> IRRADIATED_PREDALIEN = create(
        "irradiated_predalien",
        EntityType.Builder.of(Predalien::new, MobCategory.MONSTER)
            .sized(PREDALIEN_WIDTH, PREDALIEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Prowler>> IRRADIATED_PROWLER = create(
        "irradiated_prowler",
        EntityType.Builder.of(Prowler::new, MobCategory.MONSTER)
            .sized(PROWLER_WIDTH, PROWLER_HEIGHT)
    );

    /**
     * The irradiated line has NO chestburster and NO adolescent, deliberately: an irradiated hive does not breed
     * through hosts. Its egg and its hugger are ORDNANCE - the egg detonates, and the hugger detonates on the face it
     * reaches instead of implanting anything. See IrradiatedDetonation.
     */
    /**
     * The spitter the previous pass forgot. Every other caste had an irradiated form; this one fell through and left
     * Spitter.getType returning null for the strain, so irradiated hives could never field one.
     * <p>
     * Nothing special is needed for its behaviour: its acid already reads the strain. An irradiated spitter's blood is
     * FREEZING blood, and AcidBlockDamageUtil already branches on {@code acid.isIrradiated()} to turn what it destroys
     * into blue ice (or netherrack over nether resin). Same geo, irradiated texture, and the spit inherits the rest.
     */
    public static final BLibHolder<EntityType<Spitter>> IRRADIATED_SPITTER = create(
        "irradiated_spitter",
        EntityType.Builder.of(Spitter::new, MobCategory.MONSTER)
            .sized(SPITTER_WIDTH, SPITTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> IRRADIATED_FACEHUGGER = create(
        "irradiated_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> IRRADIATED_OVOMORPH = create(
        "irradiated_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Harbinger>> IRRADIATED_HARBINGER = create(
        "irradiated_harbinger",
        EntityType.Builder.of(Harbinger::new, MobCategory.MONSTER)
            .sized(HARBINGER_WIDTH, HARBINGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<RazorClaw>> IRRADIATED_RAZOR_CLAW = create(
        "irradiated_razor_claw",
        EntityType.Builder.of(RazorClaw::new, MobCategory.MONSTER)
            .sized(RAZOR_CLAW_WIDTH, RAZOR_CLAW_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ravager>> IRRADIATED_RAVAGER = create(
        "irradiated_ravager",
        EntityType.Builder.of(Ravager::new, MobCategory.MONSTER)
            .sized(RAVAGER_WIDTH, RAVAGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Queen>> IRRADIATED_QUEEN = create(
        "irradiated_queen",
        EntityType.Builder.of(Queen::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Empress>> IRRADIATED_EMPRESS = create(
        "irradiated_empress",
        EntityType.Builder.of(Empress::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Burster>> IRRADIATED_BURSTER = create(
        "irradiated_burster",
        EntityType.Builder.of(Burster::new, MobCategory.MONSTER)
            .sized(BURSTER_WIDTH, BURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Runner>> IRRADIATED_RUNNER = create(
        "irradiated_runner",
        EntityType.Builder.of(Runner::new, MobCategory.MONSTER)
            .sized(RUNNER_WIDTH, RUNNER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Warrior>> IRRADIATED_WARRIOR = create(
        "irradiated_warrior",
        EntityType.Builder.of(Warrior::new, MobCategory.MONSTER)
            .sized(WARRIOR_WIDTH, WARRIOR_HEIGHT)
    );

    public static final BLibHolder<EntityType<Adolescent>> NETHER_ADOLESCENT = create(
        "nether_adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Boiler>> NETHER_BOILER = create(
        "nether_boiler",
        EntityType.Builder.of(Boiler::new, MobCategory.MONSTER)
            .sized(BOILER_WIDTH, BOILER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> NETHER_CHESTBURSTER = create(
        "nether_chestburster",
        EntityType.Builder.of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Carrier>> NETHER_CARRIER = create(
        "nether_carrier",
        EntityType.Builder.of(Carrier::new, MobCategory.MONSTER)
            .sized(CARRIER_WIDTH, CARRIER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chrysalis>> NETHER_CHRYSALIS = create(
        "nether_chrysalis",
        EntityType.Builder.of(Chrysalis::new, MobCategory.MONSTER)
            .sized(CHRYSALIS_WIDTH, CHRYSALIS_HEIGHT)
    );

    public static final BLibHolder<EntityType<Crusher>> NETHER_CRUSHER = create(
        "nether_crusher",
        EntityType.Builder.of(Crusher::new, MobCategory.MONSTER)
            .sized(CRUSHER_WIDTH, CRUSHER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Drone>> NETHER_DRONE = create(
        "nether_drone",
        EntityType.Builder.of(Drone::new, MobCategory.MONSTER)
            .sized(DRONE_WIDTH, DRONE_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> NETHER_FACEHUGGER = create(
        "nether_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Harbinger>> NETHER_HARBINGER = create(
        "nether_harbinger",
        EntityType.Builder.of(Harbinger::new, MobCategory.MONSTER)
            .sized(HARBINGER_WIDTH, HARBINGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> NETHER_OVOMORPH = create(
        "nether_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Praetorian>> NETHER_PRAETORIAN = create(
        "nether_praetorian",
        EntityType.Builder.of(Praetorian::new, MobCategory.MONSTER)
            .sized(PRAETORIAN_WIDTH, PRAETORIAN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Predalien>> NETHER_PREDALIEN = create(
        "nether_predalien",
        EntityType.Builder.of(Predalien::new, MobCategory.MONSTER)
            .sized(PREDALIEN_WIDTH, PREDALIEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienAdolescent>> NETHER_PREDALIEN_ADOLESCENT = create(
        "nether_predalien_adolescent",
        EntityType.Builder.of(PredalienAdolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienChestburster>> NETHER_PREDALIEN_CHESTBURSTER = create(
        "nether_predalien_chestburster",
        EntityType.Builder.of(PredalienChestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Prowler>> NETHER_PROWLER = create(
        "nether_prowler",
        EntityType.Builder.of(Prowler::new, MobCategory.MONSTER)
            .sized(PROWLER_WIDTH, PROWLER_HEIGHT)
    );

    public static final BLibHolder<EntityType<RazorClaw>> NETHER_RAZOR_CLAW = create(
        "nether_razor_claw",
        EntityType.Builder.of(RazorClaw::new, MobCategory.MONSTER)
            .sized(RAZOR_CLAW_WIDTH, RAZOR_CLAW_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ravager>> NETHER_RAVAGER = create(
        "nether_ravager",
        EntityType.Builder.of(Ravager::new, MobCategory.MONSTER)
            .sized(RAVAGER_WIDTH, RAVAGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Queen>> NETHER_QUEEN = create(
        "nether_queen",
        EntityType.Builder.of(Queen::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Empress>> NETHER_EMPRESS = create(
        "nether_empress",
        EntityType.Builder.of(Empress::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Burster>> NETHER_BURSTER = create(
        "nether_burster",
        EntityType.Builder.of(Burster::new, MobCategory.MONSTER)
            .sized(BURSTER_WIDTH, BURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Runner>> NETHER_RUNNER = create(
        "nether_runner",
        EntityType.Builder.of(Runner::new, MobCategory.MONSTER)
            .sized(RUNNER_WIDTH, RUNNER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Spitter>> NETHER_SPITTER = create(
        "nether_spitter",
        EntityType.Builder.of(Spitter::new, MobCategory.MONSTER)
            .sized(SPITTER_WIDTH, SPITTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Warrior>> NETHER_WARRIOR = create(
        "nether_warrior",
        EntityType.Builder.of(Warrior::new, MobCategory.MONSTER)
            .sized(WARRIOR_WIDTH, WARRIOR_HEIGHT)
    );

    /**
     * Sized from the GEO, not guessed: the empress model measures 1.29x wider and 1.14x taller than the queen's, so her
     * hitbox takes the queen ovipositor's 5.0 x 3.25 scaled by the same ratios. Both stay far smaller than the visual
     * model, which is deliberate and inherited - a 14-block-wide collision box would be unplayable.
     */
    public static final float EMPRESS_OVIPOSITOR_WIDTH = 6.5F;

    public static final float EMPRESS_OVIPOSITOR_HEIGHT = 3.7F;

    public static final BLibHolder<EntityType<EmpressOvipositor>> EMPRESS_OVIPOSITOR = create(
        "empress_ovipositor",
        EntityType.Builder.of(EmpressOvipositor::new, MobCategory.MONSTER)
            .sized(EMPRESS_OVIPOSITOR_WIDTH, EMPRESS_OVIPOSITOR_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovipositor>> OVIPOSITOR = create(
        "ovipositor",
        EntityType.Builder.of(Ovipositor::new, MobCategory.MONSTER)
            .sized(OVIPOSITOR_WIDTH, OVIPOSITOR_HEIGHT)
    );

    public static final BLibHolder<EntityType<RoyalCocoon>> ROYAL_COCOON = create(
        "royal_cocoon",
        EntityType.Builder.of(RoyalCocoon::new, MobCategory.MONSTER)
            .sized(ROYAL_COCOON_WIDTH, ROYAL_COCOON_HEIGHT)
    );

    public static final BLibHolder<EntityType<RoyalCocoon>> ABERRANT_ROYAL_COCOON = create(
        "aberrant_royal_cocoon",
        EntityType.Builder.of(RoyalCocoon::new, MobCategory.MONSTER)
            .sized(ROYAL_COCOON_WIDTH, ROYAL_COCOON_HEIGHT)
    );

    public static final BLibHolder<EntityType<RoyalCocoon>> NETHER_ROYAL_COCOON = create(
        "nether_royal_cocoon",
        EntityType.Builder.of(RoyalCocoon::new, MobCategory.MONSTER)
            .sized(ROYAL_COCOON_WIDTH, ROYAL_COCOON_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> OVOMORPH = create(
        "ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Praetorian>> PRAETORIAN = create(
        "praetorian",
        EntityType.Builder.of(Praetorian::new, MobCategory.MONSTER)
            .sized(PRAETORIAN_WIDTH, PRAETORIAN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Predalien>> PREDALIEN = create(
        "predalien",
        EntityType.Builder.of(Predalien::new, MobCategory.MONSTER)
            .sized(PREDALIEN_WIDTH, PREDALIEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienAdolescent>> PREDALIEN_ADOLESCENT = create(
        "predalien_adolescent",
        EntityType.Builder.of(PredalienAdolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<PredalienChestburster>> PREDALIEN_CHESTBURSTER = create(
        "predalien_chestburster",
        EntityType.Builder.of(PredalienChestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Prowler>> PROWLER = create(
        "prowler",
        EntityType.Builder.of(Prowler::new, MobCategory.MONSTER)
            .sized(PROWLER_WIDTH, PROWLER_HEIGHT)
    );

    public static final BLibHolder<EntityType<RazorClaw>> RAZOR_CLAW = create(
        "razor_claw",
        EntityType.Builder.of(RazorClaw::new, MobCategory.MONSTER)
            .sized(RAZOR_CLAW_WIDTH, RAZOR_CLAW_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ravager>> RAVAGER = create(
        "ravager",
        EntityType.Builder.of(Ravager::new, MobCategory.MONSTER)
            .sized(RAVAGER_WIDTH, RAVAGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Queen>> QUEEN = create(
        "queen",
        EntityType.Builder.of(Queen::new, MobCategory.MONSTER)
            .sized(QUEEN_WIDTH, QUEEN_HEIGHT)
    );

    public static final BLibHolder<EntityType<Adolescent>> ROYAL_ABERRANT_ADOLESCENT = create(
        "royal_aberrant_adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> ROYAL_ABERRANT_CHESTBURSTER = create(
        "royal_aberrant_chestburster",
        EntityType.Builder.of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> ROYAL_ABERRANT_FACEHUGGER = create(
        "royal_aberrant_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> ROYAL_ABERRANT_OVOMORPH = create(
        "royal_aberrant_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Adolescent>> ROYAL_ADOLESCENT = create(
        "royal_adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> ROYAL_CHESTBURSTER = create(
        "royal_chestburster",
        EntityType.Builder.<Chestburster>of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> ROYAL_FACEHUGGER = create(
        "royal_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> ROYAL_OVOMORPH = create(
        "royal_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Adolescent>> ROYAL_NETHER_ADOLESCENT = create(
        "royal_nether_adolescent",
        EntityType.Builder.of(Adolescent::new, MobCategory.MONSTER)
            .sized(ADOLESCENT_WIDTH, ADOLESCENT_HEIGHT)
    );

    public static final BLibHolder<EntityType<Chestburster>> ROYAL_NETHER_CHESTBURSTER = create(
        "royal_nether_chestburster",
        EntityType.Builder.of(Chestburster::new, MobCategory.MONSTER)
            .sized(CHESTBURSTER_WIDTH, CHESTBURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Facehugger>> ROYAL_NETHER_FACEHUGGER = create(
        "royal_nether_facehugger",
        EntityType.Builder.of(Facehugger::new, MobCategory.MONSTER)
            .sized(FACEHUGGER_WIDTH, FACEHUGGER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Ovomorph>> ROYAL_NETHER_OVOMORPH = create(
        "royal_nether_ovomorph",
        EntityType.Builder.of(Ovomorph::new, MobCategory.MISC)
            .sized(OVOMORPH_WIDTH, OVOMORPH_HEIGHT)
    );

    public static final BLibHolder<EntityType<Burster>> BURSTER = create(
        "burster",
        EntityType.Builder.of(Burster::new, MobCategory.MONSTER)
            .sized(BURSTER_WIDTH, BURSTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Runner>> RUNNER = create(
        "runner",
        EntityType.Builder.of(Runner::new, MobCategory.MONSTER)
            .sized(RUNNER_WIDTH, RUNNER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Spitter>> SPITTER = create(
        "spitter",
        EntityType.Builder.of(Spitter::new, MobCategory.MONSTER)
            .sized(SPITTER_WIDTH, SPITTER_HEIGHT)
    );

    public static final BLibHolder<EntityType<Warrior>> WARRIOR = create(
        "warrior",
        EntityType.Builder.of(Warrior::new, MobCategory.MONSTER)
            .sized(WARRIOR_WIDTH, WARRIOR_HEIGHT)
    );

    public static <T extends Entity> BLibHolder<EntityType<T>> create(String path, EntityType.Builder<T> builder) {
        return TYPE_REGISTRY.createHolder(
            path,
            () -> ((SilencedEntityTypeBuilder) builder).blib$buildWithoutDataFixerCheck()
        );
    }

    public static void initialize() {
        TYPE_REGISTRY.registerAll();
        ATTRIBUTE_REGISTRY.register(ABERRANT_ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_BOILER, Boiler::createBoilerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_CARRIER, Carrier::createCarrierAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_CHRYSALIS, Chrysalis::createChrysalisAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_CRUSHER, Crusher::createCrusherAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_DRONE, Drone::createDroneAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_HARBINGER, Harbinger::createHarbingerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_PRAETORIAN, Praetorian::createPraetorianAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_PREDALIEN, Predalien::createPredalienAttributes);
        ATTRIBUTE_REGISTRY.register(
            ABERRANT_PREDALIEN_ADOLESCENT,
            PredalienAdolescent::createPredalienAdolescentAttributes
        );
        ATTRIBUTE_REGISTRY.register(
            ABERRANT_PREDALIEN_CHESTBURSTER,
            PredalienChestburster::createPredalienChestbursterAttributes
        );
        ATTRIBUTE_REGISTRY.register(ABERRANT_PROWLER, Prowler::createProwlerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_RAZOR_CLAW, RazorClaw::createRazorClawAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_RAVAGER, Ravager::createRavagerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_QUEEN, Queen::createQueenAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_EMPRESS, Empress::createEmpressAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_BURSTER, Burster::createBursterAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_RUNNER, Runner::createRunnerAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_SPITTER, Spitter::createSpitterAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_WARRIOR, Warrior::createWarriorAttributes);
        ATTRIBUTE_REGISTRY.register(CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(CARRIER, Carrier::createCarrierAttributes);
        ATTRIBUTE_REGISTRY.register(CHRYSALIS, Chrysalis::createChrysalisAttributes);
        ATTRIBUTE_REGISTRY.register(ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(BOILER, Boiler::createBoilerAttributes);
        ATTRIBUTE_REGISTRY.register(CRUSHER, Crusher::createCrusherAttributes);
        ATTRIBUTE_REGISTRY.register(DRONE, Drone::createDroneAttributes);
        ATTRIBUTE_REGISTRY.register(EMPRESS, Empress::createEmpressAttributes);
        ATTRIBUTE_REGISTRY.register(HARBINGER, Harbinger::createHarbingerAttributes);
        ATTRIBUTE_REGISTRY.register(FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_CARRIER, Carrier::createCarrierAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_CHRYSALIS, Chrysalis::createChrysalisAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_CRUSHER, Crusher::createCrusherAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_DRONE, Drone::createDroneAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_PRAETORIAN, Praetorian::createPraetorianAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_PREDALIEN, Predalien::createPredalienAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_PROWLER, Prowler::createProwlerAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_HARBINGER, Harbinger::createHarbingerAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_RAZOR_CLAW, RazorClaw::createRazorClawAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_RAVAGER, Ravager::createRavagerAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_QUEEN, Queen::createQueenAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_EMPRESS, Empress::createEmpressAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_BURSTER, Burster::createBursterAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_RUNNER, Runner::createRunnerAttributes);
        ATTRIBUTE_REGISTRY.register(IRRADIATED_WARRIOR, Warrior::createWarriorAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_BOILER, Boiler::createBoilerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_CARRIER, Carrier::createCarrierAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_CHRYSALIS, Chrysalis::createChrysalisAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_CRUSHER, Crusher::createCrusherAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_DRONE, Drone::createDroneAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_HARBINGER, Harbinger::createHarbingerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_PRAETORIAN, Praetorian::createPraetorianAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_PREDALIEN, Predalien::createPredalienAttributes);
        ATTRIBUTE_REGISTRY.register(
            NETHER_PREDALIEN_ADOLESCENT,
            PredalienAdolescent::createPredalienAdolescentAttributes
        );
        ATTRIBUTE_REGISTRY.register(
            NETHER_PREDALIEN_CHESTBURSTER,
            PredalienChestburster::createPredalienChestbursterAttributes
        );
        ATTRIBUTE_REGISTRY.register(NETHER_PROWLER, Prowler::createProwlerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_RAZOR_CLAW, RazorClaw::createRazorClawAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_RAVAGER, Ravager::createRavagerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_QUEEN, Queen::createQueenAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_EMPRESS, Empress::createEmpressAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_BURSTER, Burster::createBursterAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_RUNNER, Runner::createRunnerAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_SPITTER, Spitter::createSpitterAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_WARRIOR, Warrior::createWarriorAttributes);
        ATTRIBUTE_REGISTRY.register(OVIPOSITOR, Ovipositor::createOvipositorAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_COCOON, RoyalCocoon::createRoyalCocoonAttributes);
        ATTRIBUTE_REGISTRY.register(ABERRANT_ROYAL_COCOON, RoyalCocoon::createRoyalCocoonAttributes);
        ATTRIBUTE_REGISTRY.register(NETHER_ROYAL_COCOON, RoyalCocoon::createRoyalCocoonAttributes);
        ATTRIBUTE_REGISTRY.register(OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(PRAETORIAN, Praetorian::createPraetorianAttributes);
        ATTRIBUTE_REGISTRY.register(PREDALIEN, Predalien::createPredalienAttributes);
        ATTRIBUTE_REGISTRY.register(PREDALIEN_ADOLESCENT, PredalienAdolescent::createPredalienAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(
            PREDALIEN_CHESTBURSTER,
            PredalienChestburster::createPredalienChestbursterAttributes
        );
        ATTRIBUTE_REGISTRY.register(PROWLER, Prowler::createProwlerAttributes);
        ATTRIBUTE_REGISTRY.register(RAZOR_CLAW, RazorClaw::createRazorClawAttributes);
        ATTRIBUTE_REGISTRY.register(RAVAGER, Ravager::createRavagerAttributes);
        ATTRIBUTE_REGISTRY.register(QUEEN, Queen::createQueenAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_ABERRANT_ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_ABERRANT_CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_ABERRANT_FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_ABERRANT_OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_NETHER_ADOLESCENT, Adolescent::createAdolescentAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_NETHER_CHESTBURSTER, Chestburster::createChestbursterAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_NETHER_FACEHUGGER, Facehugger::createFacehuggerAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_NETHER_OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(ROYAL_OVOMORPH, Ovomorph::createOvomorphAttributes);
        ATTRIBUTE_REGISTRY.register(BURSTER, Burster::createBursterAttributes);
        ATTRIBUTE_REGISTRY.register(RUNNER, Runner::createRunnerAttributes);
        ATTRIBUTE_REGISTRY.register(SPITTER, Spitter::createSpitterAttributes);
        ATTRIBUTE_REGISTRY.register(WARRIOR, Warrior::createWarriorAttributes);
    }
}
