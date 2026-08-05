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

public class AberrantAlienGrowthStageProvider {

    public static void provide(BiConsumer<String, GrowthStage> biConsumer) {
        provideBaseAberrantGrowthStages(biConsumer);
        provideRunnerAberrantGrowthStages(biConsumer);
        provideAberrantMetamorphosisStages(biConsumer);
        provideAberrantScourgeStages(biConsumer);

        biConsumer.accept(
            "royal_aberrant_chestburster_to_royal_aberrant_adolescent",
            new GrowthStage(
                AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                GrowthConstants.ROYAL_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "royal_aberrant_adolescent_to_aberrant_praetorian",
            new GrowthStage(
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_PRAETORIAN.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );

        biConsumer.accept(
            "royal_aberrant_adolescent_to_aberrant_crusher",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_CRUSHER.get(),
                GrowthConstants.ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "aberrant_adolescent_to_aberrant_spitter",
            new GrowthStage(
                List.of(EntityType.LLAMA, EntityType.TRADER_LLAMA),
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_SPITTER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );

        biConsumer.accept(
            "aberrant_predalien_chestburster_to_aberrant_predalien_adolescent",
            new GrowthStage(
                AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get(),
                GrowthConstants.PREDALIEN_CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "aberrant_predalien_adolescent_to_aberrant_predalien",
            new GrowthStage(
                AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN.get(),
                GrowthConstants.PREDALIEN_ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideBaseAberrantGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "aberrant_chestburster_to_aberrant_adolescent",
            new GrowthStage(
                AlienEntityTypes.ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                GrowthConstants.CHESTBURSTER_GROWTH_TIME_IN_TICKS
            )
        );
        biConsumer.accept(
            "aberrant_adolescent_to_aberrant_drone",
            new GrowthStage(
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_DRONE.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS
            )
        );
    }

    private static void provideRunnerAberrantGrowthStages(BiConsumer<String, GrowthStage> biConsumer) {
        biConsumer.accept(
            "aberrant_adolescent_to_aberrant_runner",
            new GrowthStage(
                AlienEntityTypeTags.RUNNER_HOSTS,
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_RUNNER.get(),
                GrowthConstants.ADOLESCENT_GROWTH_TIME_IN_TICKS / 2
            )
        );
    }

    private static void provideAberrantMetamorphosisStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> metamorphosis = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getMetamorphosisHolder(), 0)
        );

        // Eggs are NO LONGER promoted by the Metamorphosis potion (splash or otherwise). Royalty is conferred by
        // feeding RAW ROYAL JELLY directly to an ovomorph - see Ovomorph.mobInteract. Deleting the stage rather
        // than un-gating it is deliberate: a requirement-less growth stage is treated as immediately matching, so
        // an un-gated stage would turn every egg royal on its own.
        biConsumer.accept(
            "aberrant_drone_to_aberrant_warrior",
            new GrowthStage(AlienEntityTypes.ABERRANT_DRONE.get(), AlienEntityTypes.ABERRANT_WARRIOR.get(), metamorphosis)
        );
        biConsumer.accept(
            "aberrant_warrior_to_aberrant_praetorian",
            new GrowthStage(AlienEntityTypes.ABERRANT_WARRIOR.get(), AlienEntityTypes.ABERRANT_PRAETORIAN.get(), metamorphosis)
        );
        biConsumer.accept(
            "aberrant_praetorian_to_aberrant_queen",
            new GrowthStage(AlienEntityTypes.ABERRANT_PRAETORIAN.get(), AlienEntityTypes.ABERRANT_QUEEN.get(), metamorphosis)
        );
        biConsumer.accept(
            "aberrant_runner_to_aberrant_prowler",
            new GrowthStage(AlienEntityTypes.ABERRANT_RUNNER.get(), AlienEntityTypes.ABERRANT_PROWLER.get(), metamorphosis)
        );
        biConsumer.accept(
            "aberrant_prowler_to_aberrant_crusher",
            new GrowthStage(AlienEntityTypes.ABERRANT_PROWLER.get(), AlienEntityTypes.ABERRANT_CRUSHER.get(), metamorphosis)
        );
        biConsumer.accept(
            "aberrant_crusher_to_aberrant_queen",
            new GrowthStage(AlienEntityTypes.ABERRANT_CRUSHER.get(), AlienEntityTypes.ABERRANT_QUEEN.get(), metamorphosis)
        );
    }

    private static void provideAberrantScourgeStages(BiConsumer<String, GrowthStage> biConsumer) {
        List<GrowthRequirement> scourge = List.of(
            new GrowthRequirement.MobEffectRequirement(AlienMobEffects.getScourgeHolder(), 0)
        );

        // The drone is the one caste with two scourge futures. ONE stage names both: carrier by default, razor claw
        // if the molt is redirected by a second dose. This was two competing stages split by a Scourge II amplifier
        // gate, which made a stronger potion permanently mandatory and left the default at the mercy of resource-scan
        // order - see GrowthStage and ScourgeStatusEffect.
        biConsumer.accept(
            "aberrant_drone_to_aberrant_carrier",
            new GrowthStage(
                AlienEntityTypes.ABERRANT_DRONE.get(),
                AlienEntityTypes.ABERRANT_CARRIER.get(),
                AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                scourge
            )
        );
        biConsumer.accept(
            "aberrant_runner_to_aberrant_burster",
            new GrowthStage(AlienEntityTypes.ABERRANT_RUNNER.get(), AlienEntityTypes.ABERRANT_BURSTER.get(), scourge)
        );
        biConsumer.accept(
            "aberrant_prowler_to_aberrant_chrysalis",
            new GrowthStage(AlienEntityTypes.ABERRANT_PROWLER.get(), AlienEntityTypes.ABERRANT_CHRYSALIS.get(), scourge)
        );
        biConsumer.accept(
            "aberrant_praetorian_to_aberrant_harbinger",
            new GrowthStage(AlienEntityTypes.ABERRANT_PRAETORIAN.get(), AlienEntityTypes.ABERRANT_HARBINGER.get(), scourge)
        );
        biConsumer.accept(
            "aberrant_warrior_to_aberrant_ravager",
            new GrowthStage(AlienEntityTypes.ABERRANT_WARRIOR.get(), AlienEntityTypes.ABERRANT_RAVAGER.get(), scourge)
        );
    }
}
