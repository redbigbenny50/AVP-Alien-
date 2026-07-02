# Raid Frenzy Lab Breakout Plan

## Goal

Raid xenomorphs should be able to pull nearby valid xenomorphs into a raid when Frenzy is involved, but Frenzy alone should not drag the entire hive into the raid.

The system should also let frenzied raid xenomorphs break out contained hive assets in labs, including contained xenomorphs, chained queens, and contained eggs.

## Raid Join Rule

A xenomorph can join an active raid when all of these are true:

```text
it has Frenzy
+ it is near the active raid path, raid target, or an existing raid member
+ it matches the raid lineage/variant
+ the raid has room for extra members
+ it is not already part of another convoy/raid
-> joins the raid
```

Frenzy by itself is not enough.

This avoids the bad version:

```text
any same-hive xeno gets Frenzy
-> entire hive joins the raid
```

## Same-Type Matching

The clean matching rule should be based on raid lineage first, then variant.

Basically:

```text
if both xenos have lineage:
    they must match lineage
else:
    they must match variant
```

Examples:

```text
normal raid xeno
-> normal nearby xenos can join
-> aberrant/nether/irradiated xenos do not join

same-lineage raid xeno
-> same-lineage nearby xenos can join
-> different-lineage xenos do not join
```

## Raid Context Requirement

The xenomorph must be near the raid context.

Valid raid context examples:

- near an active raid member
- near the raid target/player
- near the raid path
- inside or close to the lab/containment area the raid is attacking

This keeps Harbinger Frenzy from pulling unrelated hive members that are nowhere near the raid.

## What Happens When A Xeno Joins

When a valid frenzied xenomorph joins:

```text
valid frenzied xeno
-> attach to raid convoy membership
-> count as a raid member
-> use raid breakout/mining behavior
-> follow or target the raid objective
```

## Contained Xenomorphs

Contained xenomorphs can join a raid if they pass the normal raid join rule.

Basically:

```text
contained xeno gets Frenzy
+ is near raid context
+ matches raid lineage/variant
+ raid has room
-> tries to break out
-> joins raid once free
```

Contained xenos should be able to break lab containment when they are valid raid joiners.

## Chained Queens

Chained or bound queens should not break themselves out.

Rule:

```text
chained queen gets Frenzy
-> queen does not mine
-> queen does not free herself
-> nearby valid frenzied raid xenos come to break her out
```

So there are two roles:

```text
breakout actor:
  normal xenomorphs that are valid frenzied raid members

breakout target:
  chained queen or contained hive member that needs help
```

Once the queen is freed, her normal queen/hive behavior decides what happens next.

## Contained Eggs

Frenzied raid xenomorphs should also detect contained eggs and break them out.

Contained eggs include:

- normal ovomorphs
- royal ovomorphs
- variant ovomorphs
- eggs inside lab containment
- eggs trapped behind containment blocks

The behavior should be:

```text
frenzied raid xeno detects contained egg
-> breaks the egg's containment
-> egg opens/hatches
-> facehugger behavior starts if a host is available
```

Royal eggs should open into royal facehugger behavior.

## Egg Containment Breaking Rule

Contained egg breakout should not be limited only to the generic `avp_alien:xenomorph_frenzy_breakable` tag.

Instead:

```text
frenzy breakout tag = general lab breakout blocks
contained egg breakout = targeted containment around eggs
```

For contained eggs, xenos should be allowed to break blocks that are directly trapping or enclosing the egg.

Allowed targets:

- blocks directly enclosing the egg
- AVP Human lab containment blocks around the egg
- blocks considered part of the egg's enclosure

Still forbidden:

- bedrock
- unbreakable blocks
- protected blocks
- random unrelated blocks far away from the egg

The point is:

```text
xeno breaks the egg out of its container
not
xeno griefs the world looking for eggs
```

## Harbinger Frenzy Concern

Harbingers currently give Frenzy to nearby same-hive xenomorphs.

So this rule is unsafe:

```text
gets Frenzy
-> joins raid
```

That would risk pulling too many hive members.

The safe version is:

```text
gets Frenzy
+ near active raid context
+ matches raid lineage/variant
+ raid has room
-> joins raid
```

## Summary

The intended flow:

```text
raid reaches lab
-> raid xenos with Frenzy scan nearby
-> matching contained xenos can join
-> valid xenos break out contained hive members
-> chained queens call for help but do not free themselves
-> valid xenos break queen containment
-> valid xenos detect contained eggs
-> egg containment is broken
-> eggs open/hatch
```

