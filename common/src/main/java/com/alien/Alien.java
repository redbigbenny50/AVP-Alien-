package com.alien;

import com.alien.common.data.AlienReloadListeners;
import com.alien.common.data.fixer.migration.AlienDataMigrations;
import com.alien.common.gameplay.advancement.AlienAdvancementEvents;
import com.alien.common.gameplay.entity.dismemberment.AlienLimbDefinitions;
import com.alien.common.gameplay.entity.dismemberment.AlienLimbDrops;
import com.alien.common.gameplay.hive.growth.ResinDecorator;
import com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.network.AlienNetworking;
import com.alien.common.network.HeadAttachmentSync;
import com.alien.common.property.AlienPropertyAccess;
import com.alien.common.registry.GrowthStageRegistry;
import com.alien.common.registry.InfectionRegistry;
import com.alien.common.registry.init.AlienArmorMaterials;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.alien.common.registry.init.AlienCommands;
import com.alien.common.registry.init.AlienCompostingChances;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienDecoratedPotPatterns;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.alien.common.registry.init.AlienGameEvents;
import com.alien.common.registry.init.AlienGameRules;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienParticleTypes;
import com.alien.common.registry.init.AlienPotions;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.creative_mode_tab.AlienCreativeModeTabs;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienSpawnEggItems;
import com.alien.common.registry.init.item.block.AberrantAlienChitinBlockItems;
import com.alien.common.registry.init.item.block.AberrantAlienResinBlockItems;
import com.alien.common.registry.init.item.block.AlienBlockItems;
import com.alien.common.registry.init.item.block.AlienChitinBlockItems;
import com.alien.common.registry.init.item.block.AlienResinBlockItems;
import com.alien.common.registry.init.item.block.IrradiatedAlienChitinBlockItems;
import com.alien.common.registry.init.item.block.IrradiatedAlienResinBlockItems;
import com.alien.common.registry.init.item.block.NetherAlienChitinBlockItems;
import com.alien.common.registry.init.item.block.NetherAlienResinBlockItems;
import com.blib.api.BLibAPI;
import com.blib.api.common.mod.v1.BLibMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Alien {

    public static final String MOD_ID = "avp_alien";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final BLibMod MOD = BLibAPI.createMod(MOD_ID);

    public static void initialize() {
        LOGGER.info("Initializing AVP (Alien) for mod loader '{}'", BLibAPI.getModLoaderType());

        AlienPropertyAccess.INSTANCE.save();

        MOD.initialize(Alien::runInitialization);
    }

    private static void runInitialization() {
        // No dependencies.
        AlienBlocks.initialize();
        AlienChitinBlocks.initialize();
        AlienResinBlocks.initialize();
        NetherAlienChitinBlocks.initialize();
        NetherAlienResinBlocks.initialize();
        AberrantAlienChitinBlocks.initialize();
        AberrantAlienResinBlocks.initialize();
        IrradiatedAlienChitinBlocks.initialize();
        IrradiatedAlienResinBlocks.initialize();
        AlienItems.initialize();

        AlienEntityTypes.initialize();
        AlienSoundEvents.initialize();

        // Depends on blocks.
        AlienBlockItems.initialize();
        AlienChitinBlockItems.initialize();
        AlienResinBlockItems.initialize();
        NetherAlienChitinBlockItems.initialize();
        NetherAlienResinBlockItems.initialize();
        AberrantAlienChitinBlockItems.initialize();
        AberrantAlienResinBlockItems.initialize();
        IrradiatedAlienChitinBlockItems.initialize();
        IrradiatedAlienResinBlockItems.initialize();
        // Depends on sound events.
        AlienArmorMaterials.initialize();
        // Depends on armor materials.
        AlienArmorItems.initialize();
        // Depends on entity types.
        AlienSpawnEggItems.initialize();
        // Depends on blocks.
        AlienBlockEntityTypes.initialize();
        // Depends on blocks, items, block items, etc.
        AlienCreativeModeTabs.initialize();

        AlienGameEvents.initialize();
        AlienGameRules.initialize();
        AlienMobEffects.initialize();
        AlienParticleTypes.initialize();

        // Depends on mob effects and items.
        AlienPotions.initialize();

        // Functionality
        AlienDecoratedPotPatterns.initialize();
        AlienCompostingChances.initialize();
        AlienDataSyncKeys.initialize();
        AlienFactionDataTypes.initialize();

        // Depends on entity types.
        AlienLimbDefinitions.initialize();
        AlienLimbDrops.initialize();

        AlienCommands.initialize();

        // Networking: hive inspection payloads (request/reply) for the engine workspace inspector.
        AlienNetworking.initialize();

        // Facehugger head-attachment data: join/reload sync of the datapack-driven head profiles to clients.
        HeadAttachmentSync.initialize();

        // Data Migration
        AlienDataMigrations.initialize();

        // Listeners/Events
        AlienReloadListeners.initialize();
        AlienAdvancementEvents.initialize();
        com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem.initialize();

        MOD.events().postLevelTick().register(Alien::tickHiveRegistry);
        MOD.events().postLevelTick().register(Alien::tickQueenSpawnCooldown);
        MOD.events().onTagsUpdated().register(Alien::onTagsUpdated);

        MOD.events().onFactionsLoaded().register(Alien::rebuildHiveRegistryFromFactions);
        MOD.events().onServerStopped().register(server -> HiveLocationRegistry.INSTANCE.clear());
        MOD.events().onServerStopped().register(server -> com.alien.common.gameplay.hive.structure.HivePieceRegistry.INSTANCE.clear());

        // Hive: defensive cleanup when any lineage faction is removed.
        MOD.events()
            .onFactionRemove()
            .register(com.alien.common.gameplay.hive.lifecycle.HiveFactionRemoveListener::onFactionRemoved);
        MOD.events().onServerStopped().register(server -> QueenSettlementDetector.clear());
        MOD.events()
            .onServerStopped()
            .register(server -> com.alien.common.gameplay.hive.convoy.ReinforcementDispatcher.clear());
        MOD.events()
            .onServerStopped()
            .register(server -> com.alien.common.gameplay.hive.convoy.RaidDispatch.clear());
        MOD.events()
            .onServerStopped()
            .register(server -> com.alien.common.gameplay.hive.convoy.ConvoyBossBars.clear());
        MOD.events()
            .onServerStopped()
            .register(server -> com.alien.common.gameplay.hive.empress.EmpressEmergenceRitual.clear());

        // Hive: variant-faction join is event-driven. Catches every alien that loads from disk
        // (the finalizeSpawn hook covers fresh spawns). Idempotent — see
        // HiveManager.ensureVariantFactionMembership.
        MOD.events().onEntityLoad().register(Alien::onAlienEntityLoaded);

        // DO NOT REGISTER A CHUNK_LOAD LISTENER - the registration itself arms a server-killing crash.
        // BLib's MixinChunkMap_ChunkLoadEvent calls level.getChunk(x, z) synchronously BEFORE dispatching to
        // listeners, inside onFullChunkStatusChange, which runs inside DistanceManager.runAllUpdates' iteration -
        // reentrancy -> ConcurrentModificationException -> "Exception ticking world" (tester: entering the nether).
        // The handler early-outs while its listener list is EMPTY, so an empty list is the kill switch. Resin
        // decoration now polls from HiveLocationLoadedTickTask via ResinDecorator.sweepLoaded. If BLib ships the
        // real fix (non-blocking getChunkNow + deferred dispatch), event-driven decoration may return.
    }

    private static void onAlienEntityLoaded(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
            alien.getHiveManager().ensureVariantFactionMembership();
            com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.recoverLoadedAlien(entity);
        }
    }

    public static void rebuildHiveRegistryFromFactions(MinecraftServer server) {
        // Phase 12 migrator: convert any legacy avp_alien:hive/* factions into the new lineage + location
        // structure after BLib's faction store is definitely loaded. Idempotent — does nothing on a clean hive-only
        // world.
        com.alien.common.gameplay.hive.migration.OldHiveMigrator.run(server);
        com.alien.common.gameplay.claim.LegacyPlayerClaimMigration.migrateToBLib(server);
        HiveLocationRegistry.INSTANCE.rebuildFromFactions();
        // The recovery pass needs a populated registry to inspect, hence the rebuild ABOVE it; the rebuild BELOW
        // only exists to pick up factions the recovery just created, so it is skipped when the recovery repaired
        // nothing. On a clean world that second pass was byte-identical to the first - the "registry rebuild runs
        // twice" every load in the tester logs.
        if (com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.detectAndRecover(server)) {
            HiveLocationRegistry.INSTANCE.rebuildFromFactions();
        }
        HiveLocationRegistry.INSTANCE.repairTerritoryClaims(server);
    }

    /**
     * Single per-server-tick driver for the new hive system. Gated to Overworld so it fires once per server tick total;
     * the registry itself iterates every dimension internally (fixes {@code HIVE_SYSTEM_ANALYSIS.md} § 9.1.2).
     */
    private static void tickHiveRegistry(Level level) {
        if (level.isClientSide || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var server = level.getServer();

        if (server != null) {
            HiveLocationRegistry.INSTANCE.tick(server);
            com.alien.common.gameplay.hive.bootstrap.RoyalBootstrapResolver.tick(server);
            com.alien.common.network.handler.HiveRenderToggleHandler.tick(server);
            com.alien.common.network.handler.HiveStatsSidebarHandler.tick(server);
            com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.tickPlayerMessages(server);
        }
    }

    private static void tickQueenSpawnCooldown(Level level) {
        if (level.isClientSide) {
            return;
        }

        QueenSpawnChunkData.getOrCreate(level)
            .ifSome(QueenSpawnChunkData::tick);

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenNaturalSpawnTask.tick(serverLevel);
        }
    }

    private static void onTagsUpdated(RegistryAccess registryAccess, boolean flag) {
        GrowthStageRegistry.rebuildLookupMappings();
        InfectionRegistry.rebuildLookupMappings();
    }
}
