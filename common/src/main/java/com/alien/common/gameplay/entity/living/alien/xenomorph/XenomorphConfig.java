package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public record XenomorphConfig(
    XenomorphPathConfig pathConfig,
    @Nullable XenomorphAttackConfig attackConfig,
    int parallelDigCount,
    float healthRegenPerSecond,
    boolean isPushedByFluid,
    boolean canCrawl,
    boolean canCrawlAfterLegLoss,
    Function<AlienVariant, @Nullable EntityType<? extends Alien>> variantResolver
) {

    public static Builder builder(
        XenomorphPathConfig pathConfig,
        Function<AlienVariant, @Nullable EntityType<? extends Alien>> variantResolver
    ) {
        return new Builder(pathConfig, variantResolver);
    }

    public static final class Builder {

        private final XenomorphPathConfig pathConfig;

        private final Function<AlienVariant, @Nullable EntityType<? extends Alien>> variantResolver;

        private @Nullable XenomorphAttackConfig attackConfig = null;

        private int parallelDigCount = 1;

        private float healthRegenPerSecond = 0.5F;

        private boolean isPushedByFluid = true;

        private boolean canCrawl = true;

        private boolean canCrawlAfterLegLoss = true;

        private Builder(
            XenomorphPathConfig pathConfig,
            Function<AlienVariant, @Nullable EntityType<? extends Alien>> variantResolver
        ) {
            this.pathConfig = pathConfig;
            this.variantResolver = variantResolver;
        }

        public Builder attackConfig(XenomorphAttackConfig attackConfig) {
            this.attackConfig = attackConfig;
            return this;
        }

        public Builder parallelDigCount(int parallelDigCount) {
            this.parallelDigCount = parallelDigCount;
            return this;
        }

        public Builder healthRegenPerSecond(float healthRegenPerSecond) {
            this.healthRegenPerSecond = healthRegenPerSecond;
            return this;
        }

        public Builder pushedByFluid(boolean isPushedByFluid) {
            this.isPushedByFluid = isPushedByFluid;
            return this;
        }

        /**
         * Whether this xenomorph type can drop into a crawling stance — driven both by tight overhead clearance and by
         * leg dismemberment. Set to {@code false} for monumentally large variants (queen, empress, ...) so the crawling
         * manager skips them entirely and the dismemberment system never takes their legs.
         */
        public Builder canCrawl(boolean canCrawl) {
            this.canCrawl = canCrawl;
            return this;
        }

        /** Allows a damaged giant to crawl after losing a leg without allowing normal tight-space path crawling. */
        public Builder canCrawlAfterLegLoss(boolean canCrawlAfterLegLoss) {
            this.canCrawlAfterLegLoss = canCrawlAfterLegLoss;
            return this;
        }

        public XenomorphConfig build() {
            return new XenomorphConfig(
                pathConfig,
                attackConfig,
                parallelDigCount,
                healthRegenPerSecond,
                isPushedByFluid,
                canCrawl,
                canCrawlAfterLegLoss,
                variantResolver
            );
        }
    }
}
