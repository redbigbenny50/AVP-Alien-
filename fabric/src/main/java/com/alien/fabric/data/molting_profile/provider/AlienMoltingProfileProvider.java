package com.alien.fabric.data.molting_profile.provider;

import com.alien.common.model.lifecycle.growth.MoltingProfile;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.fabric.data.molting_profile.MoltingProfileConstants;

import java.util.function.BiConsumer;

public class AlienMoltingProfileProvider {

    public static void provide(BiConsumer<String, MoltingProfile> biConsumer) {
        provideChestbursterProfiles(biConsumer);
        provideAdolescentProfiles(biConsumer);
        provideBursterProfiles(biConsumer);
        provideDroneProfiles(biConsumer);
        provideRunnerProfiles(biConsumer);
        provideCarrierProfiles(biConsumer);
        provideChrysalisProfiles(biConsumer);
        providePredalienProfiles(biConsumer);
        providePraetorianProfiles(biConsumer);
        provideQueenProfiles(biConsumer);
        provideRavagerProfiles(biConsumer);
        provideRazorClawProfiles(biConsumer);
    }

    private static void provideChestbursterProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "chestburster",
            new MoltingProfile(
                AlienEntityTypes.CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.CHESTBURSTER_PHASES
            )
        );
        biConsumer.accept(
            "royal_chestburster",
            new MoltingProfile(
                AlienEntityTypes.ROYAL_CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.ROYAL_CHESTBURSTER_PHASES
            )
        );
        biConsumer.accept(
            "predalien_chestburster",
            new MoltingProfile(
                AlienEntityTypes.PREDALIEN_CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.PREDALIEN_CHESTBURSTER_PHASES
            )
        );
    }

    private static void provideAdolescentProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "adolescent",
            new MoltingProfile(
                AlienEntityTypes.ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.ADOLESCENT_PHASES
            )
        );
        biConsumer.accept(
            "royal_adolescent",
            new MoltingProfile(
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.ROYAL_ADOLESCENT_PHASES
            )
        );
        biConsumer.accept(
            "predalien_adolescent",
            new MoltingProfile(
                AlienEntityTypes.PREDALIEN_ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.PREDALIEN_ADOLESCENT_PHASES
            )
        );
    }

    private static void provideBursterProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "burster",
            new MoltingProfile(
                AlienEntityTypes.BURSTER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_burster",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_BURSTER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
    }

    private static void provideDroneProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "drone",
            new MoltingProfile(
                AlienEntityTypes.DRONE.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_drone",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_DRONE.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
    }

    private static void provideRunnerProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "runner",
            new MoltingProfile(
                AlienEntityTypes.RUNNER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_runner",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_RUNNER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );
    }

    private static void provideCarrierProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "carrier",
            new MoltingProfile(
                AlienEntityTypes.CARRIER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_carrier",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_CARRIER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void provideChrysalisProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "chrysalis",
            new MoltingProfile(
                AlienEntityTypes.CHRYSALIS.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_chrysalis",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_CHRYSALIS.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void providePredalienProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "predalien",
            new MoltingProfile(
                AlienEntityTypes.PREDALIEN.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void providePraetorianProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "praetorian",
            new MoltingProfile(
                AlienEntityTypes.PRAETORIAN.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_praetorian",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_PRAETORIAN.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void provideRavagerProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "ravager",
            new MoltingProfile(
                AlienEntityTypes.RAVAGER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_ravager",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_RAVAGER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void provideRazorClawProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "razor_claw",
            new MoltingProfile(
                AlienEntityTypes.RAZOR_CLAW.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_razor_claw",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_RAZOR_CLAW.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );
    }

    private static void provideQueenProfiles(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "queen",
            new MoltingProfile(
                AlienEntityTypes.QUEEN.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
        biConsumer.accept(
            "empress",
            new MoltingProfile(
                AlienEntityTypes.EMPRESS.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_queen",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_QUEEN.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
        biConsumer.accept(
            "irradiated_empress",
            new MoltingProfile(
                AlienEntityTypes.IRRADIATED_EMPRESS.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
    }
}
