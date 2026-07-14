package com.alien.fabric.data.raid_wave;

import com.alien.AlienResources;
import com.alien.common.data.RaidWaveProfileReloadListener;
import com.alien.common.gameplay.hive.convoy.RaidWaveProfile;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.RaidWaveProfileRegistry;
import com.alien.common.registry.init.AlienEntityTypes;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RaidWaveProfileDataProvider implements DataProvider {

    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final FabricDataOutput output;

    private final Map<ResourceLocation, RaidWaveProfile> profilesById = new LinkedHashMap<>();

    public RaidWaveProfileDataProvider(FabricDataOutput output) {
        this.output = output;
    }

    private void generate() {
        profilesById.put(AlienResources.location("default"), RaidWaveProfile.fallback());
        profilesById.put(
                RaidWaveProfileRegistry.profileIdFor(AlienVariant.NORMAL),
                profile(
                        new VariantRaidEntities(
                                AlienEntityTypes.WARRIOR.get(),
                                AlienEntityTypes.PROWLER.get(),
                                AlienEntityTypes.SPITTER.get(),
                                AlienEntityTypes.CHRYSALIS.get(),
                                AlienEntityTypes.RAZOR_CLAW.get(),
                                AlienEntityTypes.BURSTER.get(),
                                AlienEntityTypes.RAVAGER.get(),
                                AlienEntityTypes.CARRIER.get(),
                                AlienEntityTypes.HARBINGER.get()
                        )
                )
        );
        profilesById.put(
                RaidWaveProfileRegistry.profileIdFor(AlienVariant.NETHER),
                profile(
                        new VariantRaidEntities(
                                AlienEntityTypes.NETHER_WARRIOR.get(),
                                AlienEntityTypes.NETHER_PROWLER.get(),
                                AlienEntityTypes.NETHER_SPITTER.get(),
                                AlienEntityTypes.NETHER_CHRYSALIS.get(),
                                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                                AlienEntityTypes.NETHER_BURSTER.get(),
                                AlienEntityTypes.NETHER_RAVAGER.get(),
                                AlienEntityTypes.NETHER_CARRIER.get(),
                                AlienEntityTypes.NETHER_HARBINGER.get()
                        )
                )
        );
        profilesById.put(
                RaidWaveProfileRegistry.profileIdFor(AlienVariant.ABERRANT),
                profile(
                        new VariantRaidEntities(
                                AlienEntityTypes.ABERRANT_WARRIOR.get(),
                                AlienEntityTypes.ABERRANT_PROWLER.get(),
                                AlienEntityTypes.ABERRANT_SPITTER.get(),
                                AlienEntityTypes.ABERRANT_CHRYSALIS.get(),
                                AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                                AlienEntityTypes.ABERRANT_BURSTER.get(),
                                AlienEntityTypes.ABERRANT_RAVAGER.get(),
                                AlienEntityTypes.ABERRANT_CARRIER.get(),
                                AlienEntityTypes.ABERRANT_HARBINGER.get()
                        )
                )
        );
    }

    /**
     * The raid: five escalating waves (5 / 8 / 13 / 21 / 34).
     * <p>
     * Every pool is now CAPPED. Warriors and prowlers used to be the only uncapped entries, so they soaked up every
     * slot the scourge maxes left over - a full raid ate roughly half the hive's standing army in one go. Capped, a
     * complete raid costs about a third of it, and the hive keeps a garrison.
     * <p>
     * The consequence is deliberate: a hive with no harbinger has NO scourge castes, so its waves come in badly
     * under strength. The harbinger is what turns a raid from a nuisance into a siege - and it marches in wave 5,
     * so every raid risks the very thing that makes raiding possible.
     */
    private static RaidWaveProfile profile(VariantRaidEntities entities) {
        return new RaidWaveProfile(
                List.of(
                        // Wave 1 - the probe. Bursters lead from the very first wave.
                        wave(
                                5,
                                List.of(guaranteed(2, List.of(pool(entities.burster())))),
                                List.of(
                                        pool(entities.warrior(), 3, 2),
                                        pool(entities.prowler(), 2, 1)
                                )
                        ),
                        // Wave 2 - the first specialists arrive.
                        wave(
                                8,
                                List.of(
                                        guaranteed(1, List.of(pool(entities.chrysalis()))),
                                        guaranteed(1, List.of(pool(entities.razorClaw())))
                                ),
                                List.of(
                                        pool(entities.warrior(), 3, 2),
                                        pool(entities.prowler(), 3, 1),
                                        pool(entities.spitter(), 2, 1),
                                        pool(entities.burster(), 3, 2)
                                )
                        ),
                        // Wave 3 - the scourge takes over the bulk of the fighting.
                        wave(
                                13,
                                List.of(),
                                List.of(
                                        pool(entities.warrior(), 3, 3),
                                        pool(entities.prowler(), 3, 2),
                                        pool(entities.spitter(), 2, 2),
                                        pool(entities.burster(), 3, 3),
                                        pool(entities.chrysalis(), 2, 2),
                                        pool(entities.razorClaw(), 2, 1)
                                )
                        ),
                        // Wave 4 - the heavies.
                        wave(
                                21,
                                List.of(),
                                List.of(
                                        pool(entities.warrior(), 3, 4),
                                        pool(entities.prowler(), 3, 3),
                                        pool(entities.spitter(), 2, 2),
                                        pool(entities.burster(), 3, 4),
                                        pool(entities.chrysalis(), 2, 3),
                                        pool(entities.razorClaw(), 2, 3),
                                        pool(entities.ravager(), 1, 1),
                                        pool(entities.carrier(), 1, 1)
                                )
                        ),
                        // Wave 5 - everything, led by the harbinger itself.
                        wave(
                                34,
                                List.of(guaranteed(1, List.of(pool(entities.harbinger())))),
                                List.of(
                                        pool(entities.warrior(), 3, 5),
                                        pool(entities.prowler(), 3, 5),
                                        pool(entities.spitter(), 2, 4),
                                        pool(entities.burster(), 3, 6),
                                        pool(entities.chrysalis(), 2, 5),
                                        pool(entities.razorClaw(), 2, 4),
                                        pool(entities.ravager(), 1, 2),
                                        pool(entities.carrier(), 1, 2)
                                )
                        )
                )
        );
    }

    private static RaidWaveProfile.Wave wave(
            int size,
            List<RaidWaveProfile.Guarantee> guaranteed,
            List<RaidWaveProfile.PoolEntry> randomPools
    ) {
        return new RaidWaveProfile.Wave(
                size,
                RaidWaveProfile.DEFAULT_BUFFER_TICKS,
                guaranteed,
                randomPools
        );
    }

    private static RaidWaveProfile.Guarantee guaranteed(int count, List<RaidWaveProfile.PoolEntry> pools) {
        return new RaidWaveProfile.Guarantee(count, pools);
    }

    private static RaidWaveProfile.PoolEntry pool(EntityType<?> entityType) {
        return pool(entityType, 1);
    }

    private static RaidWaveProfile.PoolEntry pool(EntityType<?> entityType, int weight) {
        return pool(entityType, weight, UNLIMITED);
    }

    private static RaidWaveProfile.PoolEntry pool(EntityType<?> entityType, int weight, int maxCount) {
        return RaidWaveProfile.PoolEntry.entityPool(entityType, weight, maxCount);
    }

    @Override
    public final @NotNull CompletableFuture<?> run(CachedOutput cached) {
        generate();

        var pathProvider = output.createPathProvider(
                PackOutput.Target.DATA_PACK,
                RaidWaveProfileReloadListener.DIRECTORY_NAME
        );
        var futures = new ArrayList<CompletableFuture<?>>();

        for (var entry : profilesById.entrySet()) {
            var filePath = pathProvider.json(entry.getKey());
            var jsonElement = RaidWaveProfile.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
            futures.add(DataProvider.saveStable(cached, jsonElement, filePath));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public final @NotNull String getName() {
        return "Raid Wave Profiles";
    }

    private record VariantRaidEntities(
            EntityType<?> warrior,
            EntityType<?> prowler,
            EntityType<?> spitter,
            EntityType<?> chrysalis,
            EntityType<?> razorClaw,
            EntityType<?> burster,
            EntityType<?> ravager,
            EntityType<?> carrier,
            EntityType<?> harbinger
    ) {}
}