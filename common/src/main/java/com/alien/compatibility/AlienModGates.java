package com.alien.compatibility;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_predator.AVPPredator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Which of this mod's entities need a SIBLING MOD present before they may exist in the world.
 * <p>
 * Two families are gated. Irradiated aliens are AVP: Human content - the strain only makes sense next to that mod's
 * radiation, nuked biome and hazard gear - and predaliens are AVP: Predator content, since a predalien is what a
 * facehugger makes of a yautja. Both families are nonetheless REGISTERED by this mod either way, which is deliberate:
 * pulling them out of the registry would strip the blocks and items straight out of existing saves and would break
 * every tag, loot table and model that names them. Registered-but-unspawnable costs nothing and destroys nothing.
 * <p>
 * The creative tabs already hide both families behind the same two checks. This class is about the paths a tab cannot
 * reach: {@code /summon}, a spawn egg that came from {@code /give} or an older install, a spawner block set to a gated
 * type, and any other caller that asks a level to add one.
 * <p>
 * Note that {@code irradiated_predalien} sits in BOTH tags, so it needs both mods - the first missing one is the one
 * reported, which is the right answer either way since installing it still leaves the other check to fail.
 */
public final class AlienModGates {

    public static final String AVP_HUMAN_NAME = "AVP: Human";

    public static final String AVP_PREDATOR_NAME = "AVP: Predator";

    private AlienModGates() {}

    /**
     * The display name of a sibling mod this entity type needs and which is not installed, or null when the entity is
     * free to spawn.
     * <p>
     * The both-loaded fast path matters: this runs on every entity added to a server level, item drops and arrows
     * included, so the common case must not reach a tag lookup at all.
     */
    public static @Nullable String missingModFor(EntityType<?> type) {
        var humanLoaded = AVPHuman.MOD.isLoaded();
        var predatorLoaded = AVPPredator.MOD.isLoaded();
        if (humanLoaded && predatorLoaded) {
            return null;
        }

        if (!humanLoaded && type.is(AlienEntityTypeTags.IRRADIATED_ALIENS)) {
            return AVP_HUMAN_NAME;
        }
        if (!predatorLoaded && type.is(AlienEntityTypeTags.PREDALIENS)) {
            return AVP_PREDATOR_NAME;
        }
        return null;
    }

    /** Player-facing explanation. Deliberately names the missing mod rather than just failing quietly. */
    public static Component refusalMessage(EntityType<?> type, String missingMod) {
        return Component.empty()
            .append(type.getDescription())
            .append(Component.literal(" needs " + missingMod + " installed, so nothing was spawned."));
    }
}
