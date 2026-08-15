package com.alien.common.data.loot;

import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.model.alien.variant.AlienVariantType;
import com.alien.common.registry.init.item.AlienItems;
import net.minecraft.world.item.Item;

/**
 * Which jelly a strain bleeds.
 * <p>
 * ONE RULE: an irradiated hive has no royal jelly and no scourge jelly, only the merged pool, so ANYTHING that would
 * have dropped either drops irradiated jelly instead. Normal, nether and aberrant all still drop what they always did,
 * because their hives still spend both.
 * <p>
 * That is why the harbinger goes through here too. It is a scourge caste, not royalty - but in an irradiated hive the
 * distinction has nothing left to mean, so it is royal in name only.
 * <p>
 * A helper rather than another field on {@link AlienVariantType}: the strain-specific ITEMS on that record (chitin,
 * plated chitin, resin ball) each have four genuinely different values, whereas this is one exception against three
 * identical cases, and it would have meant threading a new argument through four variant constructors to say "the same
 * as the others" three times.
 */
public final class JellyLoot {

    private JellyLoot() {
        throw new UnsupportedOperationException();
    }

    public static Item royalJelly(AlienVariantType alienVariantType) {
        return alienVariantType.variant() == AlienVariant.IRRADIATED
            ? AlienItems.RAW_IRRADIATED_JELLY.get()
            : AlienItems.RAW_ROYAL_JELLY.get();
    }

    public static Item scourgeJelly(AlienVariantType alienVariantType) {
        return alienVariantType.variant() == AlienVariant.IRRADIATED
            ? AlienItems.RAW_IRRADIATED_JELLY.get()
            : AlienItems.RAW_SCOURGE_JELLY.get();
    }
}
