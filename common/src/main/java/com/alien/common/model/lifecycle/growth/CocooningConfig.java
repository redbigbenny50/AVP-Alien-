package com.alien.common.model.lifecycle.growth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CocooningConfig(
    int sourceTimeInTicks,
    int destinationTimeInTicks
) {

    public static final int DEFAULT_SOURCE_TIME_IN_TICKS = 30 * 20;

    public static final int DEFAULT_DESTINATION_TIME_IN_TICKS = 30 * 20;

    public static final CocooningConfig DEFAULT = new CocooningConfig(
        DEFAULT_SOURCE_TIME_IN_TICKS,
        DEFAULT_DESTINATION_TIME_IN_TICKS
    );

    /**
     * ⭐ THE SAME MOLT, ALL OF IT SPENT ON THE SOURCE.
     * <p>
     * A molt normally runs in two halves and the entity is REPLACED between them: the source wraps itself up for
     * {@code sourceTimeInTicks}, then the destination stands there in its own in-cocoon loop for
     * {@code destinationTimeInTicks} before emerging. That works when both forms have a loop clip.
     * </p>
     * <p>
     * It does not work when only the source does. [stated] "it emerges from the previous forms loop ... it never
     * changes form" - the spitter has an emerge clip and nothing else, deliberately, because it is terminal. So for
     * those molts the destination window collapses to a single tick: the source's loop covers the whole wrap-up and the
     * new form appears only to emerge. Total duration is unchanged - the time moves, it is not removed.
     * </p>
     */
    public CocooningConfig withoutDestinationWindow() {
        return new CocooningConfig(sourceTimeInTicks + destinationTimeInTicks, 1);
    }

    public static final Codec<CocooningConfig> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.INT.optionalFieldOf("sourceTimeInTicks", DEFAULT_SOURCE_TIME_IN_TICKS)
                .forGetter(CocooningConfig::sourceTimeInTicks),
            Codec.INT.optionalFieldOf("destinationTimeInTicks", DEFAULT_DESTINATION_TIME_IN_TICKS)
                .forGetter(CocooningConfig::destinationTimeInTicks)
        ).apply(instance, CocooningConfig::new)
    );
}
