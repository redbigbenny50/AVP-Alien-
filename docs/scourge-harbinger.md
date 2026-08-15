# Harbinger

The Harbinger is the main Scourge xenomorph.

## Role

The Harbinger is a late-game hive support bruiser.

It does two important things:

- buffs nearby allied xenomorphs with Frenzy
- produces Scourge Jelly quickly for the hive economy

In plain terms:

```text
Harbinger appears
-> nearby same-hive xenomorphs get Frenzy
-> hive starts producing Scourge Jelly much faster
-> more Scourge specialists become affordable
```

## Combat Stats

Current attributes:

- armor: 12
- armor toughness: 12
- attack damage: 75% of base player health
- max health: 5x base player health
- movement speed: 1.2x base walk speed
- knockback resistance: 0.7
- follow range: 35 blocks

It uses a large xenomorph path config and can dig with 2 parallel diggers.

## Attacks

The Harbinger has three regular attacks:

- claw
- bite
- tail

Each attack respects body-part requirements:

- claw requires an arm
- bite requires a head
- tail requires a tail

## Frenzy Aura

Every 30 seconds, the Harbinger checks a 16-block radius.

It finds nearby xenomorphs that:

- are alive
- are not itself
- belong to the same hive

Then it applies Frenzy for 30 seconds.

So if a Harbinger stays near a group, it can keep that group repeatedly frenzied.

## What Frenzy Does

Frenzy is a beneficial status effect.

It gives:

- +15% total attack damage
- +15% total movement speed

So basically:

```text
Harbinger is near allied xenomorphs
-> every 30 seconds it pulses Frenzy
-> same-hive xenomorphs within 16 blocks get 30 seconds of Frenzy
-> they hit harder and move faster
```

The aura does not buff enemies, players, or xenomorphs from another hive. It filters for same-hive xenomorphs only.

## Hive Economy

Harbingers produce Scourge Jelly much faster than queens.

Current default:

```text
1 Harbinger -> +1 Scourge Jelly per game-minute
```

This matters because Scourge Jelly is required for specialist upgrades like Carrier, Burster, Ravager, Razor Claw, and Chrysalis.

## Purchase Requirements

The normal Harbinger purchase requires:

- 1 Praetorian
- 200 biomass
- 1 Scourge Jelly
- minimum population of 100
- max 1 Harbinger in the location

The balance code also avoids making another Harbinger if one from that source location is away in a raid.

## Variants

Harbinger has variant-specific entity types:

- normal
- nether
- aberrant
- irradiated
