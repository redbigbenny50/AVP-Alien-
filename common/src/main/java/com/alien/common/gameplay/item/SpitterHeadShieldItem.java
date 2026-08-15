package com.alien.common.gameplay.item;

import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.SpitterSpitAttack;
import com.alien.common.gameplay.item.ability.ShieldAbilityItem;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class SpitterHeadShieldItem extends XenomorphHeadShieldItem implements ShieldAbilityItem {

    private static final int ABILITY_COOLDOWN_TICKS = 60;

    private static final int ABILITY_DURABILITY_COST = 4;

    private final AlienVariant variant;

    public SpitterHeadShieldItem(AlienVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public void activateShieldAbility(ServerPlayer player, ItemStack stack) {
        SpitterSpitAttack.shootForward(player, variant);
        player.getCooldowns().addCooldown(stack.getItem(), ABILITY_COOLDOWN_TICKS);
        stack.hurtAndBreak(ABILITY_DURABILITY_COST, player, getUsingEquipmentSlot(player));
        player.stopUsingItem();
    }

    private static EquipmentSlot getUsingEquipmentSlot(ServerPlayer player) {
        return player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }
}
