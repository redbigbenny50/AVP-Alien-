package com.alien.fabric.data.molting_profile;

import com.alien.fabric.data.molting_profile.provider.AberrantAlienMoltingProfileProvider;
import com.alien.fabric.data.molting_profile.provider.AlienMoltingProfileProvider;
import com.alien.fabric.data.molting_profile.provider.NetherAlienMoltingProfileProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;

public class MoltingProfileSubProvider extends MoltingProfileDataProvider {

    public MoltingProfileSubProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    protected void generate() {
        AlienMoltingProfileProvider.provide(this::add);
        AberrantAlienMoltingProfileProvider.provide(this::add);
        NetherAlienMoltingProfileProvider.provide(this::add);
    }
}
