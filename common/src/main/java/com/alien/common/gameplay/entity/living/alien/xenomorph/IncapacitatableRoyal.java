package com.alien.common.gameplay.entity.living.alien.xenomorph;

/**
 * ⭐⭐⭐ A ROYAL THAT CAN BE PUT ON THE FLOOR AND FINISHED.
 * <p>
 * [stated] the empress gets "the same as the queen without the chain binding and the inhibitor - the bar the rescue the
 * finishing off all of it."
 * </p>
 * <p>
 * ⚠⚠ THIS EXISTS SO THE MANAGER IS SHARED RATHER THAN COPIED. The incapacitation logic is four hundred lines of
 * carefully tuned behaviour — the bar, the down window, MAX_DOWNS, the rescue channel, who may work the finisher, the
 * beheading rule. A second copy for the empress would drift from the queen's the first time either was touched, and
 * every fix would have to be made twice by someone who remembered there were two.
 * </p>
 * <p>
 * ⭐ ONLY THREE THINGS ARE ACTUALLY ROYAL-SPECIFIC — the downed flag and giving up the eggsack. Everything else the
 * manager needs (health, navigation, bounding box, variant, head state) already lives on {@code Alien}, which is why
 * the interface is this small.
 * </p>
 * <p>
 * ⚠ CHAINING AND INHIBITORS ARE DELIBERATELY ABSENT. The queen's capture arc is not part of being incapacitated — it is
 * a separate mechanic that happens to use the same moment, and the empress has neither.
 * </p>
 */
public interface IncapacitatableRoyal {

    boolean isIncapacitated();

    void setIncapacitated(boolean value);

    /** Drops the eggsack when she goes down. Detach, never destroy — a rescued royal keeps her production. */
    void abandonOvipositorForIncapacitation();

    /**
     * ⚠ Her ordinary regeneration, exposed so the incapacitation bar can be paced by it.
     * <p>
     * {@code Alien.getHealthRegenPerSecond} is protected, and the incapacitation manager is a separate class - but the
     * bar has to fill at the speed she would actually heal, or a buffed royal recovers faster than an unbuffed one
     * instead of slower.
     * </p>
     */
    float healthRegenPerSecondForRecovery();
}
