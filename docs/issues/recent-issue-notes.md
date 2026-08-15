# Recent Issue Notes

## Issue 1: Royal Host Growth Stops At Crusher

Issue 1 is still saved as the royal-host growth problem.

Basically:

```text
royal facehugger hugs a host
-> royal adolescent happens
-> it can become a Crusher
-> then it stops if there is no hive or Metamorphosis effect
```

The reason is that the current Crusher-to-Queen path needs either:

- the `avp_alien:metamorphosis` mob effect
- or an existing hive/lineage system that can force queenless maturation

So if there are no hives in the world, the royal offspring does not have a hive system to push it into becoming a queen.

Later question:

Should a royal-host Crusher in a hive-less world automatically become the first queen/founder?

## Old World Recovery

Issue 2 is the old-world hive recovery problem.

Old worlds can have hive data in legacy `hive_data.dat` or older faction data. If that data does not attach cleanly to the new lineage/location system, xenomorphs can stop acting like hive members.

Basically:

```text
old world loads
-> old hive data is detected
-> old xenomorphs are repaired into the new hive system
-> old queens are put into a dormant recovery state
-> player decides when to wake them
```

The recovery now detects legacy hive data, stores recovery state, warns players on join, and repairs old xenomorph membership without deleting their genetics.

## Legacy Dormant Queens

Old queens are now forced into a legacy dormant state when recovery detects they are old-world queens.

Basically:

```text
old queen loads
-> recovery sees she is legacy / missing new lifecycle state / on an ovipositor
-> queen enters dormant mode
-> queen stops normal AI
-> queen stops egg-laying behavior
-> queen is kept asleep until awakened
```

The queen should also abandon/remove the old ovipositor more aggressively now:

- when she enters dormant
- while she remains dormant
- when she wakes
- if the ovipositor is still riding her
- if a nearby old ovipositor entity got left behind

## Queen Wake Commands And Founding Area

Two wake commands exist now:

```text
/avp_alien debug hive awaken_legacy_queens
/avp_alien debug hive awaken_nearest_legacy_queen
```

The first wakes all legacy dormant queens server-wide. It also queues unloaded legacy queens so they wake when they load later.

The second wakes only the nearest loaded legacy dormant queen in the command sender's current dimension.

When a legacy queen wakes:

```text
queen wakes
-> queen rejoins her repaired hive
-> if no repaired location exists, one is made at her current position
-> hive claims its 3x3 founding area
-> nearby xenomorphs join that hive
```

The 3x3 founding area means:

```text
the queen's center chunk
+ the 8 chunks around it
= the hive's starting territory
```

This claim is delayed until the queen wakes, so old hives should not fully establish their starting territory while the queen is still dormant.

## Old Reserve Members

Old reserve members are now treated as old hive members that should carry into the recovered hive.

Basically:

```text
old hive has reserve members in hiveMemberReserves
-> recovery reads those reserve counts
-> repaired hive receives them as localReserves
-> new hive systems can use them as population/reserve members
```

So old members that were not physically loaded nearby can still come over as reserves, as long as they were present in the old reserve data.

## Player Message

The old-world warning is now split into cleaner lines:

```text
[AVP] Legacy hive data detected.
Old xenomorphs are being repaired so they can rejoin the new hive system.
Old queens are dormant. They can wake from damage, interaction, or a command.
Wake all legacy queens: /avp_alien debug hive awaken_legacy_queens
Wake only the nearest loaded legacy queen: /avp_alien debug hive awaken_nearest_legacy_queen
When a legacy queen wakes, her hive claims its 3x3 founding area. That means her center chunk plus the 8 surrounding chunks, which becomes the hive's starting territory.
```

