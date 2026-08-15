# Empress

The Empress is the top royal hive unit and is closely tied to late-hive escalation.

It is not in the `scourge_aliens` tag, but it is part of the same high-tier hive picture because large lineages use Empress state for advanced hive behavior and raids require an empress to auto-dispatch.

## How An Empress Appears

An Empress appears through an emergence ritual.

The current trigger scan looks for:

- a living lineage
- at least 2 hive locations in that lineage
- no current empress
- no emergence already in progress
- a loaded queen candidate

Then:

```text
lineage has 2+ locations
-> best queen is picked
-> queen enters emergence
-> after 30 seconds by default
-> queen is replaced by an Empress
-> lineage empress id is set
```

## During Emergence

The queen is temporarily:

- invulnerable
- no-AI
- persistent

If the queen disappears or the dimension unloads, the emergence aborts.

## Combat Stats

Current attributes:

- armor: 16
- armor toughness: 16
- attack damage: 2.5x base player health
- max health: 10x base player health
- movement speed: 0.9x base walk speed
- knockback resistance: 1.0
- follow range: 35 blocks

It uses a wide/tall xenomorph path config and can dig with 4 parallel diggers.

## Attacks

The Empress has three regular attacks:

- swipe down
- backhand
- tail strike

## Ovipositor Behavior

The Empress has an `EmpressOvipositorManager`.

That manager lets the Empress:

- create/use an ovipositor
- ride/position the ovipositor
- lay eggs from the ovipositor state

When mounted on an ovipositor, the Empress uses a different GOAP graph from her normal combat graph.

## Raid Connection

Automatic raid dispatch requires a lineage to have an empress id.

So in simple terms:

```text
no empress
-> lineage records player kills
-> but automatic raid dispatch skips it

has empress
-> player kills enough lineage members
-> raid dispatch can fire
```

## Persistence

The Empress is always persistence-required.

That means the game treats her as important and should not let normal despawn rules casually remove her.

