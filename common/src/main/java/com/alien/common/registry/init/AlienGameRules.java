package com.alien.common.registry.init;

import com.alien.mixin.MixinGameRulesAccessor;
import com.alien.mixin.MixinGameRulesBooleanValueAccessor;
import net.minecraft.world.level.GameRules;

public final class AlienGameRules {

    public static final GameRules.Key<GameRules.BooleanValue> AVP_ALIEN_TOTEMS_PREVENT_CHESTBURSTER_DEATH =
        MixinGameRulesAccessor.avp_alien$register(
            "avpAlienTotemsPreventChestbursterDeath",
            GameRules.Category.PLAYER,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(false, (server, value) -> {})
        );

    /**
     * Whether a hive may substitute a PREDALIEN into its reserve production. OFF by default.
     * <p>
     * A hive growing its own predaliens from simulated reserves was contentious - it makes them ordinary hive stock
     * rather than the product of a predator being taken by a facehugger, which is where they come from. So it is now
     * opt-in per world, and needs AVP: Predator installed on top: the gamerule alone does nothing without the mod that
     * owns the species.
     */
    public static final GameRules.Key<GameRules.BooleanValue> AVP_ALIEN_HIVES_BREED_PREDALIENS =
        MixinGameRulesAccessor.avp_alien$register(
            "avpAlienHivesBreedPredaliens",
            GameRules.Category.SPAWNING,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(false, (server, value) -> {})
        );

    private AlienGameRules() {}

    public static void initialize() {}
}
