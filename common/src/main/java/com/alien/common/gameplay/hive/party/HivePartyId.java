package com.alien.common.gameplay.hive.party;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable per-party identifier. Wraps a {@link UUID}, mirroring {@code ConvoyId}'s shape — kept as a distinct type
 * rather than reusing {@code ConvoyId} since parties are a hive-level concept, structurally separate from the
 * empress-gated {@code Convoy} supply-chain system (see {@code HiveParty} for the full distinction).
 */
public record HivePartyId(UUID value) {

    public HivePartyId {
        Objects.requireNonNull(value, "HivePartyId value");
    }

    public static HivePartyId fresh() {
        return new HivePartyId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
