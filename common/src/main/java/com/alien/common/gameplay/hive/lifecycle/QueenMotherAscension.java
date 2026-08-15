package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A queen mother taking the species tier, and the empire-wide surge that follows.
 * <p>
 * A hive may seed exactly two daughters in its lifetime - see {@code maxDaughterHivesPerLocation} and
 * {@code HiveLocation.daughterHivesFounded}, which never decrements. That ceiling is what stops one hive quietly taking
 * the world, and paired with the 8-location lineage cap it converges 1 -&gt; 3 -&gt; 7 -&gt; 8 and then stops forever.
 * <p>
 * A QUEEN MOTHER CLEARS EVERY ONE OF THOSE COUNTERS. She sits above the empress - one per {@link AlienVariant} per
 * dimension, on the species faction rather than a lineage - and her arrival gives every hive of her strain its full
 * allowance back at once. The result is deliberately dramatic: an empire that had settled at its ceiling starts
 * expanding again everywhere simultaneously, which is exactly the shape the tier should have. It is the only thing in
 * the system that undoes a permanent cost.
 * <p>
 * <b>She is not built yet.</b> {@code VariantFactionData} has held the queen mother slot from the start and nothing has
 * ever filled it. This exists so that whoever wires her emergence cannot forget the surge: record her THROUGH here and
 * the reset happens, rather than leaving it as a second step someone has to remember.
 */
public final class QueenMotherAscension {

    private QueenMotherAscension() {}

    /**
     * Record {@code queenMotherId} as the queen mother for this variant in {@code dimension}, and hand every hive of
     * that strain its daughter allowance back.
     *
     * @return how many locations had their allowance restored, for logging by the caller.
     */
    public static int ascend(
        VariantFactionData variantData,
        ResourceKey<Level> dimension,
        UUID queenMotherId
    ) {
        variantData.setQueenMother(dimension, queenMotherId);

        var restored = 0;

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }

            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            // Her strain, her dimension. A nether queen mother does not restart the aberrants.
            if (lineage.variant() != variantData.variant() || !dimension.equals(lineage.dimension())) {
                continue;
            }

            for (var location : lineage.locationsById().values()) {
                // Exiled remnants are excluded. The empire wrote that hive off; a new matriarch does not un-write it,
                // and a remnant is barred from spreading anyway.
                if (!location.isAlive() || location.isExiled() || location.daughterHivesFounded() == 0) {
                    continue;
                }
                location.setDaughterHivesFounded(0);
                restored++;
            }

            lineage.markDirty();
        }

        Alien.LOGGER.info(
            "Hive: QUEEN MOTHER ascended for {} in {} - daughter allowance restored at {} hives",
            variantData.variant(),
            dimension.location(),
            restored
        );

        return restored;
    }
}
