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

    private AlienGameRules() {}

    public static void initialize() {}
}
