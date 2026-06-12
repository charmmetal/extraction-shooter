package com.extractionshooter.block;

import com.extractionshooter.core.RegistryHandler;
import com.extractionshooter.gui.LootContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 搜刮容器方块实体 - 存储战利品数据
 */
public class LootContainerBlockEntity extends BlockEntity implements MenuProvider {

    private boolean searched = false;  // 是否已被搜索
    private int searchProgress = 0;    // 搜索进度

    public LootContainerBlockEntity(BlockPos pos, BlockState state) {
        super(RegistryHandler.LOOT_CONTAINER_ENTITY.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("搜刮容器");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new LootContainerMenu(windowId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("searched", searched);
        tag.putInt("searchProgress", searchProgress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.searched = tag.getBoolean("searched");
        this.searchProgress = tag.getInt("searchProgress");
    }

    public boolean isSearched() { return searched; }
    public void setSearched(boolean searched) { this.searched = searched; }
    public int getSearchProgress() { return searchProgress; }
    public void setSearchProgress(int progress) { this.searchProgress = progress; }
}
