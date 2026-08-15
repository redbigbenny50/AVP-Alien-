package com.alien.common.model.lifecycle.growth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.util.List;

public record MoltingProfile(
    EntityType<?> entityType,
    float startScale,
    float endScale,
    List<MoltPhase> phases
) {

    public static final Codec<MoltingProfile> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entityType").forGetter(MoltingProfile::entityType),
            Codec.FLOAT.fieldOf("startScale").forGetter(MoltingProfile::startScale),
            Codec.FLOAT.fieldOf("endScale").forGetter(MoltingProfile::endScale),
            MoltPhase.CODEC.listOf().fieldOf("phases").forGetter(MoltingProfile::phases)
        ).apply(instance, MoltingProfile::new)
    );

    public int totalMaturationTicks() {
        return phases.stream().mapToInt(MoltPhase::totalTicks).sum();
    }

    public float scaleForPhase(int phaseIndex) {
        int phaseCount = phases.size();

        if (phaseCount == 0) {
            return endScale;
        }

        float fraction = (float) (phaseIndex + 1) / phaseCount;
        return startScale + (endScale - startScale) * fraction;
    }

    public float scaleBeforePhase(int phaseIndex) {
        if (phaseIndex <= 0) {
            return startScale;
        }

        return scaleForPhase(phaseIndex - 1);
    }

    public boolean isFullyMatured(int phaseIndex) {
        return phaseIndex >= phases.size();
    }
}
