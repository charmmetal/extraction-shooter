package com.extractionshooter.gui;

import com.extractionshooter.core.RegistryHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 搜刮背包菜单 - 支持物品占多格和旋转的扩展背包
 * 这是一个自定义大小的背包容器，用于替代原版背包
 */
public class ExtractionBackpackMenu extends AbstractContainerMenu {
    private final int rows;
    private final int cols;

    // 客户端构造
    public ExtractionBackpackMenu(int windowId, Inventory inv, FriendlyByteBuf buf) {
        this(windowId, inv, buf.readInt(), buf.readInt());
    }

    // 服务端构造
    public ExtractionBackpackMenu(int windowId, Inventory inv, int rows, int cols) {
        super(RegistryHandler.EXTRACTION_BACKPACK_MENU.get(), windowId);
        this.rows = rows;
        this.cols = cols;

        int totalSlots = rows * cols;

        // 自定义背包槽位
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                this.addSlot(new Slot(inv, col + row * cols,
                        8 + col * 18, 18 + row * 18) {
                    @Override
                    public int getMaxStackSize() {
                        return 1; // 占格物品最大堆叠1
                    }
                });
            }
        }

        // 玩家快捷栏 (1行 x 9列)
        int playerInvStartY = 18 + rows * 18 + 14;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, playerInvStartY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // 简化处理：Shift点击不做特殊逻辑
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}
