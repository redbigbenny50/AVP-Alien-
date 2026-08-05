package com.alien.fabric.data.dismemberment;

import com.alien.common.registry.init.AlienEntityTypes;
import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.world.entity.EntityType;

import java.util.List;

final class AlienXenomorphLimbGroups {

    static final List<Group> ALL = List.of(
        group(
            "drone",
            AlienEntityTypes.DRONE,
            AlienEntityTypes.ABERRANT_DRONE,
            AlienEntityTypes.IRRADIATED_DRONE,
            AlienEntityTypes.NETHER_DRONE
        ),
        group(
            "warrior",
            AlienEntityTypes.WARRIOR,
            AlienEntityTypes.ABERRANT_WARRIOR,
            AlienEntityTypes.IRRADIATED_WARRIOR,
            AlienEntityTypes.NETHER_WARRIOR
        ),
        group(
            "runner",
            AlienEntityTypes.RUNNER,
            AlienEntityTypes.ABERRANT_RUNNER,
            AlienEntityTypes.IRRADIATED_RUNNER,
            AlienEntityTypes.NETHER_RUNNER
        ),
        group(
            "spitter",
            AlienEntityTypes.SPITTER,
            AlienEntityTypes.ABERRANT_SPITTER,
            AlienEntityTypes.NETHER_SPITTER,
            AlienEntityTypes.IRRADIATED_SPITTER
        ),
        group(
            "praetorian",
            AlienEntityTypes.PRAETORIAN,
            AlienEntityTypes.ABERRANT_PRAETORIAN,
            AlienEntityTypes.IRRADIATED_PRAETORIAN,
            AlienEntityTypes.NETHER_PRAETORIAN
        ),
        group(
            "crusher",
            AlienEntityTypes.CRUSHER,
            AlienEntityTypes.ABERRANT_CRUSHER,
            AlienEntityTypes.IRRADIATED_CRUSHER,
            AlienEntityTypes.NETHER_CRUSHER
        ),
        group("boiler", AlienEntityTypes.BOILER, AlienEntityTypes.ABERRANT_BOILER, AlienEntityTypes.NETHER_BOILER),
        group(
            "razor_claw",
            AlienEntityTypes.RAZOR_CLAW,
            AlienEntityTypes.ABERRANT_RAZOR_CLAW,
            AlienEntityTypes.IRRADIATED_RAZOR_CLAW,
            AlienEntityTypes.NETHER_RAZOR_CLAW
        ),
        group(
            "ravager",
            AlienEntityTypes.RAVAGER,
            AlienEntityTypes.ABERRANT_RAVAGER,
            AlienEntityTypes.IRRADIATED_RAVAGER,
            AlienEntityTypes.NETHER_RAVAGER
        ),
        group(
            "prowler",
            AlienEntityTypes.PROWLER,
            AlienEntityTypes.ABERRANT_PROWLER,
            AlienEntityTypes.IRRADIATED_PROWLER,
            AlienEntityTypes.NETHER_PROWLER
        ),
        group(
            "carrier",
            AlienEntityTypes.CARRIER,
            AlienEntityTypes.ABERRANT_CARRIER,
            AlienEntityTypes.IRRADIATED_CARRIER,
            AlienEntityTypes.NETHER_CARRIER
        ),
        group(
            "chrysalis",
            AlienEntityTypes.CHRYSALIS,
            AlienEntityTypes.ABERRANT_CHRYSALIS,
            AlienEntityTypes.IRRADIATED_CHRYSALIS,
            AlienEntityTypes.NETHER_CHRYSALIS
        ),
        group(
            "predalien",
            AlienEntityTypes.PREDALIEN,
            AlienEntityTypes.ABERRANT_PREDALIEN,
            AlienEntityTypes.IRRADIATED_PREDALIEN,
            AlienEntityTypes.NETHER_PREDALIEN
        ),
        group(
            "burster",
            AlienEntityTypes.BURSTER,
            AlienEntityTypes.ABERRANT_BURSTER,
            AlienEntityTypes.IRRADIATED_BURSTER,
            AlienEntityTypes.NETHER_BURSTER
        ),
        group(
            "empress",
            AlienEntityTypes.EMPRESS,
            AlienEntityTypes.ABERRANT_EMPRESS,
            AlienEntityTypes.IRRADIATED_EMPRESS,
            AlienEntityTypes.NETHER_EMPRESS
        ),
        group(
            "harbinger",
            AlienEntityTypes.HARBINGER,
            AlienEntityTypes.ABERRANT_HARBINGER,
            AlienEntityTypes.IRRADIATED_HARBINGER,
            AlienEntityTypes.NETHER_HARBINGER
        ),
        queen(
            AlienEntityTypes.QUEEN,
            AlienEntityTypes.ABERRANT_QUEEN,
            AlienEntityTypes.IRRADIATED_QUEEN,
            AlienEntityTypes.NETHER_QUEEN
        )
    );

    private AlienXenomorphLimbGroups() {}

    @SafeVarargs
    private static Group group(String prefix, BLibHolder<? extends EntityType<?>>... entityTypes) {
        return new Group(prefix, false, List.of(entityTypes));
    }

    @SafeVarargs
    private static Group queen(BLibHolder<? extends EntityType<?>>... entityTypes) {
        return new Group("queen", true, List.of(entityTypes));
    }

    record Group(
        String prefix,
        boolean queen,
        List<BLibHolder<? extends EntityType<?>>> entityTypes
    ) {}
}
