package com.alien.common.gameplay.item;

import com.alien.common.data.AlienAdvancements;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.shield.v1.BLibShieldConfig;
import com.blib.api.common.shield.v1.BLibShieldItem;
import com.blib.api.common.shield.v1.BlockResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shield variant crafted from a xenomorph head trophy and a vanilla shield.
 */
public class XenomorphHeadShieldItem extends Item implements BLibShieldItem {

    private static final BLibShieldConfig CONFIG = new BLibShieldConfig(
        72000,
        180f,
        SoundEvents.SHIELD_BLOCK
    );

    public XenomorphHeadShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public BLibShieldConfig getShieldConfig() {
        return CONFIG;
    }

    @Override
    public BlockResult onBlocked(LivingEntity user, ItemStack stack, DamageSource source, float damage) {
        if (source.is(AlienDamageTypeKeys.ACID_SPIT)) {
            grantSpitBlockAdvancement(user, source);
            return BlockResult.fullBlock();
        }

        var damageDealt = (int) Math.max(1, Math.ceil(damage));
        stack.hurtAndBreak(damageDealt, user, EquipmentSlot.MAINHAND);

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return BlockResult.blockedAndDisabled(60);
        }

        if (
            source.getDirectEntity() instanceof LivingEntity attacker
                && (attacker.getMainHandItem().getItem() instanceof AxeItem
                    || attacker.canDisableShield())
        ) {
            return BlockResult.blockedAndDisabled(100);
        }

        return BlockResult.fullBlock();
    }

    private static void grantSpitBlockAdvancement(LivingEntity user, DamageSource source) {
        if (!(user instanceof ServerPlayer player)) {
            return;
        }

        var attacker = source.getEntity();
        if (attacker != null && attacker.getType().is(AlienEntityTypeTags.SPITTERS)) {
            AlienAdvancements.BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD.grant(player);
        }
    }
}
