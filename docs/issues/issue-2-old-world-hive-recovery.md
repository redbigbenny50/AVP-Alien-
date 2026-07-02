# Issue 2: Old World Hive Recovery

## Problem

Old worlds that used AVP before the hive redesign can load with broken xenomorph and hive behavior.

Reported symptoms include:

- Xenomorphs refuse to attack mobs.
- Xenomorphs do not collect biomass.
- Hives do not claim chunks correctly.
- Hives do not grow their location.
- Adolescents do not grow into adults.
- A mentioned queen awaken command does not exist.
- Xenomorphs refuse to spread resin.
- Xenomorphs do not spawn on resin blocks even when the hive has enough biomass.
- Founding hives can appear as only one claimed chunk instead of the expected founding area.
- Queen egg laying may stop.

Some of these may be downstream symptoms of the same problem: old hive data and old xenomorphs are not fully attached to the new lineage/location hive system.

## Current Suspicion

Old worlds can contain legacy hive data in `data/hive_data.dat`.

The current old hive migrator handles legacy BLib hive factions, but it does not appear to directly rebuild hives from the old `hive_data.dat` format. That means an old world can have old hive/member data on disk while the new system does not fully restore the matching lineage, location, membership, reserves, and claimed chunks.

In simple terms:

1. An old world has old hive data.
2. The new code loads the world.
3. The old xenomorphs may not get a real new lineage/location.
4. The hive systems that depend on lineage/location membership stop seeing those xenomorphs correctly.
5. Attacking, biomass, resin spreading, growth, spawning, claims, and queen behavior can all appear broken.

## Recovery Goal

Do not blindly delete old xenomorphs.

Instead, recover old worlds by:

- Preserving old xenomorph genetics.
- Rebuilding missing hive lineage/location data.
- Reattaching old xenomorphs to the repaired hive.
- Placing old queens into a dormant recovery state until the player wakes them.
- Providing a command to wake all legacy dormant queens.
- Informing players on world join when legacy recovery is detected.

## Planned Detection

The mod should detect old-world recovery needs by checking for one or more of these:

- Legacy `hive_data.dat` exists in saved world data.
- Legacy hive/member NBT shapes are present.
- Legacy BLib hive factions are present.
- Xenomorphs load without required new lineage/location membership.
- Queens load with old ovipositor or old hive state that should not be trusted.

The world should store recovery status so the same warning and migration are not repeated forever.

Suggested stored state:

- `legacyHiveDataDetected`
- `legacyHiveRecoveryApplied`
- `legacyHiveRecoveryMessageShown`
- `legacyDormantQueensRemaining`

## Join Message

When legacy hive data is detected, players should receive a clear message that explains what happened and includes the wake command.

Suggested full message:

```text
[AVP] Legacy hive data was detected in this world. Old xenomorphs are being repaired so they can rejoin the new hive system. Old queens have been placed into a dormant recovery state and will not wake until damaged, interacted with, or awakened with /avp_alien debug hive awaken_legacy_queens.
```

Suggested shorter message:

```text
[AVP] Legacy hive data detected. Old xenomorphs are being repaired. Old queens are dormant until damaged, interacted with, or awakened with /avp_alien debug hive awaken_legacy_queens.
```

## Planned Hive Recovery

For each old hive found:

1. Read the old hive snapshot.
2. Recover the old center position.
3. Recover the old leader/founder when possible.
4. Recover the old variant.
5. Recover known members.
6. Recover biomass/reserves.
7. Create or repair the new lineage faction.
8. Create or repair the new location faction.
9. Claim the expected founding area instead of leaving the hive as one chunk.
10. Rebuild membership links so old xenomorphs count as real hive members again.

If a partial new lineage/location already exists, the recovery should repair it instead of duplicating it.

## Planned Xenomorph Reset

Old xenomorphs should be reset only where needed.

They should keep:

- Genetics.
- Variant.
- Position.
- Name/custom name.
- Health where reasonable.
- Entity identity where possible.

They should have repaired:

- Variant faction membership.
- Lineage membership.
- Location membership.
- Broken or stale hive references.
- Growth data if old data prevents normal growth.

The goal is to make old xenomorphs behave like valid members of the new hive system without wiping what made that individual xenomorph unique.

## Planned Queen Handling

Old queens need special handling because queen lifecycle and ovipositor behavior changed.

When an old queen is detected:

1. Force her off old ovipositor state safely.
2. Put her into a stored legacy dormant/incapacitated state.
3. Prevent egg laying while dormant.
4. Prevent founding while dormant.
5. Prevent active hive behavior while dormant.
6. Keep her asleep until damaged, interacted with, or awakened by command.

When awakened:

1. Clear the dormant state.
2. Reattach her to the repaired lineage/location.
3. Allow normal queen lifecycle behavior again.
4. Allow egg laying/founding only after recovery is complete.

The current queen incapacitated state is not enough by itself and should be implemented as a real persisted legacy recovery state.

## Planned Command

Add a command for recovering dormant old queens:

```text
/avp_alien debug hive awaken_legacy_queens
```

Expected behavior:

- Finds all legacy dormant queens in the loaded world/server.
- Clears their dormant recovery state.
- Allows them to resume normal queen behavior.
- Sends feedback showing how many queens were awakened.

Optional supporting commands:

```text
/avp_alien debug hive inspect_legacy_recovery
/avp_alien debug hive repair_old_world
```

The required command is `awaken_legacy_queens`.

## Verification Checklist

Use a copy of the old world for testing, not the original zip.

Verify that after recovery:

- Xenomorphs attack valid mobs again.
- Xenomorphs collect biomass again.
- Hives claim the expected founding area.
- Hives can grow locations.
- Adolescents can grow to adults.
- Xenomorphs spread resin again.
- Xenomorphs can spawn from resin when biomass is high enough.
- Queens do not wake automatically if marked legacy dormant.
- Dormant queens wake when damaged.
- Dormant queens wake when interacted with.
- Dormant queens wake through `/avp_alien debug hive awaken_legacy_queens`.
- Queens resume egg laying only after being awakened and repaired.
- The old-world join message appears and includes the awaken command.

