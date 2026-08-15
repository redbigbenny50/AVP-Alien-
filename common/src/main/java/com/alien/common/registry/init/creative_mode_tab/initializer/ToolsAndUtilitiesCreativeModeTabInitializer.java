package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.item.AlienItems;
import com.alien.compatibility.avp_human.AVPHuman;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class ToolsAndUtilitiesCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        CreativeModeTabUtil.accept(output, AlienItems.ANCHOR);
        CreativeModeTabUtil.accept(output, AlienItems.FIELD_MANUAL);
        CreativeModeTabUtil.accept(output, AlienItems.CAPTURE_CHAIN);
        CreativeModeTabUtil.accept(output, AlienItems.INHIBITOR);
        // Tracker + tracking PDA are human tech: hidden without the human module, same gate as the other
        // AVPHuman-unlocked content.
        if (AVPHuman.MOD.isLoaded()) {
            CreativeModeTabUtil.accept(output, AlienItems.TRACKER);
            CreativeModeTabUtil.accept(output, AlienItems.TRACKING_PDA);
        }
        CreativeModeTabUtil.accept(output, AlienItems.ALIEN_MUSIC_DISC_1);
    };
}
