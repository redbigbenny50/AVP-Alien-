package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.id.HiveLocationId;

/**
 * Links a materialized entity back to the {@link HiveParty} that spawned it. Mirrors {@code ConvoyMembership}'s
 * shape, but keyed to a {@link HiveLocationId} rather than a lineage faction id, since parties are hive-level (see
 * {@link HiveParty}'s class doc for the distinction from the empress-gated {@code Convoy} system).
 * <p>
 * Not NBT-persisted on the entity (unlike {@code ConvoyMembership}) — a deliberate simplification. If the server
 * restarts while a party member is materialized, it reloads as an ordinary reserve-owned xenomorph with no party
 * affiliation (falls back to default targeting/behavior; no crash, no duplication). {@link HiveParty}'s own
 * {@code materializedMembers} tracking (which IS persisted) remains the source of truth for lifecycle
 * resolution/refunds regardless — this field only drives party-specific behavior hooks, currently
 * {@code AlienPredicates#isTargetThreatAllowed}'s biomass-hunting-party THREAT_2 bypass.
 * <p>
 * (A near-identical field existed briefly for {@link HiveParty.SurfaceSpawn}'s HARVEST bias and was removed once that
 * turned out to duplicate the hive-wide biomass-gated threat system — this revival is for {@link HiveParty.BiomassHunting},
 * which has a genuine, confirmed need for a party-specific eligibility override.)
 */
public record PartyMembership(HiveLocationId sourceLocationId, HivePartyId partyId) {}
