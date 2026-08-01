package com.alien.common.gameplay.hive.convoy;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EntityType;

import java.util.List;

public record ReinforcementProfile(List<RaidWaveProfile.PoolEntry> pools) {

    public static final Codec<ReinforcementProfile> CODEC = RecordCodecBuilder.<ReinforcementProfile>create(
        instance -> instance.group(
            RaidWaveProfile.RandomSelection.POOLS_CODEC.fieldOf("random").forGetter(ReinforcementProfile::pools)
        ).apply(instance, ReinforcementProfile::new)
    ).flatXmap(ReinforcementProfile::validate, ReinforcementProfile::validate);

    public ReinforcementProfile {
        pools = List.copyOf(pools);
    }

    public static ReinforcementProfile fallback() {
        return new ReinforcementProfile(
            List.of(
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.RUNNERS, 3, Integer.MAX_VALUE),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.DRONES, 3, Integer.MAX_VALUE),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.WARRIORS, 2, Integer.MAX_VALUE),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.PROWLERS, 2, Integer.MAX_VALUE),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.CHRYSALISES, 1, 2),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.RAZOR_CLAWS, 1, 2),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.BURSTERS, 1, 3),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.RAVAGERS, 1, 1),
                // NO CARRIERS. [stated] "lets remove them from any of the hive spawns including the raid room and
                // the harbingers reinforcements to it. have carriers only in Raids." A reinforcement carrier was
                // also a facehugger leak: arrival recalls the convoy's materialized members, and recall scatters a
                // carrier's spine payload (up to 6 huggers) AT THE HIVE before folding the carrier away.
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.PRAETORIANS, 1, 1),
                RaidWaveProfile.PoolEntry.tagPool(AlienEntityTypeTags.CRUSHERS, 1, 1)
            )
        );
    }

    public boolean matches(EntityType<?> entityType) {
        for (var pool : pools) {
            if (pool.matches(entityType)) {
                return true;
            }
        }
        return false;
    }

    private static DataResult<ReinforcementProfile> validate(ReinforcementProfile profile) {
        if (profile.pools().isEmpty()) {
            return DataResult.error(() -> "Reinforcement profile must define at least one random pool");
        }
        return DataResult.success(profile);
    }
}
