# Proposed First-Hive Bootstrap Systems

Planning note only. This document describes two possible systems for solving the question:

```text
In a world with no hives, how does the first queen happen?
```

The goal is not to change normal xenomorph growth. Normal facehugger lines should still mostly create workers and fighters. These systems focus on royal lines and abandoned royal offspring, because those are the lifecycle pieces that already feel like they should be able to seed a hive.

## System 1: Royal Leak Into The World

### Basic Idea

If a royal chestburster or royal adolescent is outside any hive and despawns, it should not simply vanish from the story. Instead, it should count as a royal strain leak.

That leak means:

```text
a royal offspring was loose in the world
-> it disappeared outside hive control
-> the world is now allowed to create a hidden founder event later
```

Later, while a player is traveling, the leak can resolve into a new underground hive seed.

### Proposed Gameplay Flow

```text
royal chestburster or royal adolescent exists outside a hive
-> it despawns
-> the matching strain is marked as leaked
-> later, when a player is traveling through valid terrain
-> an underground queen seed event triggers
-> one queen and two drones spawn underground
-> the queen immediately starts/founds a hive
-> players receive a warning message and hear a queen screech
```

### Spawn Event

The event should feel like the old hive-spawning pressure, but with a clearer story reason:

```text
the royal line escaped detection
the strain rooted itself somewhere underground
a new hive has begun
```

Suggested spawn group:

```text
1 Queen
2 Drones
```

Suggested presentation:

```text
chat message:
"A distant screech echoes from below. Something has taken root..."

sound:
queen screech / queen alert sound
```

The message should only happen when the actual seed event fires, not every time leak progress is stored.

### Restrictions

This system should only apply when the royal offspring is not already part of a hive.

It should not trigger if:

- the royal offspring belongs to an existing hive
- the royal offspring safely returns to hive reserves
- a same-strain hive already controls the nearby area
- the spawn location is inside another hive's claimed territory

### Why This Helps

This gives the game a natural first-hive path even if the royal offspring despawns before becoming an adult.

Instead of:

```text
royal offspring despawns
-> nothing happens
```

the story becomes:

```text
royal offspring despawns outside hive control
-> the strain leaks into the world
-> a hidden queen event can happen later
```

## System 2: Royal Candidate Kill-To-Metamorphosis

### Basic Idea

If a royal facehugger line does not despawn and instead grows into a Crusher or Praetorian, that alien should become a visible queen candidate.

If it is not in a hive and has no hive to mature it, it should be able to earn Metamorphosis through kills.

### Proposed Gameplay Flow

```text
royal facehugger infects a host
-> royal chestburster
-> royal adolescent
-> Crusher or Praetorian
-> if not in a hive, it becomes a wild royal candidate
-> it must get a certain number of kills
-> after enough kills, it gains Metamorphosis
-> it can grow into a Queen
-> the Queen starts/founds a hive
```

### Kill Requirement

The exact number can be tuned later. The important design rule is:

```text
the royal candidate must prove itself before becoming a founder queen
```

Possible tuning examples:

- low tension: 3 kills
- medium tension: 5 kills
- high tension: 8-10 kills

The count should probably reset or pause if the candidate joins an existing hive, because then hive queenless maturation can handle it instead.

### Which Aliens Qualify

This should only apply to royal-line adult candidates, such as:

- royal-line Crusher
- royal-line Praetorian

It should not apply to ordinary Crushers or ordinary Praetorians.

The system needs a way to remember that the adult candidate came from a royal facehugger line. Without that memory, normal Crushers could accidentally become founder queens.

### What Happens At The Threshold

When the kill requirement is met:

```text
candidate gains Metamorphosis
candidate begins the normal queen growth/cocoon path
candidate becomes a Queen
Queen starts/founds a hive
```

This keeps the existing Metamorphosis growth rule, but gives wild royal candidates a natural way to obtain it.

### Suggested Player Feedback

The game should communicate that the royal candidate is becoming dangerous.

Possible messages:

```text
"The royal xenomorph grows restless after the kill..."
"The royal xenomorph is changing. A hive may soon begin."
"A royal metamorphosis has begun."
```

This should be restrained so it does not spam chat after every kill.

## How The Two Systems Work Together

These two systems cover both failure cases:

```text
royal offspring despawns outside hive
-> leak system can seed a hidden queen later
```

```text
royal offspring survives and grows into adult candidate
-> kill-to-Metamorphosis system can make it become Queen
```

Together, they answer the first-hive problem without making every normal xenomorph capable of founding a hive.

## Design Goals

- Make royal facehugger starts feel complete.
- Give zero-hive worlds a believable first-queen path.
- Preserve normal xenomorph growth as worker/fighter growth.
- Avoid ordinary Crushers randomly becoming queens.
- Keep strain identity important.
- Avoid spawning queens inside existing hive territory.
- Give players warning when a new hive begins.

## Open Questions

- Should the leak event spawn exactly two drones, or should that scale with difficulty?
- Should the leak event use the old queen natural spawn cooldown?
- Should the leak event be per dimension or global per world?
- Should a leaked royal strain expire after enough time?
- How many kills should a wild royal candidate need before receiving Metamorphosis?
- Should player kills count more than animal kills?
- Should the candidate need to be fully grown before kills count?
- Should the candidate become more aggressive as it approaches Metamorphosis?
