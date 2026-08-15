# Scourge Specialist Castes

This file explains the current Scourge-tagged specialist xenomorphs.

The Scourge tag currently includes:

- Burster
- Carrier
- Chrysalis
- Harbinger
- Ravager
- Razor Claw

Harbinger has its own file. This file covers the other specialist castes.

## Burster

The Burster is a small-door pathing explosive specialist.

It can:

- carry ovomorphs
- build vents
- fight with claw, bite, and tail attacks
- explode with acid on death
- self-detonate when critically low on health

Important behavior:

```text
Burster health <= 10%
-> explodes
-> creates acid explosion
-> discards itself
```

Explosion values:

- radius: 2 blocks
- acid amount: 3

Hive purchase:

```text
Runner + 1 Scourge Jelly + Harbinger present -> Burster
```

## Carrier

The Carrier is a large facehugger transport specialist.

It can:

- carry facehuggers as passengers
- spawn/fill reserve facehugger payloads
- release all carried facehuggers on death
- use normal claw, bite, and tail attacks
- run carrier-specific GOAP

Important behavior:

```text
Carrier dies
-> all riding facehuggers are released outward
```

It also has named actions for:

- throwing a facehugger
- scream-releasing facehuggers

Hive purchase:

```text
Drone + 1 Scourge Jelly + Harbinger present -> Carrier
```

## Chrysalis

The Chrysalis is a rolling bruiser.

It can:

- roll toward a target
- knock targets away on impact
- damage walls when it smashes into them
- become briefly stunned after smashing
- leave a fire trail while rolling if it is the Nether variant

Important roll values:

- roll duration: 100 ticks
- roll cooldown: 600 ticks
- roll speed multiplier: 1.5
- wall smash damage: 60
- stun after wall smash: 28 to 44 ticks

Hive purchase:

```text
Prowler + 1 Scourge Jelly + Harbinger present -> Chrysalis
```

## Ravager

The Ravager is a large area-damage melee specialist.

It can:

- use single-claw and double-claw attacks
- use bite and tail attacks
- use a swim attack underwater
- prefer claw attacks when multiple close targets are nearby
- use a triggered special cleave attack

Important behavior:

```text
multiple close targets nearby
-> Ravager prefers claw/double-claw options
```

Front area values:

- range: 5 blocks
- cone angle: 90 degrees

Hive purchase:

```text
Warrior + 1 Scourge Jelly + Harbinger present -> Ravager
```

## Razor Claw

The Razor Claw is a large bleed specialist.

It can:

- use claw, bite, tail, and swim attacks
- use a triggered sweep attack
- apply Blood Loss when it successfully hurts a living target

Important behavior:

```text
Razor Claw hits a living target
-> target gets Blood Loss for 15 seconds
```

## What Blood Loss Does

Blood Loss is a harmful status effect.

While a living entity has Blood Loss, every successful damage event also reduces that entity's max health by the actual damage taken.

So basically:

```text
target has Blood Loss
-> target takes 4 real damage
-> target also loses 4 max health while Blood Loss remains
```

When Blood Loss is removed, the max-health reduction is removed too.

This makes Razor Claw dangerous because follow-up hits temporarily shrink the target's health ceiling, not just its current health.

Hive purchase:

```text
Runner + 2 Scourge Jelly + Harbinger present -> Razor Claw
```

## Raid Role

These units appear as raid escalation pieces.

The later a raid wave is, the more likely it is to include Scourge specialists.

In the normal generated raid profile:

- Wave 3 can bring Chrysalis, Razor Claw, and Burster.
- Wave 4 can bring Chrysalis, Razor Claw, Burster, Ravager, and Carrier.
- Wave 5 guarantees a Harbinger and can include the rest.
