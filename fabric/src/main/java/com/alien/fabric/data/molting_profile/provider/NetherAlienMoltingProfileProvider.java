package com.alien.fabric.data.molting_profile.provider;

import com.alien.common.model.lifecycle.growth.MoltingProfile;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.fabric.data.molting_profile.MoltingProfileConstants;

import java.util.function.BiConsumer;

public class NetherAlienMoltingProfileProvider {

    public static void provide(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
            "nether_chestburster",
            new MoltingProfile(
                AlienEntityTypes.NETHER_CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.CHESTBURSTER_PHASES
            )
        );
        biConsumer.accept(
            "royal_nether_chestburster",
            new MoltingProfile(
                AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.ROYAL_CHESTBURSTER_PHASES
            )
        );
        biConsumer.accept(
            "nether_predalien_chestburster",
            new MoltingProfile(
                AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER.get(),
                MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                MoltingProfileConstants.PREDALIEN_CHESTBURSTER_PHASES
            )
        );

        biConsumer.accept(
            "nether_adolescent",
            new MoltingProfile(
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.ADOLESCENT_PHASES
            )
        );
        biConsumer.accept(
            "royal_nether_adolescent",
            new MoltingProfile(
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.ROYAL_ADOLESCENT_PHASES
            )
        );
        biConsumer.accept(
            "nether_predalien_adolescent",
            new MoltingProfile(
                AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get(),
                MoltingProfileConstants.ADOLESCENT_START_SCALE,
                MoltingProfileConstants.ADOLESCENT_END_SCALE,
                MoltingProfileConstants.PREDALIEN_ADOLESCENT_PHASES
            )
        );

        biConsumer.accept(
            "nether_burster",
            new MoltingProfile(
                AlienEntityTypes.NETHER_BURSTER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );

        biConsumer.accept(
            "nether_drone",
            new MoltingProfile(
                AlienEntityTypes.NETHER_DRONE.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );

        biConsumer.accept(
            "nether_runner",
            new MoltingProfile(
                AlienEntityTypes.NETHER_RUNNER.get(),
                MoltingProfileConstants.DRONE_START_SCALE,
                MoltingProfileConstants.DRONE_END_SCALE,
                MoltingProfileConstants.DRONE_PHASES
            )
        );

        biConsumer.accept(
            "nether_carrier",
            new MoltingProfile(
                AlienEntityTypes.NETHER_CARRIER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_chrysalis",
            new MoltingProfile(
                AlienEntityTypes.NETHER_CHRYSALIS.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_predalien",
            new MoltingProfile(
                AlienEntityTypes.NETHER_PREDALIEN.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_praetorian",
            new MoltingProfile(
                AlienEntityTypes.NETHER_PRAETORIAN.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_ravager",
            new MoltingProfile(
                AlienEntityTypes.NETHER_RAVAGER.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_razor_claw",
            new MoltingProfile(
                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                MoltingProfileConstants.PRAETORIAN_START_SCALE,
                MoltingProfileConstants.PRAETORIAN_END_SCALE,
                MoltingProfileConstants.PRAETORIAN_PHASES
            )
        );

        biConsumer.accept(
            "nether_queen",
            new MoltingProfile(
                AlienEntityTypes.NETHER_QUEEN.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
        biConsumer.accept(
            "nether_empress",
            new MoltingProfile(
                AlienEntityTypes.NETHER_EMPRESS.get(),
                MoltingProfileConstants.QUEEN_START_SCALE,
                MoltingProfileConstants.QUEEN_END_SCALE,
                MoltingProfileConstants.QUEEN_PHASES
            )
        );
    }
}
