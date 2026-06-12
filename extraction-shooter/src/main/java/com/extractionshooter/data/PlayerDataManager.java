package com.extractionshooter.data;

import com.extractionshooter.ExtractionShooterMod;
import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.evacuation.EvacuationManager;
import com.google.gson.*;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器 - 管理每个玩家的持久化数据（背包布局、撤离进度等）
 * 数据存储在: ./config/extraction_shooter/playerdata/<uuid>.json
 */
public class PlayerDataManager {
    private final Path playerDataDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConfigManager configManager;

    // 玩家数据缓存
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    public PlayerDataManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.playerDataDir = Paths.get("config", ExtractionShooterMod.MOD_ID, "playerdata");
    }

    public void loadAll() {
        try {
            Files.createDirectories(playerDataDir);
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("创建玩家数据目录失败", e);
        }
    }

    /**
     * 加载玩家数据
     */
    public PlayerData loadPlayer(UUID uuid) {
        if (playerDataCache.containsKey(uuid)) {
            return playerDataCache.get(uuid);
        }

        Path file = playerDataDir.resolve(uuid.toString() + ".json");
        PlayerData data = new PlayerData(uuid);

        if (Files.exists(file)) {
            try {
                data = gson.fromJson(Files.newBufferedReader(file), PlayerData.class);
                if (data == null) data = new PlayerData(uuid);
                data.uuid = uuid;
            } catch (Exception e) {
                ExtractionShooterMod.LOGGER.error("加载玩家数据失败: " + uuid, e);
            }
        }

        playerDataCache.put(uuid, data);
        return data;
    }

    /**
     * 保存玩家数据
     */
    public void savePlayer(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data == null) return;

        try {
            Files.writeString(playerDataDir.resolve(uuid.toString() + ".json"), gson.toJson(data));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存玩家数据失败: " + uuid, e);
        }
    }

    /**
     * 保存所有玩家数据
     */
    public void saveAll() {
        for (UUID uuid : playerDataCache.keySet()) {
            savePlayer(uuid);
        }
    }

    /**
     * 获取玩家数据
     */
    public PlayerData getData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, this::loadPlayer);
    }

    /**
     * 保存撤离进度
     */
    public void saveEvacuationProgress(UUID uuid, EvacuationManager.EvacuationProgress progress) {
        PlayerData data = getData(uuid);
        data.evacuationZoneName = progress.zoneName;
        data.evacuationCurrentTick = progress.currentTick;
        data.evacuationMaxTick = progress.maxTick;
        data.evacuationStartTime = progress.startTime;
        savePlayer(uuid);
    }

    /**
     * 获取撤离进度
     */
    public EvacuationManager.EvacuationProgress getEvacuationProgress(UUID uuid) {
        PlayerData data = getData(uuid);
        if (data.evacuationZoneName == null || data.evacuationZoneName.isEmpty()) return null;

        EvacuationManager.EvacuationProgress progress = new EvacuationManager.EvacuationProgress(
                data.evacuationZoneName,
                data.evacuationMaxTick,
                data.evacuationStartTime
        );
        progress.currentTick = data.evacuationCurrentTick;
        return progress;
    }

    /**
     * 清除撤离进度
     */
    public void clearEvacuationProgress(UUID uuid) {
        PlayerData data = getData(uuid);
        data.evacuationZoneName = null;
        data.evacuationCurrentTick = 0;
        data.evacuationMaxTick = 0;
        data.evacuationStartTime = 0;
        savePlayer(uuid);
    }

    public ConfigManager.GlobalSettings getGlobalConfig() {
        return configManager.getGlobalSettings();
    }

    // ==================== 玩家数据类 ====================

    public static class PlayerData {
        public UUID uuid;
        public String playerName = "";
        public long lastLoginTime = 0;
        public long totalPlayTime = 0;

        // 背包布局数据（物品占格位置）
        public Map<Integer, BackpackSlot> backpackLayout = new HashMap<>();

        // 撤离进度（断线续存用）
        public String evacuationZoneName = null;
        public int evacuationCurrentTick = 0;
        public int evacuationMaxTick = 0;
        public long evacuationStartTime = 0;

        // 玩家货币
        public int currencyAmount = 0;

        public PlayerData() {}
        public PlayerData(UUID uuid) {
            this.uuid = uuid;
            this.lastLoginTime = System.currentTimeMillis();
        }
    }

    /**
     * 背包格子 - 支持物品占多格和旋转
     */
    public static class BackpackSlot {
        public int slotIndex;       // 起始格子索引
        public int width = 1;       // 占格宽度
        public int height = 1;      // 占格高度
        public boolean rotated = false; // 是否旋转
        public String itemId;       // 物品ID
        public int count = 1;
        public String quality = "普通";

        public BackpackSlot() {}
        public BackpackSlot(int slotIndex, int width, int height, String itemId) {
            this.slotIndex = slotIndex;
            this.width = width;
            this.height = height;
            this.itemId = itemId;
        }
    }
}
