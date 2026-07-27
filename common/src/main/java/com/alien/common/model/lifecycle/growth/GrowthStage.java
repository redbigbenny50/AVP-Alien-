package com.alien.common.model.lifecycle.growth;

import com.blib.api.common.entity.v1.EntityTypePredicate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * One rung of a lifecycle ladder: {@code from} becomes {@code to} once {@code requirements} hold.
 * <p>
 * {@code alternate} is the SECOND outcome the same rung can produce. A caste with two futures - a drone becomes a
 * carrier by default, or a razor claw - does not need two competing stages to express that, and deliberately must not
 * have them: {@code GrowthManager.findMatchingGrowthStage} returns the FIRST candidate whose requirements hold, and
 * candidate order comes out of a resource-scan map, so two identical-requirement stages would let file iteration order
 * decide what a drone turns into. One stage naming its own alternate is unambiguous instead. Which of the two you get
 * is decided during the molt - see {@code ScourgeStatusEffect}.
 */
public record GrowthStage(
    Optional<EntityTypePredicate> hostTypePredicate,
    EntityType<?> from,
    EntityType<?> to,
    int growthTimeInTicks,
    CocooningConfig cocooning,
    List<GrowthRequirement> requirements,
    Optional<EntityType<?>> alternate
) {

    public static final Codec<GrowthStage> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            EntityTypePredicate.CODEC.optionalFieldOf("hostTypePredicate").forGetter(GrowthStage::hostTypePredicate),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("from").forGetter(GrowthStage::from),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("to").forGetter(GrowthStage::to),
            Codec.INT.optionalFieldOf("growthTimeInTicks", 0).forGetter(GrowthStage::growthTimeInTicks),
            CocooningConfig.CODEC.optionalFieldOf("cocooning", CocooningConfig.DEFAULT).forGetter(GrowthStage::cocooning),
            GrowthRequirement.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(GrowthStage::requirements),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("alternate").forGetter(GrowthStage::alternate)
        ).apply(instance, GrowthStage::new)
    );

    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }

    public GrowthStage(
        EntityType<?> from,
        EntityType<?> to,
        int growthTimeInTicks
    ) {
        this(Optional.empty(), from, to, growthTimeInTicks, CocooningConfig.DEFAULT, List.of(), Optional.empty());
    }

    /** A rung with two possible outcomes: {@code to} by default, {@code alternate} if the molt is redirected. */
    public GrowthStage(
        EntityType<?> from,
        EntityType<?> to,
        EntityType<?> alternate,
        List<GrowthRequirement> requirements
    ) {
        this(Optional.empty(), from, to, 0, CocooningConfig.DEFAULT, requirements, Optional.of(alternate));
    }

    public GrowthStage(
        EntityType<?> from,
        EntityType<?> to,
        List<GrowthRequirement> requirements
    ) {
        this(Optional.empty(), from, to, 0, CocooningConfig.DEFAULT, requirements, Optional.empty());
    }

    public GrowthStage(
        TagKey<EntityType<?>> hostTag,
        EntityType<?> from,
        EntityType<?> to,
        int growthTimeInTicks
    ) {
        this(
            Optional.of(new EntityTypePredicate.Tag(hostTag)),
            from,
            to,
            growthTimeInTicks,
            CocooningConfig.DEFAULT,
            List.of(),
            Optional.empty()
        );
    }

    public GrowthStage(
        List<EntityType<?>> hostTypes,
        EntityType<?> from,
        EntityType<?> to,
        int growthTimeInTicks
    ) {
        this(
            Optional.of(new EntityTypePredicate.List(hostTypes)),
            from,
            to,
            growthTimeInTicks,
            CocooningConfig.DEFAULT,
            List.of(),
            Optional.empty()
        );
    }

    public GrowthStage(
        EntityType<?> hostType,
        EntityType<?> from,
        EntityType<?> to,
        int growthTimeInTicks
    ) {
        this(
            Optional.of(new EntityTypePredicate.Single(hostType)),
            from,
            to,
            growthTimeInTicks,
            CocooningConfig.DEFAULT,
            List.of(),
            Optional.empty()
        );
    }
}
