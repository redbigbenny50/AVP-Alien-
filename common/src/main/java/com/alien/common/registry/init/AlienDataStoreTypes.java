package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.gameplay.hive.containment.PlayerVisitDataStore;
import com.blib.api.common.registry.v1.BLibBuiltInRegistries;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.blib.api.common.storage.v1.DataStore;
import com.blib.api.common.storage.v1.DataStoreType;

import java.util.function.Supplier;

/** Chunk-scoped persistent stores. Registered through BLib so the data rides the chunk's own save. */
public class AlienDataStoreTypes {

    private static final BLibRegistry<DataStoreType<?>> REGISTRY =
        Alien.MOD.registries().create(BLibBuiltInRegistries.DATA_STORE_TYPES);

    /** When a player was last in this chunk — see {@link PlayerVisitDataStore}. */
    public static final BLibHolder<DataStoreType<PlayerVisitDataStore>> PLAYER_VISIT = register(
        "player_visit",
        () -> new DataStoreType<>(PlayerVisitDataStore::new)
    );

    private AlienDataStoreTypes() {}

    private static <T extends DataStore> BLibHolder<DataStoreType<T>> register(
        String path,
        Supplier<DataStoreType<T>> supplier
    ) {
        return REGISTRY.createHolder(path, supplier);
    }

    /**
     * ⚠⚠ THIS MUST CALL {@code registerAll()}. IT PREVIOUSLY DID NOTHING AND THAT WAS A REAL BUG.
     * <p>
     * I assumed touching the class was enough because the holder is a static field. It is not: {@code createHolder}
     * only DECLARES a holder, and {@code registerAll()} is what binds the values into the registry. Without it nothing
     * was ever registered, and the first thing to touch {@link #PLAYER_VISIT} threw
     * {@code Trying to access unbound value: ResourceKey[blib:data_store_types / avp_alien:player_visit]} - which is
     * exactly what a live log showed on world load.
     * </p>
     * <p>
     * ⚠ BLib's own {@code BLibDataStoreTypes.initialize()} is one line - {@code REGISTRY.registerAll()} - and mine
     * should have been the same line. The comment I left in its place read as deliberate, which is worse than an empty
     * method: it told the next reader the emptiness was intended.
     * </p>
     */
    public static void initialize() {
        REGISTRY.registerAll();
    }
}
