package com.alien.common.data;

import com.alien.Alien;
import com.blib.api.common.registry.v1.impl.BLibReloadListenerRegistry;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class AlienReloadListeners {

    private static final BLibReloadListenerRegistry REGISTRY = Alien.MOD.registries().createReloadListenerRegistry();

    public static final PreparableReloadListener MOLTING_PROFILE_RELOAD_LISTENER = new MoltingProfileReloadListener();

    public static final PreparableReloadListener GROWTH_STAGES_RELOAD_LISTENER = new GrowthStageReloadListener();

    public static final PreparableReloadListener INFECTIONS_RELOAD_LISTENER = new InfectionReloadListener();

    public static final PreparableReloadListener HIVE_UNIT_PURCHASES_RELOAD_LISTENER =
        new HiveUnitPurchaseReloadListener();

    public static final PreparableReloadListener RAID_WAVE_PROFILE_RELOAD_LISTENER =
        new RaidWaveProfileReloadListener();

    public static final PreparableReloadListener REINFORCEMENT_PROFILE_RELOAD_LISTENER =
        new ReinforcementProfileReloadListener();

    public static final PreparableReloadListener HEAD_ATTACHMENT_RELOAD_LISTENER = new HeadAttachmentReloadListener();

    public static void initialize() {
        REGISTRY.register(
            MoltingProfileReloadListener.DIRECTORY_NAME,
            MOLTING_PROFILE_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
        REGISTRY.register(
            GrowthStageReloadListener.DIRECTORY_NAME,
            GROWTH_STAGES_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
        REGISTRY.register(InfectionReloadListener.DIRECTORY_NAME, INFECTIONS_RELOAD_LISTENER, PackType.SERVER_DATA);
        REGISTRY.register(
            HiveUnitPurchaseReloadListener.DIRECTORY_NAME,
            HIVE_UNIT_PURCHASES_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
        REGISTRY.register(
            RaidWaveProfileReloadListener.DIRECTORY_NAME,
            RAID_WAVE_PROFILE_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
        REGISTRY.register(
            ReinforcementProfileReloadListener.DIRECTORY_NAME,
            REINFORCEMENT_PROFILE_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
        REGISTRY.register(
            HeadAttachmentReloadListener.DIRECTORY_NAME,
            HEAD_ATTACHMENT_RELOAD_LISTENER,
            PackType.SERVER_DATA
        );
    }
}
