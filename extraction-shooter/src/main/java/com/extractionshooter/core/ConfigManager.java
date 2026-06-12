package com.extractionshooter.core;

import com.extractionshooter.ExtractionShooterMod;
import com.google.gson.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置管理器 - 所有 OP 指令修改的配置最终都通过此类读写 JSON 文件持久化。
 * 配置目录: ./config/extraction_shooter/
 */
public class ConfigManager {
    private final Path configDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ========== 品质配置 ==========
    private final List<QualityDef> qualities = new ArrayList<>();

    // ========== 物品定义配置 ==========
    private final Map<String, ItemDef> itemDefs = new ConcurrentHashMap<>();

    // ========== 刷新点配置 ==========
    private final List<SpawnPointDef> fixedSpawnPoints = new ArrayList<>();
    private final List<SpawnPointDef> tempSpawnPoints = new ArrayList<>();

    // ========== 撤离点配置 ==========
    private final Map<String, EvacuationZoneDef> evacuationZones = new ConcurrentHashMap<>();

    // ========== 全局配置 ==========
    private GlobalSettings globalSettings = new GlobalSettings();

    public ConfigManager() {
        this.configDir = Paths.get("config", ExtractionShooterMod.MOD_ID);
    }

    public void load() {
        try {
            Files.createDirectories(configDir);
            loadGlobalSettings();
            loadQualities();
            loadItemDefs();
            loadSpawnPoints();
            loadEvacuationZones();
            ExtractionShooterMod.LOGGER.info("配置加载完成");
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("配置加载失败", e);
        }
    }

    public void saveAll() {
        saveGlobalSettings();
        saveQualities();
        saveItemDefs();
        saveSpawnPoints();
        saveEvacuationZones();
    }

    // ==================== 全局设置 ====================

    public GlobalSettings getGlobalSettings() { return globalSettings; }

    public void setGlobalSettings(GlobalSettings gs) {
        this.globalSettings = gs;
        saveGlobalSettings();
    }

    private void loadGlobalSettings() {
        Path file = configDir.resolve("global_settings.json");
        if (Files.exists(file)) {
            try {
                globalSettings = gson.fromJson(Files.newBufferedReader(file), GlobalSettings.class);
            } catch (Exception e) {
                ExtractionShooterMod.LOGGER.error("读取全局配置失败，使用默认值", e);
            }
        }
        if (globalSettings == null) globalSettings = new GlobalSettings();
        saveGlobalSettings();
    }

    private void saveGlobalSettings() {
        try {
            Files.writeString(configDir.resolve("global_settings.json"), gson.toJson(globalSettings));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存全局配置失败", e);
        }
    }

    // ==================== 品质配置 ====================

    public List<QualityDef> getQualities() { return qualities; }

    public void addQuality(QualityDef def) {
        qualities.removeIf(q -> q.name.equals(def.name));
        qualities.add(def);
        qualities.sort(Comparator.comparingInt(q -> q.order));
        saveQualities();
    }

    public void removeQuality(String name) {
        qualities.removeIf(q -> q.name.equals(name));
        saveQualities();
    }

    public QualityDef getQuality(String name) {
        return qualities.stream().filter(q -> q.name.equals(name)).findFirst().orElse(null);
    }

    public QualityDef getQualityByOrder(int order) {
        return qualities.stream().filter(q -> q.order == order).findFirst().orElse(null);
    }

    private void loadQualities() {
        Path file = configDir.resolve("qualities.json");
        if (Files.exists(file)) {
            try {
                QualityDef[] arr = gson.fromJson(Files.newBufferedReader(file), QualityDef[].class);
                if (arr != null) {
                    qualities.clear();
                    qualities.addAll(Arrays.asList(arr));
                    qualities.sort(Comparator.comparingInt(q -> q.order));
                }
            } catch (Exception e) {
                ExtractionShooterMod.LOGGER.error("读取品质配置失败", e);
            }
        }
        if (qualities.isEmpty()) {
            // 默认品质
            qualities.add(new QualityDef("普通", 0, "§7", "white", 1.0f, 0));
            qualities.add(new QualityDef("优秀", 1, "§a", "green", 1.5f, 1));
            qualities.add(new QualityDef("稀有", 2, "§9", "blue", 2.0f, 2));
            qualities.add(new QualityDef("史诗", 3, "§5", "dark_purple", 3.0f, 3));
            qualities.add(new QualityDef("传说", 4, "§6", "gold", 5.0f, 4));
            saveQualities();
        }
    }

    private void saveQualities() {
        try {
            Files.writeString(configDir.resolve("qualities.json"), gson.toJson(qualities));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存品质配置失败", e);
        }
    }

    // ==================== 物品定义 ====================

    public Map<String, ItemDef> getItemDefs() { return itemDefs; }

    public void setItemDef(String key, ItemDef def) {
        itemDefs.put(key, def);
        saveItemDefs();
    }

    public void removeItemDef(String key) {
        itemDefs.remove(key);
        saveItemDefs();
    }

    public ItemDef getItemDef(String key) { return itemDefs.get(key); }

    private void loadItemDefs() {
        Path file = configDir.resolve("item_defs.json");
        if (Files.exists(file)) {
            try {
                Map<String, ItemDef> loaded = gson.fromJson(
                        Files.newBufferedReader(file),
                        new com.google.gson.reflect.TypeToken<Map<String, ItemDef>>(){}.getType()
                );
                if (loaded != null) {
                    itemDefs.clear();
                    itemDefs.putAll(loaded);
                }
            } catch (Exception e) {
                ExtractionShooterMod.LOGGER.error("读取物品定义失败", e);
            }
        }
    }

    private void saveItemDefs() {
        try {
            Files.writeString(configDir.resolve("item_defs.json"), gson.toJson(itemDefs));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存物品定义失败", e);
        }
    }

    // ==================== 刷新点配置 ====================

    public List<SpawnPointDef> getFixedSpawnPoints() { return fixedSpawnPoints; }
    public List<SpawnPointDef> getTempSpawnPoints() { return tempSpawnPoints; }

    public void addSpawnPoint(SpawnPointDef point, boolean fixed) {
        (fixed ? fixedSpawnPoints : tempSpawnPoints).add(point);
        saveSpawnPoints();
    }

    public void removeSpawnPoint(String id) {
        fixedSpawnPoints.removeIf(p -> p.id.equals(id));
        tempSpawnPoints.removeIf(p -> p.id.equals(id));
        saveSpawnPoints();
    }

    private void loadSpawnPoints() {
        Path fixedFile = configDir.resolve("fixed_spawns.json");
        Path tempFile = configDir.resolve("temp_spawns.json");
        try {
            if (Files.exists(fixedFile)) {
                SpawnPointDef[] arr = gson.fromJson(Files.newBufferedReader(fixedFile), SpawnPointDef[].class);
                if (arr != null) { fixedSpawnPoints.clear(); fixedSpawnPoints.addAll(Arrays.asList(arr)); }
            }
            if (Files.exists(tempFile)) {
                SpawnPointDef[] arr = gson.fromJson(Files.newBufferedReader(tempFile), SpawnPointDef[].class);
                if (arr != null) { tempSpawnPoints.clear(); tempSpawnPoints.addAll(Arrays.asList(arr)); }
            }
        } catch (Exception e) {
            ExtractionShooterMod.LOGGER.error("读取刷新点配置失败", e);
        }
    }

    private void saveSpawnPoints() {
        try {
            Files.writeString(configDir.resolve("fixed_spawns.json"), gson.toJson(fixedSpawnPoints));
            Files.writeString(configDir.resolve("temp_spawns.json"), gson.toJson(tempSpawnPoints));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存刷新点配置失败", e);
        }
    }

    // ==================== 撤离点配置 ====================

    public Map<String, EvacuationZoneDef> getEvacuationZones() { return evacuationZones; }

    public void setEvacuationZone(String name, EvacuationZoneDef zone) {
        evacuationZones.put(name, zone);
        saveEvacuationZones();
    }

    public void removeEvacuationZone(String name) {
        evacuationZones.remove(name);
        saveEvacuationZones();
    }

    private void loadEvacuationZones() {
        Path file = configDir.resolve("evacuation_zones.json");
        if (Files.exists(file)) {
            try {
                Map<String, EvacuationZoneDef> loaded = gson.fromJson(
                        Files.newBufferedReader(file),
                        new com.google.gson.reflect.TypeToken<Map<String, EvacuationZoneDef>>(){}.getType()
                );
                if (loaded != null) {
                    evacuationZones.clear();
                    evacuationZones.putAll(loaded);
                }
            } catch (Exception e) {
                ExtractionShooterMod.LOGGER.error("读取撤离点配置失败", e);
            }
        }
    }

    private void saveEvacuationZones() {
        try {
            Files.writeString(configDir.resolve("evacuation_zones.json"), gson.toJson(evacuationZones));
        } catch (IOException e) {
            ExtractionShooterMod.LOGGER.error("保存撤离点配置失败", e);
        }
    }

    // ==================== 内部数据类 ====================

    public static class GlobalSettings {
        public boolean enableBackpackLimit = true;
        public boolean enableLootSystem = true;
        public boolean enableDeathDropToContainer = true;
        public boolean enableEvacuation = true;
        public boolean enableSharedInventory = false;
        public int defaultBackpackRows = 6;
        public int defaultBackpackCols = 9;
        public int evacuationTickInterval = 20;
        public int disconnectSaveInterval = 100;
        public String defaultCurrencyItem = "minecraft:emerald";
    }

    public static class QualityDef {
        public String name;
        public int order;
        public String colorCode;
        public String particleColor;
        public float lootMultiplier;
        public int minTier;

        public QualityDef() {}
        public QualityDef(String name, int order, String colorCode, String particleColor,
                          float lootMultiplier, int minTier) {
            this.name = name;
            this.order = order;
            this.colorCode = colorCode;
            this.particleColor = particleColor;
            this.lootMultiplier = lootMultiplier;
            this.minTier = minTier;
        }
    }

    public static class ItemDef {
        public String itemId;          // 物品注册名，如 "minecraft:diamond_sword"
        public int width = 1;          // 占格宽度
        public int height = 1;         // 占格高度
        public boolean rotatable = false; // 是否可旋转
        public String defaultQuality = "普通";
        public int maxStackSize = 1;
        public String customModelData = "";

        public Item getItem() {
            ResourceLocation rl = new ResourceLocation(itemId);
            if (ForgeRegistries.ITEMS.containsKey(rl)) {
                return ForgeRegistries.ITEMS.getValue(rl);
            }
            return Items.AIR;
        }
    }

    public static class SpawnPointDef {
        public String id;
        public String world;
        public double x, y, z;
        public float radius = 5.0f;
        public String containerType = "CHEST"; // CHEST, BARREL, SHULKER, LOOT_CONTAINER
        public String lootTableId = "default";
        public int refreshInterval = 6000;     // tick
        public boolean isActive = true;
        public long lastRefreshTime = 0;
    }

    public static class EvacuationZoneDef {
        public String name;
        public String world;
        public double x1, y1, z1, x2, y2, z2;  // 区域对角
        public int extractTime = 100;            // 撤离读条 tick
        public int costExpLevels = 0;
        public Map<String, Integer> costItems = new HashMap<>(); // itemId -> count
        public int costCurrency = 0;
        public String currencyItem = "minecraft:emerald";
        public String onEnterCommand = "";
        public String onSuccessCommand = "";
        public String onFailCommand = "";
        public String onDisconnectCommand = "";
        public boolean allowDisconnectResume = true;
    }
}
