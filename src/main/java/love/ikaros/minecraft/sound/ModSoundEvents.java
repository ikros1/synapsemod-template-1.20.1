package love.ikaros.minecraft.sound;

import love.ikaros.minecraft.SynapseMod;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import net.minecraft.registry.Registry;

public class ModSoundEvents {

    public static final SoundEvent APOLLON_ARROW_SHOOT = register("apollon_arrow_shoot");

    public static void registerSounds(){

    }
    private static SoundEvent register(String name){
        Identifier id = new Identifier(SynapseMod.MOD_ID,name);
        return Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));
    }
}
