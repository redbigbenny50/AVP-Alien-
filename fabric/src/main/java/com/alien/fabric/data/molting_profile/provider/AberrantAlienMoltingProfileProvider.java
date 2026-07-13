package com.alien.fabric.data.molting_profile.provider;

import com.alien.common.model.lifecycle.growth.MoltingProfile;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.fabric.data.molting_profile.MoltingProfileConstants;

import java.util.function.BiConsumer;

public class AberrantAlienMoltingProfileProvider {

    public static void provide(BiConsumer<String, MoltingProfile> biConsumer) {
        biConsumer.accept(
                "aberrant_chestburster",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_CHESTBURSTER.get(),
                        MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                        MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                        MoltingProfileConstants.CHESTBURSTER_PHASES
                )
        );
        biConsumer.accept(
                "royal_aberrant_chestburster",
                new MoltingProfile(
                        AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get(),
                        MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                        MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                        MoltingProfileConstants.ROYAL_CHESTBURSTER_PHASES
                )
        );
        biConsumer.accept(
                "aberrant_predalien_chestburster",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER.get(),
                        MoltingProfileConstants.CHESTBURSTER_START_SCALE,
                        MoltingProfileConstants.CHESTBURSTER_END_SCALE,
                        MoltingProfileConstants.PREDALIEN_CHESTBURSTER_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_adolescent",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                        MoltingProfileConstants.ADOLESCENT_START_SCALE,
                        MoltingProfileConstants.ADOLESCENT_END_SCALE,
                        MoltingProfileConstants.ADOLESCENT_PHASES
                )
        );
        biConsumer.accept(
                "royal_aberrant_adolescent",
                new MoltingProfile(
                        AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                        MoltingProfileConstants.ADOLESCENT_START_SCALE,
                        MoltingProfileConstants.ADOLESCENT_END_SCALE,
                        MoltingProfileConstants.ROYAL_ADOLESCENT_PHASES
                )
        );
        biConsumer.accept(
                "aberrant_predalien_adolescent",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get(),
                        MoltingProfileConstants.ADOLESCENT_START_SCALE,
                        MoltingProfileConstants.ADOLESCENT_END_SCALE,
                        MoltingProfileConstants.PREDALIEN_ADOLESCENT_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_burster",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_BURSTER.get(),
                        MoltingProfileConstants.DRONE_START_SCALE,
                        MoltingProfileConstants.DRONE_END_SCALE,
                        MoltingProfileConstants.DRONE_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_drone",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_DRONE.get(),
                        MoltingProfileConstants.DRONE_START_SCALE,
                        MoltingProfileConstants.DRONE_END_SCALE,
                        MoltingProfileConstants.DRONE_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_runner",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_RUNNER.get(),
                        MoltingProfileConstants.DRONE_START_SCALE,
                        MoltingProfileConstants.DRONE_END_SCALE,
                        MoltingProfileConstants.DRONE_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_carrier",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_CARRIER.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_chrysalis",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_CHRYSALIS.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_predalien",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_PREDALIEN.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_praetorian",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_PRAETORIAN.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_ravager",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_RAVAGER.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_razor_claw",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                        MoltingProfileConstants.PRAETORIAN_START_SCALE,
                        MoltingProfileConstants.PRAETORIAN_END_SCALE,
                        MoltingProfileConstants.PRAETORIAN_PHASES
                )
        );

        biConsumer.accept(
                "aberrant_queen",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_QUEEN.get(),
                        MoltingProfileConstants.QUEEN_START_SCALE,
                        MoltingProfileConstants.QUEEN_END_SCALE,
                        MoltingProfileConstants.QUEEN_PHASES
                )
        );
        biConsumer.accept(
                "aberrant_empress",
                new MoltingProfile(
                        AlienEntityTypes.ABERRANT_EMPRESS.get(),
                        MoltingProfileConstants.QUEEN_START_SCALE,
                        MoltingProfileConstants.QUEEN_END_SCALE,
                        MoltingProfileConstants.QUEEN_PHASES
                )
        );
    }
}