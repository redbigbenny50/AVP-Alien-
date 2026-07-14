package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.AlienResources;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public class AlienSoundEvents {

    private static final BLibRegistry<SoundEvent> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.SOUND_EVENT);

    public static final BLibHolder<SoundEvent> BLOCK_ACID_BURN = create("block.acid.burn");

    public static final BLibHolder<SoundEvent> BLOCK_RESIN_SPREAD = create("block.resin.spread");

    public static final BLibHolder<SoundEvent> EFFECT_BONE_CRUNCH = create("effect.bone_crunch");

    public static final BLibHolder<SoundEvent> EFFECT_HEARTBEAT_0 = create("effect.heartbeat.0");

    public static final BLibHolder<SoundEvent> EFFECT_HEARTBEAT_1 = create("effect.heartbeat.1");

    public static final BLibHolder<SoundEvent> EFFECT_HEARTBEAT_2 = create("effect.heartbeat.2");

    public static final BLibHolder<SoundEvent> EFFECT_HEARTBEAT_3 = create("effect.heartbeat.3");

    public static final BLibHolder<SoundEvent> ENTITY_CHESTBURSTER_BURST = create("entity.chestburster.burst");

    /** A facehugger is torn off a face. */
    public static final BLibHolder<SoundEvent> ENTITY_FACEHUGGER_ESCAPE = create("entity.facehugger.escape");

    public static final BLibHolder<SoundEvent> ENTITY_OVOMORPH_HATCH = create("entity.ovomorph.hatch");

    public static final BLibHolder<SoundEvent> ENTITY_OVOMORPH_LAID = create("entity.ovomorph.laid");

    public static final BLibHolder<SoundEvent> ENTITY_OVOMORPH_ROOT = create("entity.ovomorph.root");

    public static final BLibHolder<SoundEvent> ENTITY_OVOMORPH_SHEAR = create("entity.ovomorph.shear");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_ARM_ATTACK = create("entity.queen.arm_attack");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_BACK_HAND_ATTACK = create("entity.queen.back_hand_attack");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_DEATH = create("entity.queen.death");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_HURT = create("entity.queen.hurt");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_IDLE = create("entity.queen.idle");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_RAM_ATTACK = create("entity.queen.ram_attack");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_SCREAM = create("entity.queen.scream");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_STEP_THUMP = create("entity.queen.step_thump");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_STEP_THUMP_ROCK = create("entity.queen.step_thump_rock");

    public static final BLibHolder<SoundEvent> ENTITY_QUEEN_TAIL_ATTACK = create("entity.queen.tail_attack");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_ARM_ATTACK = create("entity.empress.arm_attack");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_BACK_HAND_ATTACK = create("entity.empress.back_hand_attack");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_DEATH = create("entity.empress.death");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_HURT = create("entity.empress.hurt");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_IDLE = create("entity.empress.idle");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_RAM_ATTACK = create("entity.empress.ram_attack");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_SCREAM = create("entity.empress.scream");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_STEP_THUMP = create("entity.empress.step_thump");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_STEP_THUMP_ROCK = create("entity.empress.step_thump_rock");

    public static final BLibHolder<SoundEvent> ENTITY_EMPRESS_TAIL_ATTACK = create("entity.empress.tail_attack");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_ATTACK = create("entity.xenomorph.attack");

    /** A captive tears free of the drone carrying it off. */
    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_ESCAPE_HOST = create("entity.xenomorph.escape_host");

    /** A drone seizes a host. NOTE: this fires once per capture - if you hear it STUTTER, a carrier is grabbing and
     *  dropping its captive in a loop, which is a bug signature worth chasing. */
    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_GRAB_HOST = create("entity.xenomorph.grab_host");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_DEATH = create("entity.xenomorph.death");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_HISS = create("entity.xenomorph.hiss");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_HURT = create("entity.xenomorph.hurt");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_IDLE = create("entity.xenomorph.idle");

    public static final BLibHolder<SoundEvent> ENTITY_XENOMORPH_LUNGE = create("entity.xenomorph.lunge");

    public static final BLibHolder<SoundEvent> ITEM_ARMOR_EQUIP_CHITIN = create("item.armor.equip_chitin");

    public static final BLibHolder<SoundEvent> JUKEBOX_SOUNDS_ALIEN_MUSIC_1 = create("jukebox_sounds.alien_music_1");

    public static final BLibHolder<SoundEvent> UI_TERMINAL_OPEN = create("ui.terminal.open");

    public static final BLibHolder<SoundEvent> UI_TERMINAL_CLOSE = create("ui.terminal.close");

    public static final BLibHolder<SoundEvent> UI_TERMINAL_MOUSEOVER = create("ui.terminal.mouseover");

    public static final BLibHolder<SoundEvent> UI_TERMINAL_CLICK = create("ui.terminal.click");

    public static final BLibHolder<SoundEvent> UI_TERMINAL_EXECUTE = create("ui.terminal.execute");

    private static BLibHolder<SoundEvent> create(String path) {
        return REGISTRY.createHolder(path, () -> SoundEvent.createVariableRangeEvent(AlienResources.location(path)));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}