package com.alien.common.gameplay.hive.empress;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;

/**
 * One rule for every ceiling an empress raises: a flat percentage on the BASE cap.
 * <p>
 * This replaces four hand-tuned {@code ...MaxSizeEmpress} knobs and a hardcoded 250 -> 400 member cap, and it now also
 * carries EVERY PER-CASTE ceiling: the {@code max_entity_count_in_location} condition on all 33 hive unit purchases
 * runs through here, so warriors, praetorians, crushers, spitters and the rest all grow together. That is the
 * difference between an empress meaning "more workers" and an empress meaning a bigger ARMY.
 * <p>
 * Separate empress values meant every new cap had to remember to grow an empress twin, and the ones that already
 * existed had drifted into unrelated ratios (the member cap was +60% while host-hunt was +50%). A single multiplier
 * makes "an empress hive is half again bigger" a property of the system rather than a list of numbers, so a cap added
 * later scales for free.
 * <p>
 * The harbinger cap is deliberately NOT routed through here. It is not a number an empress multiplies - it is one per
 * raid chamber, and she raises it by giving the hive a second chamber. That stays an emergent consequence of the bigger
 * footprint rather than another scalar.
 */
public final class EmpressCaps {

    private EmpressCaps() {}

    /**
     * {@code baseCap} for an ordinary hive, or the raised ceiling if this hive is under empress influence.
     * <p>
     * Never returns less than the base: a percentage below 100 would otherwise let a misconfiguration PUNISH a hive for
     * having an empress, which is never the intent.
     */
    public static int scale(HiveLocation location, int baseCap) {
        if (!location.isEmpressInfluenced()) {
            return baseCap;
        }
        var percent = HiveLocationRegistry.INSTANCE.config().empressCapPercent();
        return Math.max(baseCap, (int) Math.round(baseCap * (percent / 100.0D)));
    }
}
