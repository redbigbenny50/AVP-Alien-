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

        // The suppression gamble is per-implantation: everything resets when the embryo leaves the body.
        setSuppressionDoseCount(0);
        setJellyToxicity(0);
        setSuppressionSpent(false);
        setEmbryoWithered(false);
    }

    /** Growth Suppression doses taken during this implantation (drives the escalating sickness chance). */
    int getSuppressionDoseCount();

    void setSuppressionDoseCount(int doseCount);

    /** Hidden jelly toxicity tier 0-4 accumulated this implantation; 4 arms the coin flip. */
    int getJellyToxicity();

    void setJellyToxicity(int toxicity);

    /** True once a cheated death sentence spent the potion for this implantation - further doses do nothing. */
    boolean isSuppressionSpent();

    void setSuppressionSpent(boolean spent);

    /** True once the death sentence marked this embryo: whenever and however it emerges, it emerges withered. */
    boolean isEmbryoWithered();

    void setEmbryoWithered(boolean withered);

    /**
     * World game time this host was embedded in a host chamber (drives the egg-delivery settle delay); Long.MIN_VALUE
     * if never.
     */
    long getEmbedGameTime();

    void setEmbedGameTime(long gameTime);
}
