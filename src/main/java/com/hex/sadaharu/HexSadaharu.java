package com.hex.sadaharu;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.*;

@Mod(HexSadaharu.ID)
public final class HexSadaharu {
    public static final String ID = "hexsadaharu";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    public static final RegistryObject<EntityType<Sadaharu>> DOG = ENTITIES.register("sadaharu", () -> EntityType.Builder.of(Sadaharu::new, MobCategory.CREATURE).sized(2.15F, 2.85F).clientTrackingRange(12).updateInterval(1).fireImmune().build(ID+":sadaharu"));
    public static final RegistryObject<Item> KIBBLE = ITEMS.register("kibble", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> POOP = ITEMS.register("poop", () -> new Item(new Item.Properties()));
    public static final RegistryObject<MenuType<SadaharuMenu>> MENU = MENUS.register("companion", () -> net.minecraftforge.common.extensions.IForgeMenuType.create((IContainerFactory<SadaharuMenu>)SadaharuMenu::new));
    public static final String[] VOICES = {"bark","excited","deep_bark","whine","growl","pant","sleep","yawn","eat","land","step"};
    static { for (String voice : VOICES) SOUNDS.register(voice, () -> SoundEvent.createVariableRangeEvent(id(voice))); }
    public static ResourceLocation id(String path) { return new ResourceLocation(ID,path); }
    public HexSadaharu() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); ITEMS.register(bus); MENUS.register(bus); SOUNDS.register(bus);
        bus.addListener(this::attributes);
        Network.init(); MinecraftForge.EVENT_BUS.register(new WorldEvents());
    }
    private void attributes(EntityAttributeCreationEvent e) { e.put(DOG.get(), Sadaharu.attributes().build()); }
}
