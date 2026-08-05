# Limb Hitbox System — Working Design

## Goal

Extend, rather than replace, Minecraft's normal entity hitbox. Firearms can also hit supported animated limbs. The first complete target is the Praetorian; later work can generate definitions for AVP mobs, vanilla mobs, and compatible modded mobs. Unsupported or unrecognised mobs keep normal vanilla hit detection.

The initial gameplay rules are:

- Headshots deal bonus damage but heads never detach.
- Arms, legs, and tails have persistent limb-health pools and can detach.
- A detached limb immediately stops accepting limb hits and disappears visually.
- Lost legs trigger the existing crawling behaviour.
- This applies to mobs, not players.
- The vanilla body hitbox remains active.

## Authority and Networking

The server is authoritative for every gameplay result: firearm raycasts, damage, limb-pool accumulation, detachment, AI changes, persistence, and removal of detached limb hitboxes.

Clients must never send model cube positions, bone matrices, animation transforms, hitbox shapes, or authoritative damage. A client is only responsible for rendering the state replicated by the server.

For the server-only version, no client limb-selection packet is required. If a later client-assist mode is considered, it must remain optional and the server must independently validate it; it is not part of the baseline system.

## Server Hitbox Evaluation

The server calculates additive limb volumes from a supported entity's registered model definition and animation pose. The normal entity box continues to handle ordinary body hits.

The intended query path is:

1. Broad phase: find supported entities close to the firearm ray, using each type's cached maximum limb reach.
2. Limb envelope: reject limbs whose conservative server envelope cannot intersect the ray.
3. Exact limb volumes: test only the candidate limb's posed model cubes or grouped tail segments.
4. Apply server-side normal health damage, limb damage, and detachment.

Definitions and static geometry are loaded once at resource/model load or reload time. They are never generated during combat. Dynamic posed volumes are cached once per entity per server tick and reused by multiple pellets or shots in that tick.

## Visual Forgiveness Margin

Small server-side margins compensate for client interpolation, server tick boundaries, and fast tail motion. They enlarge each individual posed cube or tail segment, never the entire limb as one huge box.

Initial tuning targets:

| Limb | Base margin |
| --- | --- |
| Head | 0.08–0.12 blocks |
| Arm / leg | 0.12–0.18 blocks |
| Tail segment | 0.18–0.25 blocks |

The final margin may add a small, capped allowance derived from movement of that limb between server ticks. Shotguns can receive a very small additional pellet allowance. Margins must never be large enough to permit hits through nearby cover or empty space.

## Animation Clock Options

### Baseline: independent server pose with forgiveness

The server evaluates a deterministic pose from server-owned state such as movement, crawl state, attack type, attack start tick, duration, and entity transform. The client continues to render AVP animations normally. The forgiveness margins cover small visual disagreement.

Benefits:

- Fully server authoritative.
- No pose packets and no client trust.
- Does not alter AVP renderer timing or animation playback.
- Good first release path.

Cost:

- It cannot be literally frame-identical to a renderer whose animation time is local/client-based. A fast tail can still be slightly different from what one client sees.

### Future upgrade: shared deterministic animation clock

Use one common pose/timeline evaluator for both server limb hitboxes and client rendering. The server owns the animation state and start tick; both sides evaluate the same pose from those values. This is not a per-frame pose stream.

Benefits:

- Best visual/hitbox agreement.
- Still server authoritative.
- No bone transforms, cube data, or hitbox shapes sent every frame.

Cost and requirement:

- It requires the existing AVP animation runtime to expose and use a shared deterministic clock/state source.
- If AVP's current renderer instead starts or advances an animation from local rendering time, merely adding server hitboxes cannot make the result exact.
- This must be introduced without changing animation speed, AI, animation selection, or authored animation data; it is a timing-source integration project and must be tested against every existing Praetorian animation.

## Auto-generation and Fallback

The generator reads model groups once and recognises semantic names and descendants, including AVP names such as `gTail1`. It produces a cached limb definition only if the group structure is valid, has usable three-dimensional geometry, and maps safely to gameplay rules.

Failures are safe: no generated limb definition means that entity uses its normal vanilla hitbox only. Modded support should be adapter-based; it must not assume every renderer exposes a usable pose.

## Debugging

F3+B limb debug rendering must use the exact same posed server-volume representation used by firearm validation, not a second independently evaluated debug representation. Detached limbs must disappear from both the gameplay volume set and the debug overlay immediately.

## Do Not Repeat

- Do not restore the removed old all-mob collision system.
- Do not run model parsing or auto-generation per shot or per entity tick.
- Do not send bone/cube transforms each frame.
- Do not calculate posed hitboxes for every mob globally every tick.
- Do not modify AVP animation speed, animation selection, AI timing, or authored animation assets as a shortcut for hitbox sync.
