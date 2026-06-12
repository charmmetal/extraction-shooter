package com.extractionshooter.core;

import com.extractionshooter.ExtractionShooterMod;
import com.extractionshooter.block.LootContainerBlock;
import com.extractionshooter.block.LootContainerBlockEntity;
import com.extractionshooter.gui.LootContainerMenu;
import com.extractionshooter.gui.ExtractionBackpackMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegistryHandler {
    // 方块注册
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExtractionShooterMod.MOD_ID);

    // 物品注册
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExtractionShooterMod.MOD_ID);

    // 方块实体注册
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ExtractionShooterMod.MOD_ID);

    // 菜单类型注册
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ExtractionShooterMod.MOD_ID);

    // ========== 方块 ==========
    public static final RegistryObject<Block> LOOT_CONTAINER_BLOCK = BLOCKS.register("loot_container",
            () -> new LootContainerBlock(BlockBehaviour.Properties.of(Material.WOOD).strength(2.0f)));

    // ========== 方块物品 ==========
    public static final RegistryObject<Item> LOOT_CONTAINER_ITEM = ITEMS.register("loot_container",
            () -> new BlockItem(LOOT_CONTAINER_BLOCK.get(), new Item.Properties()));

    // ========== 方块实体 ==========
    public static final RegistryObject<BlockEntityType<LootContainerBlockEntity>> LOOT_CONTAINER_ENTITY =
            BLOCK_ENTITIES.register("loot_container",
                    () -> BlockEntityType.Builder.of(
                            LootContainerBlockEntity::new, LOOT_CONTAINER_BLOCK.get()
                    ).build(null));

    // ========== 菜单 ==========
    public static final RegistryObject<MenuType<LootContainerMenu>> LOOT_CONTAINER_MENU =
            MENUS.register("loot_container",
                    () -> IForgeMenuType.create(LootContainerMenu::new));

    public static final RegistryObject<MenuType<ExtractionBackpackMenu>> EXTRACTION_BACKPACK_MENU =
            MENUS.register("extraction_backpack",
                    () -> IForgeMenuType.create(ExtractionBackpackMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
