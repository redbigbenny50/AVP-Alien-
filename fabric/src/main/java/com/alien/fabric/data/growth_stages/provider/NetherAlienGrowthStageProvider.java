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

public class NetherAlienGrowthStageProvider {

    public static void provide(BiConsumer<String, GrowthStage> biConsumer) {
        provideBaseNetherGrowthStages(biConsumer);
        provideRunnerNetherGrowthStages(biConsumer);
        provideNetherMetamorphosisStages(biConsumer);
        provideNetherScourgeStages(biConsumer);

        biConsumer.accept(
            "royal_nether_chestburster_to_royal_nether_adolescent",
            new GrowthStage(
                AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                GrowthConstants.ROYAL_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "royal_nether_adolescent_to_nether_praetorian",
            new GrowthStage(
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_PRAETORIAN.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );

        biConsumer.accept(
            "royal_nether_adolescent_to_nether_crusher",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_CRUSHER.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "nether_adolescent_to_nether_spitter",
            new GrowthStage(
                List.of(EntityType.LLAMA, EntityType.TRADER_LLAMA),
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_SPITTER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "nether_predalien_chestburster_to_nether_predalien_adolescent",
            new GrowthStage(
                AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get(),
                GrowthConstants.PREDALIEN_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "nether_predalien_adolescent_to_nether_predalien",
            new GrowthStage(
                AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_PREDALIEN.get(),
                GrowthConstants.PREDALIEN_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideBaseNetherGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "nether_chestburster_to_nether_adolescent",
            new GrowthStage(
                AlienEntityTypes.NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                GrowthConstants.CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "nether_adolescent_to_nether_drone",
            new GrowthStage(
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_DRONE.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideRunnerNetherGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "nether_adolescent_to_nether_runner",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_RUNNER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );
    }

    private static void provideNetherMetamorphosisStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> metamorphosis = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getMetamorphosisHolder(), 0)
        );

        // Eggs are NO LONGER promoted by the Metamorphosis potion (splash or otherwise). Royalty is conferred by
        // feeding RAW ROYAL JELLY directly to an ovomorph - see Ovomorph.mobInteract. Deleting the stage rather
        // than un-gating it is deliberate: a requirement-less growth stage is treated as immediately matching, so
        // an un-gated stage would turn every egg royal on its own.
        biConsumer.accept(
            "nether_drone_to_nether_warrior",
            new GrowthStage(AlienEntityTypes.NETHER_DRONE.get(), AlienEntityTypes.NETHER_WARRIOR.get(), metamorphosis)
        );
        biConsumer.accept(
            "nether_warrior_to_nether_praetorian",
            new GrowthStage(AlienEntityTypes.NETHER_WARRIOR.get(), AlienEntityTypes.NETHER_PRAETORIAN.get(), metamorphosis)
        );
        biConsumer.accept(
            "nether_praetorian_to_nether_queen",
            new GrowthStage(AlienEntityTypes.NETHER_PRAETORIAN.get(), AlienEntityTypes.NETHER_QUEEN.get(), metamorphosis)
        );
        biConsumer.accept(
            "nether_runner_to_nether_prowler",
            new GrowthStage(AlienEntityTypes.NETHER_RUNNER.get(), AlienEntityTypes.NETHER_PROWLER.get(), metamorphosis)
        );
        biConsumer.accept(
            "nether_prowler_to_nether_crusher",
            new GrowthStage(AlienEntityTypes.NETHER_PROWLER.get(), AlienEntityTypes.NETHER_CRUSHER.get(), metamorphosis)
        );
        biConsumer.accept(
            "nether_crusher_to_nether_queen",
            new GrowthStage(AlienEntityTypes.NETHER_CRUSHER.get(), AlienEntityTypes.NETHER_QUEEN.get(), metamorphosis)
        );
    }

    private static void provideNetherScourgeStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> scourge = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getScourgeHolder(), 0)
        );

        // The drone is the one caste with two scourge futures. ONE stage names both: carrier by default, razor claw
        // if the molt is redirected by a second dose. This was two competing stages split by a Scourge II amplifier
        // gate, which made a stronger potion permanently mandatory and left the default at the mercy of resource-scan
        // order - see GrowthStage and ScourgeStatusEffect.
        biConsumer.accept(
            "nether_drone_to_nether_carrier",
            new GrowthStage(
                AlienEntityTypes.NETHER_DRONE.get(),
                AlienEntityTypes.NETHER_CARRIER.get(),
                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                scourge
            )
        );
        biConsumer.accept(
            "nether_runner_to_nether_burster",
            new GrowthStage(AlienEntityTypes.NETHER_RUNNER.get(), AlienEntityTypes.NETHER_BURSTER.get(), scourge)
        );
        biConsumer.accept(
            "nether_prowler_to_nether_chrysalis",
            new GrowthStage(AlienEntityTypes.NETHER_PROWLER.get(), AlienEntityTypes.NETHER_CHRYSALIS.get(), scourge)
        );
        biConsumer.accept(
            "nether_praetorian_to_nether_harbinger",
            new GrowthStage(AlienEntityTypes.NETHER_PRAETORIAN.get(), AlienEntityTypes.NETHER_HARBINGER.get(), scourge)
        );
        biConsumer.accept(
            "nether_warrior_to_nether_ravager",
            new GrowthStage(AlienEntityTypes.NETHER_WARRIOR.get(), AlienEntityTypes.NETHER_RAVAGER.get(), scourge)
        );
    }
}
