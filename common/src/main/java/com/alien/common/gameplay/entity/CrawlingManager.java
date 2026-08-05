package com.alien.common.gameplay.entity;

import com.alien.AlienResources;
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

    public void tick() {
        if (entity.level().isClientSide) {
            return;
        }

        if (!canCrawl && !canCrawlAfterLegLoss) {
            removeMovementSpeedModifier();
            return;
        }

        tryToCrawl();
        applyMovementSpeedModifier();
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
        var pathRequestsCrawl = canCrawl
            && entity instanceof PathNavigatorUser navigatorUser
            && navigatorUser.getPathNavigator().getPostureView().shouldCrawl();
        var isTight = canCrawl && (pathRequestsCrawl || isTightSpace(blockPosition));

        if (canCrawl && path != null && path.getNextNodeIndex() < path.getNodeCount()) {
            var previousNode = path.getPreviousNode();
            isTight = isTight || previousNode != null && isTightSpace(previousNode.asBlockPos());
            var nextNode = path.getNextNode();
            isTight = isTight || isTightSpace(nextNode.asBlockPos());
        }

        // A dismembered leg forces the stance into crawling regardless of overhead clearance — the mob lost a leg, it
        // can't stand back up.
        var hasLegOff = canCrawlAfterLegLoss
            && entity instanceof Dismemberable dismemberable
            && hasDetachedLegLimb(dismemberable);

        isCrawling.set(isTight || hasLegOff);
    }

    private boolean hasDetachedLegLimb(Dismemberable dismemberable) {
        var manager = dismemberable.getDismembermentManager();

        if (manager == null || !manager.hasAnyDetached()) {
            return false;
        }

        for (var definition : LimbDefinitionRegistry.getDefinitions(entity.getType())) {
            if (definition.category().equals(LimbCategories.LEG) && manager.isDetached(definition)) {
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

    private boolean isTightSpace(BlockPos blockPos) {
        var level = entity.level();
        // A single block-above probe is not enough for wide/tall xenomorphs: it can report clear while a shoulder or
        // head would still intersect a low ceiling. Test the complete standing collision volume at the current/path
        // position so crawl state cannot flicker off under an overhang.
        var bounds = entity.getBoundingBox();
        var targetCenterX = blockPos.getX() + 0.5D;
        var targetCenterZ = blockPos.getZ() + 0.5D;
        var standingBounds = bounds.move(
            targetCenterX - entity.getX(),
            blockPos.getY() - bounds.minY,
            targetCenterZ - entity.getZ()
        );
        return !level.noCollision(entity, standingBounds);
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
