package com.alien.common.gameplay.hive.convoy;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

public record RaidWaveProfile(List<Wave> waves) {

    public static final int MIN_WAVE_SIZE = 5;

    /** Revenge/recovery waves are small and immediate: six bodies, three times. */
    public static final int REVENGE_WAVE_SIZE = 6;

    public static final long DEFAULT_BUFFER_TICKS = 20L * 10L;

    public static final Codec<RaidWaveProfile> CODEC = RecordCodecBuilder.<RaidWaveProfile>create(
            instance -> instance.group(
                    Wave.CODEC.listOf().fieldOf("waves").forGetter(RaidWaveProfile::waves)
            ).apply(instance, RaidWaveProfile::new)
    ).flatXmap(RaidWaveProfile::validate, RaidWaveProfile::validate);

    public RaidWaveProfile {
        waves = List.copyOf(waves);
    }

    public static RaidWaveProfile fallback() {
        return new RaidWaveProfile(
                List.of(
                        new Wave(
                                5,
                                DEFAULT_BUFFER_TICKS,
                                List.of(),
                                List.of(
                                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 2, Integer.MAX_VALUE)
                                )
                        ),
                        new Wave(
                                8,
                                DEFAULT_BUFFER_TICKS,
                                List.of(
                                        new Guarantee(
                                                1,
                                                List.of(
                                                        PoolEntry.tagPool(AlienEntityTypeTags.CHRYSALISES, 1, Integer.MAX_VALUE),
                                                        PoolEntry.tagPool(AlienEntityTypeTags.RAZOR_CLAWS, 1, Integer.MAX_VALUE)
                                                )
                                        )
                                ),
                                List.of(
                                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 4, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 3, Integer.MAX_VALUE)
                                )
                        ),
                        new Wave(
                                13,
                                DEFAULT_BUFFER_TICKS,
                                List.of(),
                                List.of(
                                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.CHRYSALISES, 2, 3),
                                        PoolEntry.tagPool(AlienEntityTypeTags.RAZOR_CLAWS, 2, 3),
                                        PoolEntry.tagPool(AlienEntityTypeTags.BURSTERS, 2, 4)
                                )
                        ),
                        new Wave(
                                21,
                                DEFAULT_BUFFER_TICKS,
                                List.of(),
                                List.of(
                                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.CHRYSALISES, 2, 4),
                                        PoolEntry.tagPool(AlienEntityTypeTags.RAZOR_CLAWS, 2, 4),
                                        PoolEntry.tagPool(AlienEntityTypeTags.BURSTERS, 2, 5),
                                        PoolEntry.tagPool(AlienEntityTypeTags.RAVAGERS, 1, 3),
                                        PoolEntry.tagPool(AlienEntityTypeTags.CARRIERS, 1, 3)
                                )
                        ),
                        new Wave(
                                34,
                                DEFAULT_BUFFER_TICKS,
                                List.of(
                                        new Guarantee(
                                                1,
                                                List.of(PoolEntry.tagPool(AlienEntityTypeTags.HARBINGERS, 1, Integer.MAX_VALUE))
                                        )
                                ),
                                List.of(
                                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 3, Integer.MAX_VALUE),
                                        PoolEntry.tagPool(AlienEntityTypeTags.CHRYSALISES, 2, 6),
                                        PoolEntry.tagPool(AlienEntityTypeTags.RAZOR_CLAWS, 2, 6),
                                        PoolEntry.tagPool(AlienEntityTypeTags.BURSTERS, 2, 8),
                                        PoolEntry.tagPool(AlienEntityTypeTags.RAVAGERS, 1, 4),
                                        PoolEntry.tagPool(AlienEntityTypeTags.CARRIERS, 1, 4)
                                )
                        )
                )
        );
    }

    public Wave wave(int index) {
        return waves.get(Math.clamp(index, 0, waves.size() - 1));
    }

    /**
     * A 3-wave code fallback for rescue raids (queen captured). Same shorter shape as {@link #revengeFallback} — a
     * focused strike to free the queen — so rescue works without a datapack profile present.
     */
    public static RaidWaveProfile rescueFallback() {
        return revengeFallback();
    }

    /**
     * A 3-wave code fallback for revenge raids (queen killed) — a shorter, sharper strike than the full 5-wave raid.
     * Mirrors {@link #fallback}'s shape so revenge works without a datapack profile present, matching how the normal
     * raid profile provides a built-in default.
     */
    /**
     * Revenge and recovery: the hive comes for you with its STANDING ARMY, not its raid tier.
     * <p>
     * Three waves, six bodies each. No scourge castes and no harbinger - those belong to a planned raid, which the
     * hive must build up to. Revenge is what it can field RIGHT NOW, with what it has: warriors, prowlers, spitters,
     * and the queen's guard (praetorians and crushers) turned outward for once. A hive that has just lost its queen
     * is not staging a campaign; it is lashing out.
     * <p>
     * Tag pools, so this one profile serves every strain - a nether hive sends nether warriors.
     */
    public static RaidWaveProfile revengeFallback() {
        return new RaidWaveProfile(
                List.of(
                        revengeWave(),
                        revengeWave(),
                        revengeWave()
                )
        );
    }

    /** One revenge wave: 6 bodies drawn from the standing army, elites rarer than the rank and file. */
    private static Wave revengeWave() {
        return new Wave(
                REVENGE_WAVE_SIZE,
                DEFAULT_BUFFER_TICKS,
                List.of(),
                List.of(
                        PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 4, REVENGE_WAVE_SIZE),
                        PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 4, REVENGE_WAVE_SIZE),
                        PoolEntry.tagPool(AlienEntityTypeTags.SPITTERS, 3, REVENGE_WAVE_SIZE),
                        PoolEntry.tagPool(AlienEntityTypeTags.PRAETORIANS, 1, 2),
                        PoolEntry.tagPool(AlienEntityTypeTags.CRUSHERS, 1, 2)
                )
        );
    }

    public int totalSize() {
        var total = 0;
        for (var wave : waves) {
            total += wave.size();
        }
        return total;
    }

    public boolean isRaidEligible(EntityType<?> entityType) {
        for (var wave : waves) {
            if (wave.matches(entityType)) {
                return true;
            }
        }
        return false;
    }

    private static DataResult<RaidWaveProfile> validate(RaidWaveProfile profile) {
        if (profile.waves().isEmpty() || profile.waves().size() > Convoy.Raid.WAVE_COUNT) {
            return DataResult.error(
                    () -> "Raid wave profile must define between 1 and " + Convoy.Raid.WAVE_COUNT + " waves"
            );
        }
        for (var i = 0; i < profile.waves().size(); i++) {
            var wave = profile.waves().get(i);
            var waveNumber = i + 1;
            if (wave.size() < MIN_WAVE_SIZE) {
                return DataResult.error(
                        () -> "Raid wave " + waveNumber + " must have at least " + MIN_WAVE_SIZE + " members"
                );
            }
            if (wave.bufferTicks() < 0L) {
                return DataResult.error(() -> "Raid wave " + waveNumber + " buffer_ticks cannot be negative");
            }
            if (wave.guaranteedSize() > wave.size()) {
                return DataResult.error(
                        () -> "Raid wave " + waveNumber + " guaranteed counts exceed the configured wave size"
                );
            }
            if (wave.pools().isEmpty() && wave.guaranteedSize() < wave.size()) {
                return DataResult.error(
                        () -> "Raid wave " + waveNumber + " random pools cannot be empty unless guarantees fill the wave"
                );
            }
        }
        return DataResult.success(profile);
    }

    public record Wave(
            int size,
            long bufferTicks,
            List<Guarantee> guaranteed,
            List<PoolEntry> pools
    ) {

        public static final Codec<Wave> CODEC = RecordCodecBuilder.<Wave>create(
                instance -> instance.group(
                        Codec.INT.fieldOf("size").forGetter(Wave::size),
                        Codec.LONG.fieldOf("buffer_ticks").forGetter(Wave::bufferTicks),
                        Guarantee.CODEC.listOf().optionalFieldOf("guaranteed", List.of()).forGetter(Wave::guaranteed),
                        RandomSelection.POOLS_CODEC.fieldOf("random").forGetter(Wave::pools)
                ).apply(instance, Wave::new)
        );

        public Wave {
            guaranteed = List.copyOf(guaranteed);
            pools = List.copyOf(pools);
        }

        public int guaranteedSize() {
            var total = 0;
            for (var guarantee : guaranteed) {
                total += guarantee.count();
            }
            return total;
        }

        public boolean matches(EntityType<?> entityType) {
            for (var guarantee : guaranteed) {
                if (guarantee.matches(entityType)) {
                    return true;
                }
            }
            for (var pool : pools) {
                if (pool.matches(entityType)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record RandomSelection(List<PoolEntry> pools) {

        public static final Codec<RandomSelection> CODEC = RecordCodecBuilder.<RandomSelection>create(
                instance -> instance.group(
                        PoolEntry.CODEC.listOf().fieldOf("pools").forGetter(RandomSelection::pools)
                ).apply(instance, RandomSelection::new)
        );

        static final Codec<List<PoolEntry>> POOLS_CODEC = CODEC.xmap(RandomSelection::pools, RandomSelection::new);

        public RandomSelection {
            pools = List.copyOf(pools);
        }
    }

    public record Guarantee(
            int count,
            List<PoolEntry> pools
    ) {

        public static final Codec<Guarantee> CODEC = RecordCodecBuilder.<Guarantee>create(
                instance -> instance.group(
                        Codec.INT.fieldOf("count").forGetter(Guarantee::count),
                        PoolEntry.CODEC.listOf().fieldOf("pools").forGetter(Guarantee::pools)
                ).apply(instance, Guarantee::new)
        ).flatXmap(Guarantee::validate, Guarantee::validate);

        public Guarantee {
            pools = List.copyOf(pools);
        }

        public boolean matches(EntityType<?> entityType) {
            for (var pool : pools) {
                if (pool.matches(entityType)) {
                    return true;
                }
            }
            return false;
        }

        private static DataResult<Guarantee> validate(Guarantee guarantee) {
            if (guarantee.count() <= 0) {
                return DataResult.error(() -> "Raid wave guaranteed count must be positive");
            }
            if (guarantee.pools().isEmpty()) {
                return DataResult.error(() -> "Raid wave guarantee must define at least one pool");
            }
            return DataResult.success(guarantee);
        }
    }

    public record PoolEntry(
            Optional<EntityType<?>> entity,
            Optional<ResourceLocation> tag,
            int weight,
            int maxCount
    ) {

        public static final Codec<PoolEntry> CODEC = RecordCodecBuilder.<PoolEntry>create(
                instance -> instance.group(
                        BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("entity").forGetter(PoolEntry::entity),
                        ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(entry -> entry.tag()),
                        Codec.INT.optionalFieldOf("weight", 1).forGetter(PoolEntry::weight),
                        Codec.INT.optionalFieldOf("max_count", Integer.MAX_VALUE).forGetter(PoolEntry::maxCount)
                ).apply(instance, PoolEntry::new)
        ).flatXmap(PoolEntry::validate, PoolEntry::validate);

        public static PoolEntry tagPool(TagKey<EntityType<?>> tag, int weight, int maxCount) {
            return new PoolEntry(Optional.empty(), Optional.of(tag.location()), weight, maxCount);
        }

        public static PoolEntry entityPool(EntityType<?> entityType, int weight, int maxCount) {
            return new PoolEntry(Optional.of(entityType), Optional.empty(), weight, maxCount);
        }

        public boolean matches(EntityType<?> entityType) {
            if (entity.isPresent() && entity.get() == entityType) {
                return true;
            }
            return tag.isPresent() && entityType.is(TagKey.create(Registries.ENTITY_TYPE, tag.get()));
        }

        private static DataResult<PoolEntry> validate(PoolEntry entry) {
            if (entry.entity().isEmpty() == entry.tag().isEmpty()) {
                return DataResult.error(() -> "Raid wave pool entry must define exactly one of entity or tag");
            }
            if (entry.weight() <= 0) {
                return DataResult.error(() -> "Raid wave pool entry weight must be positive");
            }
            if (entry.maxCount() <= 0) {
                return DataResult.error(() -> "Raid wave pool entry max_count must be positive");
            }
            return DataResult.success(entry);
        }
    }
}