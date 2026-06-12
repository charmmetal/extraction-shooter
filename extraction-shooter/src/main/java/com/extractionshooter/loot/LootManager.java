package com.extractionshooter.loot;

import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.quality.QualityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 战利品管理器 - 管理搜刮容器、刷新点、死亡掉落等
 */
public class LootManager {
    private final ConfigManager configManager;
    private final QualityManager qualityManager;
    private final Random random = new Random();

    // 正在被搜索的容器位置 -> 搜索进度 (0.0 ~ 1.0)
    private final Map<BlockPos, SearchProgress> activeSearches = new HashMap<>();

    // 玩家死亡掉落容器位置
    private final Map<UUID, BlockPos> deathLootContainers = new HashMap<>();

    public LootManager(ConfigManager configManager, QualityManager qualityManager) {
        this.configManager = configManager;
        this.qualityManager = qualityManager;
    }

    public void load() {
        // 数据由 ConfigManager 管理
    }

    // ==================== 搜刮流程 ====================

    /**
     * 开始搜索一个容器
     */
    public boolean startSearch(ServerPlayer player, BlockPos pos) {
        if (activeSearches.containsKey(pos)) return false;

        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // 检查是否为支持的容器类型
        if (!isLootableContainer(state)) return false;

        SearchProgress progress = new SearchProgress(pos, 100); // 100 tick = 5秒
        activeSearches.put(pos, progress);
        return true;
    }

    /**
     * 推进搜索进度
     */
    public boolean tickSearch(ServerPlayer player, BlockPos pos) {
        SearchProgress progress = activeSearches.get(pos);
        if (progress == null) return false;

        progress.currentTick++;
        if (progress.currentTick >= progress.maxTick) {
            // 搜索完成
            completeSearch(player, pos);
            return true;
        }
        return false;
    }

    /**
     * 搜索完成 - 容器变为可拾取状态
     */
    private void completeSearch(ServerPlayer player, BlockPos pos) {
        activeSearches.remove(pos);

        // 生成战利品
        generateLoot(player, pos);

        // 通知玩家
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§a✓ 搜索完成！可拾取战利品")
        );
    }

    /**
     * 取消搜索
     */
    public void cancelSearch(BlockPos pos) {
        activeSearches.remove(pos);
    }

    /**
     * 获取搜索进度 (0.0 ~ 1.0)
     */
    public double getSearchProgress(BlockPos pos) {
        SearchProgress p = activeSearches.get(pos);
        if (p == null) return 0.0;
        return (double) p.currentTick / p.maxTick;
    }

    /**
     * 判断位置是否正在被搜索
     */
    public boolean isSearching(BlockPos pos) {
        return activeSearches.containsKey(pos);
    }

    // ==================== 战利品生成 ====================

    /**
     * 在容器位置生成战利品
     */
    public void generateLoot(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);

        // 获取或创建容器
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) return;

        // 清空并填充战利品
        chest.clearContent();

        // 根据配置生成物品
        List<ItemStack> loot = rollLoot(player, pos);
        for (int i = 0; i < Math.min(loot.size(), chest.getContainerSize()); i++) {
            chest.setItem(i, loot.get(i));
        }

        chest.setChanged();
    }

    /**
     * 随机生成战利品列表
     */
    private List<ItemStack> rollLoot(ServerPlayer player, BlockPos pos) {
        List<ItemStack> loot = new ArrayList<>();
        int itemCount = 3 + random.nextInt(5); // 3-7 个物品

        // 获取所有已定义的物品
        Collection<ConfigManager.ItemDef> defs = configManager.getItemDefs().values();
        if (defs.isEmpty()) {
            // 没有自定义物品时，生成一些默认物品
            for (int i = 0; i < itemCount; i++) {
                ItemStack stack = new ItemStack(
                        net.minecraft.world.item.Items.EMERALD,
                        1 + random.nextInt(3)
                );
                qualityManager.applyRandomQuality(stack);
                loot.add(stack);
            }
            return loot;
        }

        List<ConfigManager.ItemDef> defList = new ArrayList<>(defs);
        for (int i = 0; i < itemCount; i++) {
            ConfigManager.ItemDef def = defList.get(random.nextInt(defList.size()));
            ItemStack stack = new ItemStack(def.getItem(), 1);
            qualityManager.applyRandomQuality(stack);
            loot.add(stack);
        }

        return loot;
    }

    // ==================== 死亡掉落 ====================

    /**
     * 存储玩家死亡后的背包物品到搜刮容器
     */
    public void storePlayerDeathLoot(ServerPlayer player) {
        Level level = player.level();
        BlockPos deathPos = player.blockPosition();

        // 在死亡位置生成一个战利品容器
        BlockPos containerPos = findSuitablePosition(level, deathPos);
        if (containerPos == null) return;

        // 放置容器
        level.setBlock(containerPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) return;

        // 转移玩家背包物品
        Inventory inv = player.getInventory();
        int slot = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && slot < chest.getContainerSize()) {
                chest.setItem(slot, stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
                slot++;
            }
        }

        chest.setChanged();
        deathLootContainers.put(player.getUUID(), containerPos);

        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "§c☠ 你的物品已存入死亡容器！位置: " +
                                containerPos.getX() + ", " +
                                containerPos.getY() + ", " +
                                containerPos.getZ()
                )
        );
    }

    /**
     * 获取玩家的死亡掉落容器位置
     */
    public BlockPos getDeathLootContainer(UUID playerUUID) {
        return deathLootContainers.get(playerUUID);
    }

    // ==================== 刷新点管理 ====================

    /**
     * 刷新所有到期的固定刷新点
     */
    public void refreshFixedSpawns(ServerLevel level) {
        long gameTime = level.getGameTime();
        for (ConfigManager.SpawnPointDef def : configManager.getFixedSpawnPoints()) {
            if (!def.isActive) continue;
            if (!def.world.equals(level.dimension().location().toString())) continue;

            if (gameTime - def.lastRefreshTime >= def.refreshInterval) {
                refreshSpawnPoint(level, def);
                def.lastRefreshTime = gameTime;
            }
        }
        configManager.saveAll();
    }

    /**
     * 刷新单个刷新点
     */
    private void refreshSpawnPoint(ServerLevel level, ConfigManager.SpawnPointDef def) {
        BlockPos pos = BlockPos.containing(def.x, def.y, def.z);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            chest.clearContent();
            // 填充战利品
            List<ItemStack> loot = rollLoot(null, pos);
            for (int i = 0; i < Math.min(loot.size(), chest.getContainerSize()); i++) {
                chest.setItem(i, loot.get(i));
            }
            chest.setChanged();
        }
    }

    // ==================== 工具方法 ====================

    private boolean isLootableContainer(BlockState state) {
        return state.getBlock() == Blocks.CHEST
                || state.getBlock() == Blocks.BARREL
                || state.getBlock() == Blocks.SHULKER_BOX
                || state.getBlock() == com.extractionshooter.core.RegistryHandler.LOOT_CONTAINER_BLOCK.get();
    }

    private BlockPos findSuitablePosition(Level level, BlockPos near) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos candidate = near.offset(dx, 0, dz);
                if (level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.above()).isAir()) {
                    return candidate;
                }
            }
        }
        return near;
    }

    // ==================== 内部类 ====================

    public static class SearchProgress {
        public final BlockPos pos;
        public final int maxTick;
        public int currentTick = 0;

        public SearchProgress(BlockPos pos, int maxTick) {
            this.pos = pos;
            this.maxTick = maxTick;
        }
    }
}
