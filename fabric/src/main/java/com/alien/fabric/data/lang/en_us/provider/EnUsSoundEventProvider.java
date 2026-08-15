package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.init.AlienSoundEvents;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsSoundEventProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addSound(builder, AlienSoundEvents.BLOCK_ACID_BURN, "Acid burns");
        addSound(builder, AlienSoundEvents.BLOCK_RESIN_SPREAD, "Xenomorph spreads resin");

        addSound(builder, AlienSoundEvents.EFFECT_BONE_CRUNCH, "Bones crunching");
        addSound(builder, AlienSoundEvents.EFFECT_HEARTBEAT_0, "Heart beats");
        addSound(builder, AlienSoundEvents.EFFECT_HEARTBEAT_1, "Heart beats quickly");
        addSound(builder, AlienSoundEvents.EFFECT_HEARTBEAT_2, "Heart beats rapidly");
        addSound(builder, AlienSoundEvents.EFFECT_HEARTBEAT_3, "Heart beats fatally");

        addSound(builder, AlienSoundEvents.ENTITY_CHESTBURSTER_BURST, "Chestburster bursting");

        addSound(builder, AlienSoundEvents.ENTITY_FACEHUGGER_ESCAPE, "Facehugger torn off");

        addSound(builder, AlienSoundEvents.ENTITY_OVOMORPH_HATCH, "Ovomorph hatches");
        addSound(builder, AlienSoundEvents.ENTITY_OVOMORPH_LAID, "Queen lays egg");
        addSound(builder, AlienSoundEvents.ENTITY_OVOMORPH_ROOT, "Ovomorph takes root");
        addSound(builder, AlienSoundEvents.ENTITY_OVOMORPH_SHEAR, "Ovomorph de-roots");

        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_ARM_ATTACK, "Queen attacks");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_BACK_HAND_ATTACK, "Queen back hand attacks");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_RAM_ATTACK, "Queen ram attacks");
        addSound(builder, AlienSoundEvents.ENTITY_HARBINGER_ROAR_3, "Harbinger roars");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_SCREAM, "Queen screams");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_TAIL_ATTACK, "Queen tail attacks");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_DEATH, "Queen dies");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_HURT, "Queen hurts");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_IDLE, "Queen breathes");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_STEP_THUMP, "Queen steps");
        addSound(builder, AlienSoundEvents.ENTITY_QUEEN_STEP_THUMP_ROCK, "Queen steps");

        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_ARM_ATTACK, "Empress attacks");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_BACK_HAND_ATTACK, "Empress back hand attacks");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_RAM_ATTACK, "Empress ram attacks");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_SCREAM, "Empress screams");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_TAIL_ATTACK, "Empress tail attacks");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_DEATH, "Empress dies");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_HURT, "Empress hurts");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_IDLE, "Empress breathes");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_STEP_THUMP, "Empress steps");
        addSound(builder, AlienSoundEvents.ENTITY_EMPRESS_STEP_THUMP_ROCK, "Empress steps");

        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_GRAB_HOST, "Xenomorph seizes a host");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_ESCAPE_HOST, "Host tears free");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_ATTACK, "Xenomorph attacks");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_DEATH, "Xenomorph dies");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_HISS, "Xenomorph hisses");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_HURT, "Xenomorph hurts");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_IDLE, "Xenomorph breathes");
        addSound(builder, AlienSoundEvents.ENTITY_XENOMORPH_LUNGE, "Xenomorph lunges");

        addSound(builder, AlienSoundEvents.ITEM_ARMOR_EQUIP_CHITIN, "Chitin armor squishes");

        addSound(builder, AlienSoundEvents.JUKEBOX_SOUNDS_ALIEN_MUSIC_1, "Silver Smile plays");

        addSound(builder, AlienSoundEvents.UI_TERMINAL_OPEN, "Terminal powers on");
        addSound(builder, AlienSoundEvents.UI_TERMINAL_CLOSE, "Terminal powers off");
        addSound(builder, AlienSoundEvents.UI_TERMINAL_MOUSEOVER, "Terminal blip");
        addSound(builder, AlienSoundEvents.BROADCAST_WAR_SOS, "Emergency broadcast: distress signal");
        addSound(builder, AlienSoundEvents.BROADCAST_WAR_ALL_CLEAR, "Emergency broadcast: all clear");

        addSound(builder, AlienSoundEvents.UI_TERMINAL_CLICK, "Terminal selects");
        addSound(builder, AlienSoundEvents.UI_TERMINAL_EXECUTE, "Terminal confirms");
    };

    private static void addSound(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<SoundEvent> soundEventSupplier,
        String value
    ) {
        addSound(translationBuilder, soundEventSupplier.get(), value);
    }

    private static void addSound(FabricLanguageProvider.TranslationBuilder translationBuilder, SoundEvent soundEvent, String value) {
        translationBuilder.add("subtitles." + soundEvent.getLocation().getPath(), value);
    }
}
