package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.client.render.entity.carrier.CarrierSpineBoneCache;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.CarrierAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.CarrierSpine;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class CarrierAnimator extends AzEntityAnimator<Carrier> {

    private static final String NAME = "carrier";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private double prevEntityX;

    private double prevEntityY;

    private double prevEntityZ;

    private boolean hasPrevEntityPosition;

    /**
     * This caste authors an EMERGE-oriented molt clip ({@code molt.emerge}) rather than the {@code molt.enter} most
     * castes ship, so it stays on the emerge-first path: the clip plays forwards to emerge and backwards to cocoon in.
     * Left on the default it would ask for a {@code molt.enter} that does not exist.
     */
    /**
     * ⚠⚠ THE LOOP NAME WAS A LITERAL `"molting"` AND THE ART HAS NEVER CONTAINED IT — the loop half of every carrier
     * molt was bind-posing, silently. Now named through the Refs so a future rename is a one-line change.
     * <p>
     * Emerge-oriented: the carrier is a molt DESTINATION only, so it has no enter clip and the tracker makes the
     * cocooning half by reversing the emerge.
     * </p>
     */
    private final CocoonAnimationStateTracker<Carrier> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        carrier -> CarrierAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        carrier -> CarrierAnimationRefs.MOLT_EMERGE_ANIMATION_NAME
    );

    /**
     * ⭐⭐ THE JUMP DIAL. Ticks airborne before the jump clip plays - RAISE IT if carriers look like they are hopping
     * over every stair and slab. Same figure as every other caste.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks;

    private boolean jumpPlayed;

    /** Latched so the collapse is dispatched ONCE - re-sending it every frame would restart the clip forever. */
    private boolean collapsePlayed;

    /** Edge detection for the BLOCKING crawl transitions - null until the first posture is observed. */
    private Boolean previousCrawling;

    public CarrierAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Carrier> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Carrier animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Carrier animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
        updateSpineBoneData(animatable);
    }

    private void runPassiveAnimations(Carrier carrier) {
        var dispatcher = carrier.getAnimationDispatcher();

        // ⭐ DYING BEATS EVERYTHING. `Carrier.die` releases the whole brood, and this is the body dropping as they
        // scatter - so it must outrank the gait, the attacks and the crawl, all of which would otherwise keep
        // animating a corpse. `deathTime` is driven client-side off the vanilla death event, so no sync is needed.
        if (carrier.deathTime > 0) {
            if (!collapsePlayed) {
                // ⚠ POSTURE READ ONCE, HERE. The latch means a body that settles out of its crawl partway through
                // dying keeps the clip it started - it cannot swap mid-collapse.
                dispatcher.collapse(carrier.getCrawlingManager().isCrawling());
                collapsePlayed = true;
            }
            return;
        }

        var attackType = carrier.attackType.get();
        var attackId = carrier.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                // ⚠ POSTURE FIRST. Underwater it has its own mirrored swim attack; crawling it must use the crawl
                // clips or the body snaps upright for the swing.
                if (carrier.isUnderWater() && attackType != Carrier.THROW && attackType != Carrier.SCREAM)
                    dispatcher.swimAttack();
                else if (
                    carrier.getCrawlingManager().isCrawling()
                        && attackType != Carrier.THROW
                        && attackType != Carrier.SCREAM
                ) {
                    if (attackType == Carrier.CRAWL_BITE)
                        dispatcher.crawlBiteAttack();
                    else
                        dispatcher.crawlAttack(calculateAttackSpeed(carrier, attackType));
                } else if (attackType == Carrier.BITE)
                    dispatcher.biteAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.CLAW)
                    dispatcher.clawAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.TAIL)
                    dispatcher.tailAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.THROW)
                    dispatcher.throwAttack();
                else if (attackType == Carrier.SCREAM)
                    dispatcher.screamAttack();

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = carrier.getCrawlingManager().isCrawling();

        // ⭐ THE CRAWL TRANSITIONS. Both are BLOCKING: dispatched once on the flip and allowed to finish.
        // ⚠ A LOST LEG PLAYS THE SAME DROP CLIP FASTER rather than a different clip.
        if (previousCrawling == null) {
            previousCrawling = isCrawling;
        } else if (previousCrawling != isCrawling) {
            previousCrawling = isCrawling;

            if (isCrawling) {
                dispatcher.crawlDrop(carrier.getCrawlingManager().isLegForcedCrawl() ? 2.0F : 1.0F);
            } else {
                dispatcher.crawlRise();
            }
            return;
        }

        // ⭐ AIRBORNE. A TIME floor, not a height one - see AIRBORNE_TICKS_BEFORE_JUMP above.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(carrier.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!carrier.onGround() && verticallyAirborne && !carrier.isUnderWater() && !isCrawling) {
            airborneTicks++;

            // Fire ONCE, on the tick the count EQUALS the floor - re-dispatching restarts the clip and freezes it.
            if (airborneTicks == AIRBORNE_TICKS_BEFORE_JUMP) {
                dispatcher.jump();
                jumpPlayed = true;
                return;
            }

            if (jumpPlayed) {
                return; // holding the last frame until the ground comes back
            }
        } else {
            if (jumpPlayed) {
                dispatcher.land();
                jumpPlayed = false;
                airborneTicks = 0;
                return;
            }

            airborneTicks = 0;
        }

        var isMovingOnGround = carrier.isMovingHorizontally.get() && carrier.onGround();
        Runnable animFunction;

        if (carrier.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(carrier));
            } else if (carrier.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private void updateSpineBoneData(Carrier carrier) {
        if (!hasPrevEntityPosition) {
            saveCurrentEntityPosition(carrier);
            CarrierSpineBoneCache.remove(carrier.getId());
            return;
        }

        var bakedModel = context().boneCache().getBakedModel();
        var offsets = new Vector3d[CarrierSpine.COUNT];
        var rotations = new Vector3f[CarrierSpine.COUNT];

        for (var spine : CarrierSpine.values()) {
            var bone = bakedModel.getBoneOrNull(spine.getBoneName());

            if (bone != null) {
                bone.setTrackingMatrices(true);
                var worldPos = bone.getWorldPosition();

                // Matrix tracking is updated after this animation pass, during model rendering. The world position
                // available here is therefore from the previous render and includes the carrier's tick position from
                // that same render. Subtract that tick position, not the interpolated render position, so movement
                // interpolation is applied exactly once by the facehugger renderer.
                offsets[spine.getPassengerIndex()] = new Vector3d(
                    worldPos.x - prevEntityX,
                    worldPos.y - prevEntityY,
                    worldPos.z - prevEntityZ
                );

                rotations[spine.getPassengerIndex()] = new Vector3f(
                    bone.getRotX(),
                    bone.getRotY(),
                    bone.getRotZ()
                );
            }
        }

        CarrierSpineBoneCache.put(carrier.getId(), offsets, rotations);
        saveCurrentEntityPosition(carrier);
    }

    private void saveCurrentEntityPosition(Carrier carrier) {
        prevEntityX = carrier.getX();
        prevEntityY = carrier.getY();
        prevEntityZ = carrier.getZ();
        hasPrevEntityPosition = true;
    }

    private float calculateAttackSpeed(Carrier carrier, AttackType attackType) {
        String animationName;

        if (attackType == Carrier.BITE)
            animationName = CarrierAnimationRefs.ATTACKBITE_ANIMATION_NAME;
        else if (attackType == Carrier.CLAW)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = CarrierAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == Carrier.TAIL)
            animationName = CarrierAnimationRefs.ATTACKTAIL_ANIMATION_NAME;
        else if (attackType == Carrier.SCREAM)
            animationName = CarrierAnimationRefs.SPECIAL_ATTACK_SCREAM_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = carrier.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(carrier, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
