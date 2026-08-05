package com.alien.fabric.data.growth_stages.provider;

import com.alien.common.model.lifecycle.growth.GrowthRequirement;
import com.alien.common.model.lifecycle.growth.GrowthStage;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.fabric.data.growth_stages.GrowthConstants;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.function.BiConsumer;

public class AlienGrowthStageProvider {

    public static void provide(BiConsumer<String, GrowthStage> biConsumer) {
        provideBaseGrowthStages(biConsumer);
        provideRunnerGrowthStages(biConsumer);
        provideMetamorphosisStages(biConsumer);
        provideScourgeStages(biConsumer);

        biConsumer.accept(
            "royal_chestburster_to_royal_adolescent",
            new GrowthStage(
                AlienEntityTypes.ROYAL_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                GrowthConstants.ROYAL_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "royal_adolescent_to_praetorian",
            new GrowthStage(
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                AlienEntityTypes.PRAETORIAN.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );

        biConsumer.accept(
            "royal_adolescent_to_crusher",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                AlienEntityTypes.CRUSHER.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "adolescent_to_spitter",
            new GrowthStage(
                List.of(EntityType.LLAMA, EntityType.TRADER_LLAMA),
                AlienEntityTypes.ADOLESCENT.get(),
                AlienEntityTypes.SPITTER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "predalien_chestburster_to_predalien_adolescent",
            new GrowthStage(
                AlienEntityTypes.PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.PREDALIEN_ADOLESCENT.get(),
                GrowthConstants.PREDALIEN_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "predalien_adolescent_to_predalien",
            new GrowthStage(
                AlienEntityTypes.PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.PREDALIEN.get(),
                GrowthConstants.PREDALIEN_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideBaseGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "chestburster_to_adolescent",
            new GrowthStage(
                AlienEntityTypes.CHESTBURSTER.get(),
                AlienEntityTypes.ADOLESCENT.get(),
                GrowthConstants.CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "adolescent_to_drone",
            new GrowthStage(
                AlienEntityTypes.ADOLESCENT.get(),
                AlienEntityTypes.DRONE.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideRunnerGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "adolescent_to_runner",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.ADOLESCENT.get(),
                AlienEntityTypes.RUNNER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );
    }

    private static void provideMetamorphosisStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> metamorphosis = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getMetamorphosisHolder(), 0)
        );

        // Eggs are NO LONGER promoted by the Metamorphosis potion (splash or otherwise). Royalty is conferred by
        // feeding RAW ROYAL JELLY directly to an ovomorph - see Ovomorph.mobInteract. Deleting the stage rather
        // than un-gating it is deliberate: a requirement-less growth stage is treated as immediately matching, so
        // an un-gated stage would turn every egg royal on its own.
        biConsumer.accept(
            "drone_to_warrior",
            new GrowthStage(AlienEntityTypes.DRONE.get(), AlienEntityTypes.WARRIOR.get(), metamorphosis)
        );
        biConsumer.accept(
            "warrior_to_praetorian",
            new GrowthStage(AlienEntityTypes.WARRIOR.get(), AlienEntityTypes.PRAETORIAN.get(), metamorphosis)
        );
        biConsumer.accept(
            "praetorian_to_queen",
            new GrowthStage(AlienEntityTypes.PRAETORIAN.get(), AlienEntityTypes.QUEEN.get(), metamorphosis)
        );
        biConsumer.accept(
            "runner_to_prowler",
            new GrowthStage(AlienEntityTypes.RUNNER.get(), AlienEntityTypes.PROWLER.get(), metamorphosis)
        );
        biConsumer.accept(
            "prowler_to_crusher",
            new GrowthStage(AlienEntityTypes.PROWLER.get(), AlienEntityTypes.CRUSHER.get(), metamorphosis)
        );
        biConsumer.accept(
            "crusher_to_queen",
            new GrowthStage(AlienEntityTypes.CRUSHER.get(), AlienEntityTypes.QUEEN.get(), metamorphosis)
        );
    }

    private static void provideScourgeStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> scourge = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getScourgeHolder(), 0)
        );

        // The drone is the one caste with two scourge futures. ONE stage names both: carrier by default, razor claw
        // if the molt is redirected by a second dose. This was two competing stages split by a Scourge II amplifier
        // gate, which made a stronger potion permanently mandatory and left the default at the mercy of resource-scan
        // order - see GrowthStage and ScourgeStatusEffect.
        biConsumer.accept(
            "drone_to_carrier",
            new GrowthStage(
                AlienEntityTypes.DRONE.get(),
                AlienEntityTypes.CARRIER.get(),
                AlienEntityTypes.RAZOR_CLAW.get(),
                scourge
            )
        );
        biConsumer.accept(
            "runner_to_burster",
            new GrowthStage(AlienEntityTypes.RUNNER.get(), AlienEntityTypes.BURSTER.get(), scourge)
        );
        biConsumer.accept(
            "prowler_to_chrysalis",
            new GrowthStage(AlienEntityTypes.PROWLER.get(), AlienEntityTypes.CHRYSALIS.get(), scourge)
        );
        biConsumer.accept(
            "praetorian_to_harbinger",
            new GrowthStage(AlienEntityTypes.PRAETORIAN.get(), AlienEntityTypes.HARBINGER.get(), scourge)
        );
        biConsumer.accept(
            "warrior_to_ravager",
            new GrowthStage(AlienEntityTypes.WARRIOR.get(), AlienEntityTypes.RAVAGER.get(), scourge)
        );
    }
}
