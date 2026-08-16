package com.alien.common.gameplay.entity;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class CrawlingManager implements NBTSerializable {

    private static final String NBT_CRAWLING = "crawling";

    private static final ResourceLocation CRAWLING_MOVEMENT_SPEED_MODIFIER = AlienResources.location("crawling_movement_speed");

    /** Matches Xenomorph.SHOULDER_BREAK_RUN_FACTOR, so "running" means the same thing to both systems. */
    private static final double CHARGE_RUN_FACTOR = 1.15;

    private static final float BASE_CRAWL_SLOWDOWN = 0.1F;

    private static final float CRAWL_SLOWDOWN_PER_LOST_LIMB = 0.1F;

    private static final float MAX_CRAWL_SLOWDOWN = 0.5F;

    private final PathfinderMob entity;

    private final DataAccessor<Boolean> isCrawling;

    /**
     * Whether this entity type is permitted to crawl at all. Owned by the manager so callers (movement, animation,
     * dismemberment-eligibility) can ask one source of truth instead of poking at entity-class instanceof checks.
     */
    private final boolean canCrawl;

    /** Allows a giant caste to keep its normal standing behavior but crawl after a leg is detached. */
    private final boolean canCrawlAfterLegLoss;

    public CrawlingManager(
        PathfinderMob entity,
        DataAccessor<Boolean> isCrawling,
        boolean canCrawl,
        boolean canCrawlAfterLegLoss
    ) {
        this.entity = entity;
        this.isCrawling = isCrawling;
        this.canCrawl = canCrawl;
        this.canCrawlAfterLegLoss = canCrawlAfterLegLoss;
    }

    public boolean canCrawl() {
        return canCrawl;
    }

    public boolean canCrawlAfterLegLoss() {
        return canCrawlAfterLegLoss;
    }

    /**
     * Last crawl value this manager has OBSERVED, used for edge detection. Null until the first server tick so a
     * freshly loaded entity that saved mid-crawl does not replay a drop clip on login - the first observation just
     * records, only genuine flips fire.
     */
    private Boolean lastObservedCrawling;

    /** Game time until which the current posture transition blocks navigation and new attacks (0 = not blocking). */
    private long postureTransitionUntilTick;

    /** True while a crawl ENTER/EXIT transition clip is playing - navigation is stopped and AttackType.canUse gates. */
    public boolean isPostureTransitioning() {
        return postureTransitionUntilTick > 0L && entity.level().getGameTime() < postureTransitionUntilTick;
    }

    /** Whether the crawl is FORCED by a detached leg - the collapse case; also read by the client animators. */
    public boolean isLegForcedCrawl() {
        return canCrawlAfterLegLoss
            && entity instanceof Dismemberable dismemberable
            && hasDetachedLegLimb(dismemberable);
    }

    /**
     * Edge detection for the crawl transitions ([stated] blocking drop/rise clips; leg-loss collapse = same drop clip
     * faster, so its block is HALVED to match the doubled playback). Runs after the state write each server tick;
     * castes that don't implement the listener flip instantly, exactly as before.
     */
    private void detectPostureFlip() {
        var nowCrawling = isCrawling();
        if (lastObservedCrawling == null) {
            lastObservedCrawling = nowCrawling;
            return;
        }
        if (nowCrawling == lastObservedCrawling) {
            return;
        }
        lastObservedCrawling = nowCrawling;

        if (!(entity instanceof CrawlPostureTransitionListener listener)) {
            return;
        }
        var ticks = listener.crawlPostureTransitionTicks(nowCrawling);
        if (nowCrawling && isLegForcedCrawl()) {
            ticks = Math.max(1, ticks / 2);
        }
        if (ticks <= 0) {
            return;
        }
        postureTransitionUntilTick = entity.level().getGameTime() + ticks;
    }

    public void tick() {
        if (entity.level().isClientSide) {
            return;
        }

        if (!canCrawl && !canCrawlAfterLegLoss) {
            removeMovementSpeedModifier();
            return;
        }

        tryToCrawl();
        detectPostureFlip();
        applyMovementSpeedModifier();

        // Blocking half of the transition: the clip owns the body, so the legs don't slide under it.
        if (isPostureTransitioning()) {
            entity.getNavigation().stop();
        }
    }

    public boolean isCrawling() {
        return isCrawling.get();
    }

    public static float getCrawlSpeedMultiplier(PathfinderMob entity) {
        return 1.0F - getCrawlSlowdown(entity);
    }

    private static float getCrawlSlowdown(PathfinderMob entity) {
        return Mth.clamp(
            BASE_CRAWL_SLOWDOWN + CRAWL_SLOWDOWN_PER_LOST_LIMB * detachedArmOrLegCount(entity),
            BASE_CRAWL_SLOWDOWN,
            MAX_CRAWL_SLOWDOWN
        );
    }

    private void tryToCrawl() {
        var blockPosition = entity.blockPosition();
        var level = entity.level();
        var navigation = entity.getNavigation();

        if (level.isClientSide) {
            return;
        }

        var path = navigation.getPath();

        // THE ROUTE ITSELF ASKING TO CRAWL STILL WINS OUTRIGHT. This is the navigator saying the path it chose
        // needs a crawl to be walkable at all - a real doorway it must fit through. Ducking THAT is correct and
        // is not what the sustained test below is trying to prevent.
        var pathRequestsCrawl = entity instanceof PathNavigatorUser navigatorUser
            && navigatorUser.getPathNavigator().getPostureView().shouldCrawl();

        // ⭐⭐⭐ SUSTAINED LOW CEILING, NOT A SINGLE LOW BLOCK. [stated] "it is supposed to only crawl if the
        // entire hallways is short or it loses a leg. just going under a doorway shouldnt lock it."
        //
        // ⚠⚠ THIS USED TO BE AN OR-CHAIN across the current position, the previous path node and the next one,
        // so ONE low block anywhere in that window dropped the whole body. Combined with isTightSpace scanning
        // standingHeight - 1 of headroom, a PRAETORIAN (3.98 tall, so 3 blocks of scan) read almost every hive
        // corridor and doorway as tight and crawled essentially all the time indoors - which in turn made its
        // crawl attacks its ordinary combat mode instead of the exception they are meant to be.
        //
        // Now every sampled position must be low: the ceiling has to hold across the stretch it is standing in
        // and moving through, which is the difference between a short hallway and an arch it passes under.
        var isTight = isTightSpace(blockPosition);

        if (isTight) {
            if (path != null && path.getNextNodeIndex() < path.getNodeCount()) {
                var previousNode = path.getPreviousNode();
                if (previousNode != null) {
                    isTight = isTightSpace(previousNode.asBlockPos());
                }
                isTight = isTight && isTightSpace(path.getNextNode().asBlockPos());
            } else {
                // ⚠ STANDING STILL HAS NO PATH TO SAMPLE, so without this a praetorian parked in a doorway
                // would still read tight from its own tile alone and drop - the exact case he ruled out.
                isTight = neighboursAreAlsoLow(blockPosition);
            }
        }

        isTight = isTight || pathRequestsCrawl;

        // A dismembered leg forces the stance into crawling regardless of overhead clearance - the mob lost a leg, it
        // can't stand back up.
        var hasLegOff = canCrawlAfterLegLoss
            && entity instanceof Dismemberable dismemberable
            && hasDetachedLegLimb(dismemberable);

        // A CHARGE DOES NOT DUCK. [stated] "if its a small doorway they will crawl 'duck' under it. if its say a
        // hallway and they are pursueing something or if its just a few floating blocks in their way they will
        // smash through it."
        //
        // Without this, a running queen would drop to a crawl at the first arch or ceiling rib, and crawling puts
        // her under Xenomorph's 3.0 shoulder-break height gate - so she could neither fit nor break, and a charge
        // down a decorated corridor would stall. Standing tall keeps her above that gate, which is what lets her
        // take the arch out on the way through.
        //
        // "Running" is judged against the caste's OWN walk speed, the same way shoulderThroughObstructions judges
        // it, so a queen and a runner are each measured by their own gait. A lost leg still overrides everything -
        // she cannot stand up to charge on a missing limb.
        var wasCrawling = isCrawling.get();
        var nowCrawling = (canCrawl && isTight && !isChargingATarget()) || hasLegOff;
        isCrawling.set(nowCrawling);

        // ⚠⚠ THE HITBOX HAS TO FOLLOW THE POSTURE. Alien.getDimensions returns a shorter box while crawling, but
        // vanilla caches the bounding box - without refreshDimensions the entity keeps its old size until
        // something else happens to invalidate it, which is precisely how a "crawling" harbinger kept a 5.5-tall
        // box and wedged itself on one-block steps.
        if (wasCrawling != nowCrawling) {
            entity.refreshDimensions();
        }
    }

    /** Pursuing something and moving faster than its own walk pace - a charge, not a patrol. */
    private boolean isChargingATarget() {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || mob.getTarget() == null) {
            return false;
        }

        var motion = entity.getDeltaMovement();
        var speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        var walkSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);

        return speed > walkSpeed * CHARGE_RUN_FACTOR;
    }

    private boolean hasDetachedLegLimb(Dismemberable dismemberable) {
        var manager = dismemberable.getDismembermentManager();

        if (manager == null || !manager.hasAnyDetached()) {
            return false;
        }

        // ⭐ THE CRUSHER IS BROADER THAN EVERYONE ELSE. [stated] "if any of its limbs except tail are shot off it can
        // only crawl." Its bulk sits on all four quarters, so losing an arm topples it just as a leg does - whereas
        // every other caste only collapses on a LEG. The tail is excluded because it carries nothing.
        var collapsesOnAnyLimb = entity instanceof Crusher;

        for (var definition : LimbDefinitionRegistry.getDefinitions(entity.getType())) {
            var category = definition.category();
            var collapses = collapsesOnAnyLimb
                ? !category.equals(LimbCategories.TAIL)
                : category.equals(LimbCategories.LEG);

            if (collapses && manager.isDetached(definition)) {
                return true;
            }
        }

        return false;
    }

    private void applyMovementSpeedModifier() {
        var attributeInstance = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributeInstance == null) {
            return;
        }

        attributeInstance.removeModifier(CRAWLING_MOVEMENT_SPEED_MODIFIER);

        if (!isCrawling()) {
            return;
        }

        attributeInstance.addTransientModifier(
            new AttributeModifier(
                CRAWLING_MOVEMENT_SPEED_MODIFIER,
                -getCrawlSlowdown(entity),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
        );
    }

    private void removeMovementSpeedModifier() {
        var attributeInstance = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributeInstance != null) {
            attributeInstance.removeModifier(CRAWLING_MOVEMENT_SPEED_MODIFIER);
        }
    }

    private static int detachedArmOrLegCount(PathfinderMob entity) {
        if (!(entity instanceof Dismemberable dismemberable)) {
            return 0;
        }

        var manager = dismemberable.getDismembermentManager();

        if (manager == null || !manager.hasAnyDetached()) {
            return 0;
        }

        var count = 0;

        for (var definition : LimbDefinitionRegistry.getDefinitions(entity.getType())) {
            var category = definition.category();

            if (
                (category.equals(LimbCategories.ARM) || category.equals(LimbCategories.LEG))
                    && manager.isDetached(definition)
            ) {
                count++;
            }
        }

        return count;
    }

    /**
     * Whether the space at {@code blockPos} is too low for this mob to stand in.
     * <p>
     * This used to test ONLY {@code blockPos.above()} - a single block, one up from the feet. That is head height for a
     * player-sized mob and is correct for one, but a praetorian is 3.98 tall: the block one above its feet is its own
     * TORSO space, which is empty in any corridor it can enter at all. So the test returned false, the mob never
     * crawled, and a 4-block-tall caste simply stood at a 3-block doorway it was perfectly capable of crawling under.
     * The taller the caste, the more of its body the check ignored.
     * <p>
     * Now it scans the whole column the mob needs to STAND in - feet to {@code ceil(height)} - and reports tight if
     * anything blocks it. Crawling scales height to 40%, so a 3.98 caste drops to 1.99 and needs two blocks rather than
     * four, which is what makes a low doorway passable.
     */
    /**
     * Whether the low ceiling CONTINUES into the neighbouring tiles it could actually walk to.
     * <p>
     * Only OPEN neighbours are judged - a wall beside it says nothing about the ceiling, and counting walls as "not
     * low" would stop it ever crawling in a proper 1-wide corridor, which is precisely where it should. If every side
     * is walled (a nook barely its own size) there is nothing to corroborate with, so its own tile stands as the
     * answer.
     * </p>
     */
    private boolean neighboursAreAlsoLow(BlockPos blockPos) {
        var level = entity.level();
        var openNeighbours = 0;

        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var neighbour = blockPos.relative(direction);

            if (level.getBlockState(neighbour).entityCanStandOn(level, neighbour, entity)) {
                continue; // a wall at foot level - not somewhere it can be, so it gets no vote
            }

            openNeighbours++;

            if (!isTightSpace(neighbour)) {
                return false; // it opens out right there: a doorway or an arch, not a short hallway
            }
        }

        return true; // every open side was low too (or it is boxed in), so the ceiling genuinely holds
    }

    private boolean isTightSpace(BlockPos blockPos) {
        var level = entity.level();
        var standingHeight = Math.max(1, Mth.ceil(entity.getType().getDimensions().height()));

        for (var offset = 1; offset <= standingHeight - 1; offset++) {
            var pos = blockPos.above(offset);
            var state = level.getBlockState(pos);
            if (!state.isAir() && state.entityCanStandOn(level, pos, entity)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(NBT_CRAWLING)) {
            isCrawling.set(compoundTag.getBoolean(NBT_CRAWLING));
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putBoolean(NBT_CRAWLING, isCrawling.get());
    }
}
