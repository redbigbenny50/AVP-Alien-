package com.alien.common.gameplay.armor;

import com.alien.common.registry.tag.AlienItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Set bonuses for avp_alien armour.
 * <p>
 * These used to be delegated to BLib tags ({@code BLibItemTags.FIRE_RESISTANT_ARMORS} and friends), but nothing in BLib
 * ever read them and both were deprecated for removal in 0.3.0-alpha.419 - so the bonus the armour tooltips PROMISE was
 * never actually delivered. {@code NetherChitinArmorItem} and {@code PlatedNetherChitinArmorItem} both advertise
 * {@code WHEN_FULL_ARMOR_SET_EQUIPPED -> EFFECT_FIRE_RESISTANCE}; this is where that becomes true.
 */
public final class ArmorSetEffects {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private ArmorSetEffects() {
        throw new UnsupportedOperationException();
    }

    /**
     * True when all four armour slots are filled with nether chitin, plated nether chitin, or any mix of the two.
     * <p>
     * Mixing is allowed on purpose: plated nether chitin is the upgrade of the same material, so a half-upgraded set is
     * still a full set of nether-forged plate and there is no reason for it to burn. Requiring FOUR pieces is what the
     * tooltip promises, and it keeps a single scavenged helmet from granting immunity.
     */
    public static boolean isWearingFireproofSet(LivingEntity entity) {
        for (var slot : ARMOR_SLOTS) {
            if (!isFireproofPiece(entity.getItemBySlot(slot))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isFireproofPiece(ItemStack stack) {
        return !stack.isEmpty()
            && (stack.is(AlienItemTags.NETHER_CHITIN_ARMOR) || stack.is(AlienItemTags.PLATED_NETHER_CHITIN_ARMOR));
    }
}
