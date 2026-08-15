package com.alien.common.util;

import com.alien.common.gameplay.entity.CrawlingManager;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzCommandBuilder;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehavior;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;
import com.blib.api.client.animation.v1.command.policy.OnPropertiesChanged;
import com.blib.api.client.animation.v1.track.AzTrackHandle;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class AzAlienAnimationUtil {

    private static final float MAX_CRAWL_ANIMATION_SPEED = 2.0F;

    // Xeno movement attributes use BLib's scaled player walk speed, while position deltas remain in vanilla units.
    private static final float ATTRIBUTE_TO_POSITION_DELTA_SCALE = 0.1F / PlayerStatConstants.BASE_WALK_SPEED;

    private static final float MOVEMENT_EPSILON = 1.0E-4F;

    public static final AzTrackHandle<Alien> BODY = AzTrackHandle.declare("body");

    public static final AzTrackHandle<Alien> HEAD = AzTrackHandle.declare("head");

    public static final AzTrackHandle<Alien> LEFT_ARM = AzTrackHandle.declare("leftarm");

    public static final AzTrackHandle<Alien> LEFT_LEG = AzTrackHandle.declare("leftleg");

    public static final AzTrackHandle<Alien> LEFT_TITTY_ARM = AzTrackHandle.declare("lefttittyarm");

    public static final AzTrackHandle<Alien> LEFT_WHIP = AzTrackHandle.declare("leftwhip");

    public static final AzTrackHandle<Alien> RIGHT_ARM = AzTrackHandle.declare("rightarm");

    public static final AzTrackHandle<Alien> RIGHT_LEG = AzTrackHandle.declare("rightleg");

    public static final AzTrackHandle<Alien> RIGHT_TITTY_ARM = AzTrackHandle.declare("righttittyarm");

    public static final AzTrackHandle<Alien> RIGHT_WHIP = AzTrackHandle.declare("rightwhip");

    public static final AzTrackHandle<Alien> TAIL = AzTrackHandle.declare("tail");

    public static final List<AzTrackHandle<Alien>> XENO_LIMBS = List.of(
        BODY,
        HEAD,
        LEFT_ARM,
        LEFT_LEG,
        RIGHT_ARM,
        RIGHT_LEG,
        TAIL
    );

    public static final List<AzTrackHandle<Alien>> XENO_QUEEN_LIMBS = List.of(
        BODY,
        HEAD,
        LEFT_ARM,
        LEFT_LEG,
        LEFT_TITTY_ARM,
        RIGHT_ARM,
        RIGHT_LEG,
        RIGHT_TITTY_ARM,
        TAIL
    );

    public static final List<AzTrackHandle<Alien>> XENO_EMPRESS_LIMBS = List.of(
        BODY,
        HEAD,
        LEFT_ARM,
        LEFT_LEG,
        LEFT_TITTY_ARM,
        RIGHT_ARM,
        RIGHT_LEG,
        RIGHT_TITTY_ARM,
        TAIL
    );

    public static float crawlAnimationSpeed(Alien alien) {
        var crawlSpeedMultiplier = CrawlingManager.getCrawlSpeedMultiplier(alien);
        var movementSpeed = actualHorizontalMovementSpeed(alien);

        if (movementSpeed <= MOVEMENT_EPSILON) {
            return crawlSpeedMultiplier;
        }

        var currentSpeedAttribute = (float) alien.getAttributeValue(Attributes.MOVEMENT_SPEED);
        var uncrawledSpeedAttribute = currentSpeedAttribute / crawlSpeedMultiplier;
        var referenceSpeed = Math.max(0.01F, uncrawledSpeedAttribute * ATTRIBUTE_TO_POSITION_DELTA_SCALE);
        return Mth.clamp(
            movementSpeed / referenceSpeed,
            crawlSpeedMultiplier,
            MAX_CRAWL_ANIMATION_SPEED
        );
    }

    private static float actualHorizontalMovementSpeed(Alien alien) {
        var deltaX = alien.getX() - alien.xOld;
        var deltaZ = alien.getZ() - alien.zOld;
        var positionSpeed = deltaX * deltaX + deltaZ * deltaZ;
        var movement = alien.getDeltaMovement();
        var velocitySpeed = movement.x * movement.x + movement.z * movement.z;
        return Mth.sqrt((float) Math.max(positionSpeed, velocitySpeed));
    }

    public static <T extends Alien> AzCommand<T> composeWithSpeed(
        List<? extends AzTrackHandle<? super T>> handles,
        String baseName,
        AzPlayBehavior playBehavior,
        AzDispatchMode dispatchMode,
        float speed
    ) {
        return AzCommand.compose(
            handles.stream()
                .map(
                    handle -> AzAlienAnimationUtil.<T>builderFor(dispatchMode)
                        .onPropertiesChanged(OnPropertiesChanged.UPDATE_IN_PLACE)
                        .play(handle, baseName + "." + handle.name(), playBehavior)
                        .setSpeed(handle, speed)
                        .build()
                )
                .toList()
        );
    }

    public static <T extends Alien> AzCommand<T> singleWithSpeed(
        AzTrackHandle<? super T> handle,
        String animationName,
        AzPlayBehavior playBehavior,
        AzDispatchMode dispatchMode,
        float speed
    ) {
        return AzAlienAnimationUtil.<T>builderFor(dispatchMode)
            .onPropertiesChanged(OnPropertiesChanged.UPDATE_IN_PLACE)
            .play(handle, animationName, playBehavior)
            .setSpeed(handle, speed)
            .build();
    }

    private static <T extends Alien> AzCommandBuilder<T> builderFor(AzDispatchMode mode) {
        return switch (mode) {
            case REPLAY -> AzCommand.replay();
            case PLAY_IF_NOT_PLAYING -> AzCommand.idempotent();
            case ENQUEUE -> AzCommand.enqueueing();
        };
    }
}
