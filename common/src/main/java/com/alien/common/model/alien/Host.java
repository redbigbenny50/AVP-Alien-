package com.alien.common.model.alien;

import com.alien.common.gameplay.entity.living.alien.parasite.Parasite;
import com.alien.compatibility.avp_human.GeneContainerProxy;
import com.just.core.functional.option.Option;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public interface Host {

    Option<EntityType<?>> getEmbryoType();

    void setEmbryoType(@Nullable EntityType<?> embryoType);

    void implantEmbryo(Parasite parasite);

    GeneContainerProxy getOrCreateParasiteGeneContainer();

    int getEmbryoGrowthTimeInTicks();

    void setEmbryoGrowthTimeInTicks(int growthTimeInTicks);

    default void incrementEmbryoGrowthTimeInTicks() {
        setEmbryoGrowthTimeInTicks(getEmbryoGrowthTimeInTicks() + 1);
    }

    default void removeEmbryo() {
        setEmbryoType(null);
        setEmbryoGrowthTimeInTicks(0);
        getOrCreateParasiteGeneContainer().clear();
    }

    /**
     * World game time this host was embedded in a host chamber (drives the egg-delivery settle delay); Long.MIN_VALUE
     * if never.
     */
    long getEmbedGameTime();

    void setEmbedGameTime(long gameTime);
}
