package com.extractionshooter.gui;

import com.extractionshooter.block.LootContainerBlockEntity;
import com.extractionshooter.core.RegistryHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 搜刮容器菜单 - 显示战利品和搜索进度
 */
public class LootContainerMenu extends AbstractContainerMenu {
    private final LootContainerBlockEntity container;

    // 客户端构造
    public LootContainerMenu(int windowId, Inventory inv, FriendlyByteBuf buf) {
        this(windowId, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    // 服务端构造
    public LootContainerMenu(int windowId, Inventory inv, BlockEntity blockEntity) {
        super(RegistryHandler.LOOT_CONTAINER_MENU.get(), windowId);

        if (blockEntity instanceof LootContainerBlockEntity lootContainer) {
            this.container = lootContainer;
        } else {
            this.container = null;
        }

        // 战利品槽位 (3行 x 9列 = 27格)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(lootContainer != null ? inv : null, col + row * 9,
                        8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // 不可放入
                    }
                });
            }
        }

        // 玩家背包 (3行 x 9列)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9,
                        8 + col * 18, 86 + row * 18));
            }
        }

        // 玩家快捷栏 (1行 x 9列)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 144));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        // 战利品区域 -> 玩家背包
        if (slotIndex < 27) {
            if (!this.moveItemStackTo(stack, 27, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 -> 战利品区域（不可放入）
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public LootContainerBlockEntity getContainer() {
        return container;
    }
}
