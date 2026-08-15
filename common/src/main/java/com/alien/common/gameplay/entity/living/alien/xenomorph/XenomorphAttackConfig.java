package com.alien.common.gameplay.entity.living.alien.xenomorph;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XenomorphAttackConfig {

    public record WeightedAttack(
        AttackType attack,
        int weight
    ) {}

    private final List<WeightedAttack> regulars;

    private final List<AttackType> triggered;

    private XenomorphAttackConfig(List<WeightedAttack> regulars, List<AttackType> triggered) {
        this.regulars = Collections.unmodifiableList(regulars);
        this.triggered = Collections.unmodifiableList(triggered);
    }

    public List<WeightedAttack> regulars() {
        return regulars;
    }

    public List<AttackType> triggered() {
        return triggered;
    }

    public boolean hasRegulars() {
        return !regulars.isEmpty();
    }

    /**
     * Picks a weighted-random regular attack whose cooldown is ready and whose requirements are satisfied.
     * <p>
     * CRAWL PREFERENCE: while the xenomorph is crawling, if ANY usable-and-ready regular is a crawl attack, the pick is
     * restricted to crawl attacks only. This gives a caste WITH crawl attacks the full posture rule ("a crawling
     * xenomorph uses only crawl attacks") without touching the softened gate in AttackType.canUse - a caste with crawl
     * clips fights properly from the ground, while one without any still falls through to its standing set exactly as
     * before. The ravager is unaffected: its own selectAttack never reaches this method while crawling.
     */
    public @Nullable AttackType selectRegular(RandomSource random, AttackCooldownTracker cooldownTracker, Xenomorph xenomorph) {
        var restrictToCrawlAttacks = false;
        if (xenomorph.getCrawlingManager().isCrawling()) {
            for (var weighted : regulars) {
                if (
                    weighted.attack().crawlAttack()
                        && cooldownTracker.isReady(weighted.attack())
                        && weighted.attack().canUse(xenomorph)
                ) {
                    restrictToCrawlAttacks = true;
                    break;
                }
            }
        }

        var totalWeight = 0;

        for (var weighted : regulars) {
            if (restrictToCrawlAttacks && !weighted.attack().crawlAttack()) {
                continue;
            }
            if (cooldownTracker.isReady(weighted.attack()) && weighted.attack().canUse(xenomorph)) {
                totalWeight += weighted.weight();
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        var roll = random.nextInt(totalWeight);

        for (var weighted : regulars) {
            if (restrictToCrawlAttacks && !weighted.attack().crawlAttack()) {
                continue;
            }
            if (!cooldownTracker.isReady(weighted.attack()) || !weighted.attack().canUse(xenomorph)) {
                continue;
            }

            roll -= weighted.weight();

            if (roll < 0) {
                return weighted.attack();
            }
        }

        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<WeightedAttack> regulars = new ArrayList<>();

        private final List<AttackType> triggered = new ArrayList<>();

        private Builder() {}

        /** Add a regular attack using its own default weight. */
        public Builder addRegular(AttackType attack) {
            return addRegular(attack, attack.weight());
        }

        /** Add a regular attack overriding its weight just for this config. */
        public Builder addRegular(AttackType attack, int weight) {
            if (weight <= 0) {
                throw new IllegalArgumentException("Attack weight must be positive (got " + weight + " for " + attack.id() + ")");
            }
            regulars.add(new WeightedAttack(attack, weight));
            return this;
        }

        /** Add a triggered (special) attack. Each gets its own GOAP goal/action. */
        public Builder addTriggered(AttackType attack) {
            triggered.add(attack);
            return this;
        }

        public XenomorphAttackConfig build() {
            return new XenomorphAttackConfig(regulars, triggered);
        }
    }
}
