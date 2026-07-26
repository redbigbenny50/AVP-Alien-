package com.alien.common.gameplay.block.jelly;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Which kind of jelly a jelly vat holds. Fixed when the vat is placed (a vat is royal or scourge for its lifetime), so
 * it is stored as a block-state property — which also means the structure parser can read it straight from a piece's
 * palette to sort royal vats from scourge vats.
 */
public enum JellyType implements StringRepresentable {

    ROYAL("royal"),
    SCOURGE("scourge");

    private final String name;

    JellyType(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
