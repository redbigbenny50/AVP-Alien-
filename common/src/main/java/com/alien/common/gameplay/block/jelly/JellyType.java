package com.alien.common.gameplay.block.jelly;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Which kind of jelly a jelly vat holds. Fixed when the vat is placed, so it is stored as a block-state property -
 * which also means the structure parser can read it straight from a piece's palette to sort vats by kind.
 * <p>
 * IRRADIATED is the third kind. An irradiated hive does not keep the other two: its royal and scourge vats convert, and
 * its economy runs on the merged pool alone. That conversion belongs to the irradiated hive's own logic - this enum
 * only has to be able to name the result.
 * <p>
 * Anything switching on this MUST handle all three. The lookups in {@link JellyItems} and the vat renderer were both
 * written as {@code == SCOURGE ? scourge : royal} ternaries, which would have silently reported irradiated jelly as
 * royal rather than failing; they are switches now for exactly that reason.
 */
public enum JellyType implements StringRepresentable {

    ROYAL("royal"),
    SCOURGE("scourge"),
    IRRADIATED("irradiated");

    private final String name;

    JellyType(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
