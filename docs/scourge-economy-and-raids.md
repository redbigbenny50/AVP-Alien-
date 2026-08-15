# Scourge Economy And Raids

This file explains how Scourge units enter hives and raids.

## The Resource Loop

Scourge Jelly is the bottleneck.

```text
Queens slowly make Scourge Jelly
-> first Harbinger can be purchased
-> Harbinger makes Scourge Jelly quickly
-> hive can buy specialist Scourge units
-> late raid waves can spend/send those units
```

## Production

Scourge Jelly production is per hive location.

Producer counts come from:

- loaded members
- local reserves

Known-but-unloaded UUID members are intentionally ignored because they can be stale.

Current default production:

```text
queen count adds to queenScourgeAccumulator
harbinger count adds to harbingerScourgeAccumulator

queen threshold: 100 game-minutes per Scourge Jelly
harbinger threshold: 1 game-minute per Scourge Jelly
```

Scourge Jelly is capped by claimed chunk count.

So:

```text
location has 20 claimed chunks
-> max 20 Scourge Jelly stored
```

## Buying Scourge Units

The hive balance task buys units every tick when resources and population rules allow it.

The usual progression is:

```text
population fills with basic units
-> hive reaches population pressure/ratios
-> royal/scourge resources are available
-> hive upgrades existing reserve units into stronger forms
```

Scourge purchases usually do not add population. They convert an existing caste into a stronger specialist.

Examples:

```text
Praetorian -> Harbinger
Drone -> Carrier
Runner -> Burster
Runner -> Razor Claw
Warrior -> Ravager
Prowler -> Chrysalis
```

## Harbinger Gate

Most Scourge specialists require at least one Harbinger in the location.

That means the Harbinger is both:

- a combat/support unit
- an economy unlock

## Harbinger Limit

The balance task blocks extra Harbingers if:

- the location already has one
- a Harbinger from that source location is away in a raid

This prevents a hive from creating duplicate Harbingers just because its existing one is temporarily traveling.

## Raid Waves

Raid profiles are data-driven.

The current normal raid profile escalates like this:

```text
Wave 1: warriors/prowlers
Wave 2: warriors/prowlers plus a Chrysalis or Razor Claw guarantee
Wave 3: adds Chrysalis, Razor Claw, Burster options
Wave 4: adds Ravager and Carrier options
Wave 5: guarantees a Harbinger and can include all major specialists
```

So Scourge units are mostly a late-raid threat.

## Design Meaning

Scourge is currently the "the hive is mature and dangerous now" layer.

A young hive can grow with drones, runners, warriors, and queens.

A mature hive with Scourge Jelly and a Harbinger starts producing specialized answers:

- Bursters punish close combat and low-health cleanup.
- Carriers bring facehugger pressure.
- Chrysalises create rolling disruption.
- Ravagers punish groups.
- Razor Claws add Blood Loss pressure.
- Harbingers buff the whole pack and keep Scourge Jelly flowing.

## Effects In This Layer

Frenzy is the main combat buff connected to Scourge behavior.

It is applied by Harbingers as an aura:

```text
every 30 seconds
-> scan 16 blocks
-> find same-hive xenomorphs
-> apply 30 seconds of Frenzy
```

Frenzy gives:

- +15% total attack damage
- +15% total movement speed

Blood Loss is the main harmful effect connected to Scourge specialists.

Razor Claws apply Blood Loss for 15 seconds on successful hits. While Blood Loss is active, damage taken also reduces max health by the actual damage amount. When the effect ends, that max-health reduction is removed.

Scourge itself currently acts as a neutral red-particle potion/effect. It is registered and brewable from Raw Scourge Jelly, but the current effect class does not add stat modifiers or tick behavior.
