# Alien Territory War Expansion Ideas

## Summary

These are future design ideas for making alien territory war feel more alive, readable, and dangerous.

This is not a list of currently implemented behavior. The current system already has BLib territory contests, player claim warnings, contested chunk map/HUD support, and AVP-Alien hive rivalry rules. These ideas describe ways to expand that foundation later.

BLib should stay responsible for shared claim ownership, contest state, player warnings, map overlays, HUD/actionbar text, and generic contest rules.

AVP-Alien should stay responsible for alien behavior: which hive attacks, how hives remember enemies, how raids support war, how queens and empresses influence politics, and what aliens do when they pressure a claim.

## Contest Escalation Stages

Right now, a chunk is mostly either contested or not contested. A future system could make contest pressure escalate through clearer stages so players can feel the situation getting worse.

Possible stages:

- `Border Pressure`
- `Active Contest`
- `Raid Imminent`
- `Claim Breach`
- `Claim Lost`

The goal is to make the player understand the danger curve:

```text
hive notices border
-> hive applies pressure
-> chunk becomes contested
-> hive sends bodies/objectives
-> claim starts losing ground
-> claim falls
```

This would make territory war feel less like a silent ownership flip and more like a visible biological siege.

## Owner-Facing Contest Details

Players should understand what is happening to their claims without needing debug tools or Xaero.

Useful owner-facing details:

- contested chunk coordinates
- dimension
- attacker name
- defender/claim owner name
- whether the player is winning, losing, or holding even
- rough threat level instead of raw debug numbers

Example actionbar states:

```text
CONTESTED - Claim: Shay at chunk 12, -4 - Holding
CONTESTED - Claim: Shay at chunk 12, -4 - Losing Ground
CONTESTED - Claim: Shay at chunk 12, -4 - Overwhelmed
```

Suggested readable threat labels:

- `Holding`
- `Even Fight`
- `Losing Ground`
- `Overwhelmed`

The system should avoid dumping raw contest power values to normal players unless a debug mode is enabled.

## Hive War Memory

Hives should remember important conflicts instead of treating every nearby claim as a fresh blank target.

Things a hive could remember:

- a player repeatedly killed hive members
- a player claim resisted a contest
- a player claim was successfully captured
- a rival hive stole territory
- a rival hive killed the queen
- a hive lost a chunk and wants it back
- an enemy lineage keeps appearing near the border

This memory could influence future behavior:

- attack a hated player claim sooner
- prioritize reclaiming lost chunks
- send larger pressure groups against repeat enemies
- avoid wasting pressure on claims that are too heavily defended
- make old rival lineages more likely to fight again

The goal is for hives to feel like colonies with grudges and survival instincts, not random area markers.

## War Objectives Beyond Chunks

Not every war action has to be `take this chunk`.

Future alien objectives could include:

- weaken a player claim before contesting it
- resin over border areas
- destroy lights, doors, or defensive blocks
- break into a lab or containment structure
- capture a cave entrance or tunnel route
- kill or free a queen
- cut off a hive by taking connecting chunks
- recover previously lost territory
- force a player to abandon a border claim

This would let territory war create stories:

```text
hive pressures border
-> drones resin nearby cave
-> warriors attack doors/lights
-> contest starts
-> raid arrives to hold the chunk
-> claim falls if the player cannot push them out
```

## Queen And Empress Diplomacy Rules

The empress should matter as more than a stronger queen. She should shape hive politics.

Possible rules:

- different lineages are usually rivals
- same-strain hives can still fight if they are different bloodlines
- queenless hives may be absorbed by a stronger nearby hive
- weak hives may submit to a nearby empress
- hives under the same empress do not fight each other
- rival empresses create major war zones
- killing an empress can fracture her controlled hives
- a new empress can unify nearby same-strain or compatible lineages

Possible outcomes:

```text
queenless hive near strong queen
-> adopted, ignored, or consumed

two queens near each other
-> border war

empress emerges
-> nearby hives submit or resist

empress dies
-> old unified hives may split back into rivals
```

This would make empress emergence feel like a biological empire event.

## Player Claim Defense Tools

If hives can attack player claims, players need clear ways to respond.

Possible player-facing tools:

- claim alarm text
- persistent contested actionbar
- claim defense status
- temporary reinforcement or ward blocks
- anti-resin cleanup tools
- contested claim marker/compass/waypoint
- stronger defense power when the owner or allies are physically present
- clearer feedback when player presence is helping the claim hold

Possible message examples:

```text
Your presence is helping the claim hold.
The hive is losing pressure at chunk 12, -4.
The hive is overwhelming this claim.
```

The important part is fairness: if the game lets hives take player territory, players need readable warnings and meaningful response options.

## Map Pressure Overlay

The current contested chunk blink is a good first step. The map could eventually show more detail about pressure.

Possible overlay upgrades:

- contested chunks blink red
- pressured border chunks pulse darker
- losing chunks flash faster
- attacker/defender names in tooltip
- rough status in tooltip
- warzone label for clusters of active contests
- different border style for hive pressure vs player pressure

Example tooltip:

```text
CONTESTED - Claim: Shay
Chunk: 12, -4
Attacker: Nether Hive 2
Status: Losing Ground
```

BLib should own the generic map/HUD display. AVP-Alien should provide alien-specific names, pressure, and contest causes through the shared territory systems.

## Alien Raid-To-Contest Bridge

Raids currently work mainly as player-retaliation convoys. A future war system could connect raids to territory contests.

Possible flow:

```text
hive starts contest
-> contest stalls or hive is losing
-> hive dispatches a pressure raid to the contested chunk
-> raid aliens hold the area
-> their presence increases contest power
-> claim falls if defenders cannot clear them
```

This would make raids feel like part of territorial war instead of only player punishment.

Important constraints:

- a raid should have a clear objective
- raid members should count as pressure only near the contested area
- Frenzy should not pull an entire hive into every raid
- hives should not endlessly spam raids into unwinnable claims
- player-visible warnings should explain when a raid is tied to claim pressure

## Recommended Next Priority

The next best expansion is probably:

```text
contest escalation
+ owner-facing winning/losing status
```

Reason:

- it improves player clarity immediately
- it builds on the existing BLib contest state
- it does not require a full new hive AI layer
- it makes current territory war easier to debug and balance
- it gives later raids/objectives a clear status system to plug into

Suggested first version:

- keep BLib contest resolution as-is
- expose whether a player claim is holding, even, losing, or overwhelmed
- show that status in actionbar and start/update messages
- optionally expose the same status in Xaero tooltip
- keep raw power numbers hidden outside debug tools

## Long-Term Goal

Alien territory should feel dangerous and active.

If a hive is near a player claim, the player should feel the hive testing the border, applying pressure, and trying to spread.

If two hives meet, it should feel like a biological war for dominance.

If an empress emerges, nearby hives should either submit, resist, or be crushed.

The map should not just show static colored territory. It should show pressure, danger, rivalry, and the spread of living colonies.
