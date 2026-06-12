package com.extractionshooter.evacuation;

import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.data.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 撤离管理器 - 管理撤离区域、撤离读条、费用扣除、断线续存等
 */
public class EvacuationManager {
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;

    // 正在撤离的玩家 -> 撤离状态
    private final Map<UUID, EvacuationProgress> activeEvacuations = new ConcurrentHashMap<>();

    // 断线玩家的撤离状态缓存 (UUID -> serialized progress)
    private final Map<UUID, String> disconnectedProgress = new ConcurrentHashMap<>();

    public EvacuationManager(ConfigManager configManager, PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
    }

    public void load() {
        // 从玩家数据中恢复断线撤离状态
        // 数据由 PlayerDataManager 管理
    }

    /**
     * 每 tick 更新 - 检查玩家是否在撤离区域、推进撤离进度
     */
    public void tick(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!configManager.getGlobalSettings().enableEvacuation) return;

        long gameTime = level.getGameTime();

        // 检查所有在线玩家
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) continue;

            // 检查玩家是否在撤离区域内
            ConfigManager.EvacuationZoneDef zone = getPlayerZone(player);
            if (zone != null) {
                // 在撤离区域内
                handlePlayerInZone(player, zone, gameTime);
            } else {
                // 不在撤离区域内
                handlePlayerOutOfZone(player);
            }
        }

        // 清理超时的撤离进度
        activeEvacuations.entrySet().removeIf(e -> e.getValue().isExpired(gameTime));
    }

    /**
     * 玩家在撤离区域内
     */
    private void handlePlayerInZone(ServerPlayer player, ConfigManager.EvacuationZoneDef zone, long gameTime) {
        UUID uuid = player.getUUID();
        EvacuationProgress progress = activeEvacuations.get(uuid);

        if (progress == null) {
            // 开始撤离读条
            progress = new EvacuationProgress(zone.name, zone.extractTime, gameTime);
            activeEvacuations.put(uuid, progress);

            // 执行进入命令
            if (!zone.onEnterCommand.isEmpty()) {
                executeCommand(player, zone.onEnterCommand);
            }

            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§e⚠ 进入撤离区域！等待 " + (zone.extractTime / 20) + " 秒..."
                    )
            );
        }

        // 推进进度
        progress.tick(gameTime);

        // 每 10 tick 发送一次进度提示
        if (gameTime % 10 == 0) {
            int remaining = (progress.maxTick - progress.currentTick) / 20;
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§6撤离倒计时: " + remaining + " 秒"
                    )
            );
        }

        // 撤离完成
        if (progress.currentTick >= progress.maxTick) {
            completeEvacuation(player, zone);
        }
    }

    /**
     * 玩家不在撤离区域内
     */
    private void handlePlayerOutOfZone(ServerPlayer player) {
        UUID uuid = player.getUUID();
        EvacuationProgress progress = activeEvacuations.remove(uuid);

        if (progress != null) {
            // 撤离中断
            ConfigManager.EvacuationZoneDef zone = configManager.getEvacuationZones().get(progress.zoneName);
            if (zone != null && !zone.onFailCommand.isEmpty()) {
                executeCommand(player, zone.onFailCommand);
            }

            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c✗ 撤离中断！")
            );
        }
    }

    /**
     * 撤离成功
     */
    private void completeEvacuation(ServerPlayer player, ConfigManager.EvacuationZoneDef zone) {
        UUID uuid = player.getUUID();
        activeEvacuations.remove(uuid);

        // 扣除撤离费用
        if (!deductEvacuationCost(player, zone)) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c✗ 撤离费用不足！")
            );
            return;
        }

        // 执行成功命令
        if (!zone.onSuccessCommand.isEmpty()) {
            executeCommand(player, zone.onSuccessCommand);
        }

        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§a✔ 撤离成功！")
        );

        // 可选：传送玩家到世界出生点或执行其他逻辑
    }

    /**
     * 扣除撤离费用
     */
    private boolean deductEvacuationCost(ServerPlayer player, ConfigManager.EvacuationZoneDef zone) {
        // 扣除经验等级
        if (zone.costExpLevels > 0) {
            if (player.experienceLevel < zone.costExpLevels) return false;
            player.experienceLevel -= zone.costExpLevels;
        }

        // 扣除物品
        for (Map.Entry<String, Integer> entry : zone.costItems.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();
            if (!hasItem(player, itemId, count)) return false;
            removeItem(player, itemId, count);
        }

        // 扣除货币（自定义货币物品）
        if (zone.costCurrency > 0 && !zone.currencyItem.isEmpty()) {
            if (!hasItem(player, zone.currencyItem, zone.costCurrency)) return false;
            removeItem(player, zone.currencyItem, zone.costCurrency);
        }

        return true;
    }

    /**
     * 检查玩家是否有足够物品
     */
    private boolean hasItem(ServerPlayer player, String itemId, int count) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && stack.getItem().getRegistryName().toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total >= count;
    }

    /**
     * 从玩家背包移除物品
     */
    private void removeItem(ServerPlayer player, String itemId, int count) {
        Inventory inv = player.getInventory();
        int remaining = count;
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && stack.getItem().getRegistryName().toString().equals(itemId)) {
                int toRemove = Math.min(stack.getCount(), remaining);
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * 获取玩家所在的撤离区域
     */
    private ConfigManager.EvacuationZoneDef getPlayerZone(ServerPlayer player) {
        Vec3 pos = player.position();
        for (ConfigManager.EvacuationZoneDef zone : configManager.getEvacuationZones().values()) {
            if (!zone.world.equals(player.level().dimension().location().toString())) continue;

            AABB zoneAABB = new AABB(zone.x1, zone.y1, zone.z1, zone.x2, zone.y2, zone.z2);
            if (zoneAABB.contains(pos)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * 执行配置的命令
     */
    private void executeCommand(ServerPlayer player, String command) {
        String cmd = command.replace("%player%", player.getName().getString());
        player.getServer().getCommands().performPrefixedCommand(
                player.getServer().createCommandSourceStack(), cmd
        );
    }

    // ==================== 断线续存 ====================

    /**
     * 处理玩家断线 - 保存撤离进度
     */
    public void handleDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        EvacuationProgress progress = activeEvacuations.remove(uuid);

        if (progress != null) {
            ConfigManager.EvacuationZoneDef zone = configManager.getEvacuationZones().get(progress.zoneName);
            if (zone != null && zone.allowDisconnectResume) {
                // 保存撤离进度到玩家数据
                playerDataManager.saveEvacuationProgress(uuid, progress);
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§e⚠ 你的撤离进度已保存，重连后可继续！"
                        )
                );

                // 执行断线命令
                if (!zone.onDisconnectCommand.isEmpty()) {
                    executeCommand(player, zone.onDisconnectCommand);
                }
            } else {
                // 撤离失败
                if (zone != null && !zone.onFailCommand.isEmpty()) {
                    executeCommand(player, zone.onFailCommand);
                }
            }
        }
    }

    /**
     * 恢复玩家撤离进度
     */
    public void resumePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        EvacuationProgress saved = playerDataManager.getEvacuationProgress(uuid);

        if (saved != null) {
            // 检查玩家是否仍在撤离区域内
            ConfigManager.EvacuationZoneDef zone = configManager.getEvacuationZones().get(saved.zoneName);
            if (zone != null && getPlayerZone(player) != null) {
                activeEvacuations.put(uuid, saved);
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§a✔ 撤离进度已恢复！剩余 " +
                                        ((saved.maxTick - saved.currentTick) / 20) + " 秒"
                        )
                );
            } else {
                playerDataManager.clearEvacuationProgress(uuid);
            }
        }
    }

    // ==================== 内部类 ====================

    public static class EvacuationProgress {
        public final String zoneName;
        public final int maxTick;
        public int currentTick;
        public long startTime;

        public EvacuationProgress(String zoneName, int maxTick, long startTime) {
            this.zoneName = zoneName;
            this.maxTick = maxTick;
            this.currentTick = 0;
            this.startTime = startTime;
        }

        public void tick(long gameTime) {
            this.currentTick++;
        }

        public boolean isExpired(long gameTime) {
            return gameTime - startTime > maxTick * 2; // 超时2倍视为过期
        }
    }
}
