# Issue 1: Royal Host Growth Stops At Crusher Without A Hive

## Problem

A royal facehugger can impregnate a cow, pig, villager, player, or other valid host and produce a royal adolescent. That royal adolescent grows into a Crusher, but then stops there if no hive exists.

## Current Behavior

The current growth path is:

```text
royal_facehugger + valid host
-> royal_adolescent
-> crusher
-> stops unless Metamorphosis or hive queenless maturation happens
```

The Crusher-to-Queen growth stage requires the `avp_alien:metamorphosis` mob effect:

```text
crusher + Metamorphosis -> queen
```

The hive fallback can also force queen-track maturation, but only when a hive/lineage exists and can pick a queenless leader. If there are no hives in the world, that logic has nothing to act on.

## Why This Matters

This makes royal facehugger starts feel incomplete in fresh worlds. A royal facehugger can create a queen candidate, but the candidate does not naturally become a queen unless the player intervenes with Metamorphosis or a hive already exists.

## Questions For Later

- Should a royal-host Crusher become a Queen automatically when no hive exists?
- Should the host type matter, such as player/villager producing a Queen path while animals produce Crusher?
- Should royal facehugger offspring carry a special flag that lets them bypass the Metamorphosis requirement once?
- Should the first royal Crusher in a hive-less world found a new lineage/hive on its own?
