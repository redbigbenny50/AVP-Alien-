# Player Claims, Totem Chestbursting, And Claim-Safe Leaks

## Summary

Add always-on shared BLib player chunk claiming, add an off-by-default gamerule that lets Totems of Undying prevent player chestbursting death while still spawning an aberrant chestburster, and preserve the planned first-hive bootstrap leak system while making player-claimed chunks safe from leak/despawn triggers.

## Key Changes

### Player Claims

- Add persistent BLib-owned per-dimension chunk ownership for player claims.
- Add persistent BLib-owned per-player purchased extra claim slots.
- Register player claims into BLib territory with stable player claim ids.
- Keep claims "BLib only" for hive behavior: do not directly block hive AI, expansion, queen spawning, or existing hive territory logic.

### Claim Commands

Add shared BLib `/claim` commands:

- `/claim chunk`
  - Claims the player's current chunk if unclaimed and within their cap.
- `/claim buy`
  - Consumes diamonds and adds one extra claim slot.
- `/claim info`
  - Shows claimed count, max slots, next buy cost, and current chunk status.
- `/claim unclaim`
  - Releases the player's current chunk if they own it.

Claim economy:

- Base claim slots: `9`.
- Extra slot cost: `9 * (purchasedExtraSlots + 1)`.
- Example costs: `9`, then `18`, then `27` diamonds.

### Totem Chestbursting Gamerule

Add gamerule:

```text
avpAlienTotemsPreventChestbursterDeath
```

Behavior:

- Type: boolean.
- Default: `false`.
- When false, chestbursting behaves as it does now.
- When true, a player with a Totem of Undying in main hand or offhand survives lethal chestbursting.
- The totem is consumed.
- Normal totem survival effects are applied.
- The embryo is removed from the host.
- The spawned chestburster should be the aberrant equivalent where one exists.
- If an aberrant equivalent does not exist for the embryo type, fall back to the original spawned type.

### Claim-Safe Leaks

The first-hive bootstrap system from `proposed-first-hive-bootstrap-systems.md` should still be added.

Leak behavior:

- Leak-eligible royal chestbursters/adolescents can still leak when they despawn outside hive control.
- If a leak-eligible alien is inside a player-claimed chunk, it must not despawn into `StrainLeakData`.
- In player claims, those leak-eligible actors should be persistent or explicitly skip leak handling if vanilla despawn is somehow reached.
- Player claims protect leak-eligible bootstrap actors only, not every alien globally.

### Claim Map Style

- For xenomorph hive/location factions, set BLib `ClaimMapStyle` overlay metadata to the corresponding resin vein texture.
- Keep faction colors intact as fallback/current rendering behavior.
- Use the hive variant to select texture:
  - Normal: `avp_alien:textures/block/resin_vein.png`
  - Aberrant: `avp_alien:textures/block/aberrant_resin_vein.png`
  - Nether: `avp_alien:textures/block/nether_resin_vein.png`
  - Irradiated: `avp_alien:textures/block/irradiated_resin_vein.png`

Note: BLib's current Xaero highlighter remains color-only until a deeper texture overlay hook is implemented, but AVP should set the metadata now.

## Test Plan

- Run `./gradlew build`.
- Verify a player can claim 9 chunks and must buy more slots for a 10th.
- Verify `/claim buy` costs 9, then 18, then 27 diamonds.
- Verify claims and purchased slots survive world reload.
- Verify another player cannot claim an already claimed chunk.
- Verify chestbursting behaves normally when `avpAlienTotemsPreventChestbursterDeath` is false.
- Verify a player survives chestbursting with a totem when the gamerule is true.
- Verify the totem is consumed, the embryo is removed, and the spawned burster is aberrant.
- Verify leak-eligible royal offspring outside player claims can still feed the planned leak system.
- Verify the same leak-eligible alien inside a player claim does not despawn/leak.
- Verify hive claim factions still have colors and set resin overlay metadata.

## Assumptions

- "In a claim" means inside a chunk claimed by the new player claim system.
- Player claims do not directly change hive AI or hive territory decisions.
- "Abberant" maps to the existing project spelling: `aberrant`.
