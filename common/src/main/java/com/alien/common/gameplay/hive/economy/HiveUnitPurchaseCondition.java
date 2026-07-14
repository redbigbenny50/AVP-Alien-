package com.alien.common.gameplay.hive.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * Datapack-encoded gating condition for a {@link HiveUnitPurchase}. Evaluated against the candidate
 * {@link com.alien.common.gameplay.hive.location.HiveLocation} at buy time.
 * <p>
 * Sealed; each implementation is registered with a {@code type} discriminator in the dispatch codec.
 */
public sealed interface HiveUnitPurchaseCondition {

    String typeId();

    Codec<HiveUnitPurchaseCondition> CODEC = Codec.STRING
        .dispatch("type", HiveUnitPurchaseCondition::typeId, HiveUnitPurchaseCondition::mapCodecByType);

    private static MapCodec<? extends HiveUnitPurchaseCondition> mapCodecByType(String typeId) {
        return switch (typeId) {
            case MinPopulation.TYPE -> MinPopulation.MAP_CODEC;
            case MinEntityCountInLocation.TYPE -> MinEntityCountInLocation.MAP_CODEC;
            case MaxEntityCountInLocation.TYPE -> MaxEntityCountInLocation.MAP_CODEC;
            case MaxPerRaidChamber.TYPE -> MaxPerRaidChamber.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown HiveUnitPurchaseCondition type: " + typeId);
        };
    }

    /** Total location population (live members + reserves) must be at least {@code value}. */
    record MinPopulation(int value) implements HiveUnitPurchaseCondition {

        public static final String TYPE = "min_population";

        public static final MapCodec<MinPopulation> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance
                .group(Codec.INT.fieldOf("value").forGetter(MinPopulation::value))
                .apply(instance, MinPopulation::new)
        );

        @Override
        public String typeId() {
            return TYPE;
        }
    }

    /** Count of {@code entity} members in this location must be at least {@code value}. */
    record MinEntityCountInLocation(
        EntityType<?> entity,
        int value
    ) implements HiveUnitPurchaseCondition {

        public static final String TYPE = "min_entity_count_in_location";

        public static final MapCodec<MinEntityCountInLocation> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(MinEntityCountInLocation::entity),
                Codec.INT.fieldOf("value").forGetter(MinEntityCountInLocation::value)
            ).apply(instance, MinEntityCountInLocation::new)
        );

        @Override
        public String typeId() {
            return TYPE;
        }
    }

    /** Count of {@code entity} members in this location must be strictly less than {@code value}. */
    record MaxEntityCountInLocation(
        EntityType<?> entity,
        int value
    ) implements HiveUnitPurchaseCondition {

        public static final String TYPE = "max_entity_count_in_location";

        public static final MapCodec<MaxEntityCountInLocation> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(MaxEntityCountInLocation::entity),
                Codec.INT.fieldOf("value").forGetter(MaxEntityCountInLocation::value)
            ).apply(instance, MaxEntityCountInLocation::new)
        );

        @Override
        public String typeId() {
            return TYPE;
        }
    }

    /**
     * Count of {@code entity} must be strictly less than the hive's RAID CHAMBER count - one harbinger per chamber.
     * <p>
     * An ordinary hive only ever builds one raid chamber, so it gets exactly one harbinger: kill it and the hive is
     * disabled until it grows another. A hive under EMPRESS influence may build a second, and so fields a second
     * harbinger - which is what makes empress-backed hives able to keep raiding after you have decapitated one. That is
     * the whole point: empress backing does not just share resources, it buys raid RESILIENCE.
     */
    record MaxPerRaidChamber(EntityType<?> entity) implements HiveUnitPurchaseCondition {

        public static final String TYPE = "max_per_raid_chamber";

        public static final MapCodec<MaxPerRaidChamber> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(MaxPerRaidChamber::entity)
            ).apply(instance, MaxPerRaidChamber::new)
        );

        @Override
        public String typeId() {
            return TYPE;
        }
    }
}
