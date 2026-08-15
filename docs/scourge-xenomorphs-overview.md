# Scourge Xenomorphs Overview

This is a current-code explanation of what the Scourge tier does.

## Short Version

Scourge is a late-hive power tier.

It currently means three connected things:

- **Scourge Jelly**: a rare hive resource used to buy high-tier caste upgrades.
- **Harbingers**: the key Scourge caste. They unlock other Scourge-tagged units and generate Scourge Jelly quickly.
- **Scourge-tagged specialists**: Burster, Carrier, Chrysalis, Harbinger, Ravager, and Razor Claw.

So basically:

```text
Hive gets queens
-> queens slowly make Scourge Jelly
-> hive can buy one Harbinger
-> Harbinger makes Scourge Jelly faster
-> Harbinger unlocks specialist Scourge units
-> raids can include those specialists in late waves
```

## What Counts As Scourge

The `scourge_aliens` entity tag currently includes:

- Bursters
- Carriers
- Chrysalises
- Harbingers
- Ravagers
- Razor Claws

The tag lives at:

- `common/src/main/generated/data/avp_alien/tags/entity_type/scourge_aliens.json`

## Scourge Jelly

Scourge Jelly is a rare location resource.

It is stored per hive location, next to biomass and royal jelly.

Queens produce Scourge Jelly very slowly:

```text
queen -> +1 Scourge Jelly per 100 game-minutes
```

Harbingers produce Scourge Jelly much faster:

```text
harbinger -> +1 Scourge Jelly per game-minute
```

This makes the Harbinger the "engine" of the Scourge tier.

## Why Harbingers Matter

The hive balance system limits a location to one Harbinger.

To buy a Harbinger, a hive location needs:

- at least 100 population
- a Praetorian input
- 200 biomass
- 1 Scourge Jelly
- no existing Harbinger in that location

Once the Harbinger exists, the hive can buy other Scourge-tagged specialist forms using Scourge Jelly.

## Specialist Unlock Pattern

Most Scourge specialists require a Harbinger in the location.

Examples:

```text
Drone + Scourge Jelly + Harbinger present -> Carrier
Runner + Scourge Jelly + Harbinger present -> Burster
Runner + 2 Scourge Jelly + Harbinger present -> Razor Claw
Warrior + Scourge Jelly + Harbinger present -> Ravager
Prowler + Scourge Jelly + Harbinger present -> Chrysalis
Praetorian + Scourge Jelly + large population -> Harbinger
```

## Raids

Late raid waves can include Scourge specialists.

In the default normal raid profile:

- Wave 3 can include Chrysalis, Razor Claw, and Burster.
- Wave 4 can include Chrysalis, Razor Claw, Burster, Ravager, and Carrier.
- Wave 5 guarantees a Harbinger and can include the other specialists.

That means Scourge units are the escalation layer of raids.

## Current Important Detail

There is a `ScourgeStatusEffect`, but at the moment it is only a neutral red-particle effect class. It has brewable potion variants, but the effect class itself does not currently modify stats, deal damage, heal, or trigger extra behavior.

The main Scourge gameplay is currently handled through:

- entity tags
- Scourge Jelly production
- hive unit purchases
- raid wave composition
- Harbinger aura behavior

## Related Effects

Frenzy:

```text
+15% attack damage
+15% movement speed
```

Harbingers apply this to nearby same-hive xenomorphs.

Blood Loss:

```text
damage taken while active
-> also reduces max health by that damage amount
-> max-health reduction is removed when Blood Loss ends
```

Razor Claws apply this on successful hits.

Marked for Death:

```text
currently a harmful dark-red marker effect
```

Raids refresh it on the target player while the raid is incoming or active.
