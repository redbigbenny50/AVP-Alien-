GROWTH SUPPRESSION SYSTEM (poison jelly -> potion, jelly sickness, the withered)
================================================================================
11 files, full replacements at repo paths. Requires `runDatagen` (lang entries)
then a normal rebuild.

WHAT CHANGED
- Poison jelly: direct use on xenos REMOVED - now purely a brewing ingredient.
- NEW Potion of Growth Suppression: Awkward + Poison Jelly. Single tier.
  On a xeno: freezes caste/growth (persistent) until a Metamorphosis potion.
  On a host with a chestburster: resets the burst clock to 5 MC days remaining
  (never stacks - dose late!). Each dose: 32% +8%/dose jelly sickness risk;
  hits ratchet Jelly Sickness I -> IV (IV wither-grade). At IV, the next dose
  grants no time and flips the coin:
    - Death sentence: embryo marked WITHERED, Wither II 30s executes you.
      Cheat it with milk/heals and you live - but the potion is spent, further
      doses print "You feel movement in your chest, the potions no longer
      effective", and the withered burster comes on the remaining clock. Death
      by ANY cause after the mark chest-bursts the withered burster on death.
    - Mercy: the burster dies; you endure the same Wither II 30s.
  All hidden counters are per-implantation (reset when the embryo leaves).
- Metamorphosis: long/strong potions REMOVED (untiered on/off accelerant; old
  bottles become "uncraftable potion"). Applying it now clears growth
  suppression on xenos, and on an implanted host triggers IMMEDIATE bursting.
- THE WITHERED: persistent mark that rides every molt (burster->adult->queen).
  Wither-immune (already tag-covered), black smoke aura, melee inflicts
  Wither I 10s like a wither skeleton.

NOT IN THIS DELIVERY (stage 2, next):
- Queen egg-layer inheritance: hive members born (host-born or reserve) under a
  withered egg-laying queen becoming withered themselves. Needs the hive spawn
  choke points mapped; the withered flag and carry mechanics this delivery adds
  are the foundation it will plug into.

QUICK TESTS
1. Brew: Awkward + Poison Jelly -> Growth Suppression (single tier only).
2. Splash a drone: it never molts again; splash Metamorphosis: growth resumes.
3. Get hugged, let the burster near term, drink: timer visibly resets (heartbeat
   cues stop); repeat doses until sickness appears and ratchets I->IV.
4. At IV drink again: either Wither II kills you (burster pops out withered -
   black smoke, wither-inflicting bites) or you survive and the embryo is gone.
5. Drink milk during the death sentence, survive, drink again: chest-movement
   message, no time granted, withered burster on schedule - or on your death.
6. Metamorphosis while implanted: instant burst.
