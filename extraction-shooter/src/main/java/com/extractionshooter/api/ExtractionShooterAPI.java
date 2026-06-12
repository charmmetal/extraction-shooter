package com.extractionshooter.api;

import com.extractionshooter.ExtractionShooterMod;
import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.data.PlayerDataManager;
import com.extractionshooter.evacuation.EvacuationManager;
import com.extractionshooter.loot.LootManager;
import com.extractionshooter.quality.QualityManager;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Extraction Shooter Mod 对外 API
 * 其他 Mod 可通过此 API 与本 Mod 交互
 */
public class ExtractionShooterAPI {

    private static ExtractionShooterMod getMod() {
        return ExtractionShooterMod.getInstance();
    }

    // ==================== 品质 API ====================

    /**
     * 为物品应用随机品质
     */
    public static void applyRandomQuality(ItemStack stack) {
        getMod().getQualityManager().applyRandomQuality(stack);
    }

    /**
     * 为物品应用指定品质
     */
    public static void applyQuality(ItemStack stack, String qualityName) {
        getMod().getQualityManager().applyQuality(stack, qualityName);
    }

    /**
     * 获取物品品质名称
     */
    public static String getQualityName(ItemStack stack) {
        return getMod().getQualityManager().getQualityName(stack);
    }

    /**
     * 获取物品品质掉落倍率
     */
    public static float getLootMultiplier(ItemStack stack) {
        return getMod().getQualityManager().getLootMultiplier(stack);
    }

    // ==================== 玩家数据 API ====================

    /**
     * 获取玩家货币数量
     */
    public static int getPlayerCurrency(UUID uuid) {
        return getMod().getPlayerDataManager().getData(uuid).currencyAmount;
    }

    /**
     * 设置玩家货币数量
     */
    public static void setPlayerCurrency(UUID uuid, int amount) {
        getMod().getPlayerDataManager().getData(uuid).currencyAmount = amount;
        getMod().getPlayerDataManager().savePlayer(uuid);
    }

    /**
     * 增加玩家货币
     */
    public static void addPlayerCurrency(UUID uuid, int amount) {
        PlayerDataManager.PlayerData data = getMod().getPlayerDataManager().getData(uuid);
        data.currencyAmount += amount;
        getMod().getPlayerDataManager().savePlayer(uuid);
    }

    // ==================== 配置 API ====================

    /**
     * 重新加载配置
     */
    public static void reloadConfig() {
        getMod().getConfigManager().load();
    }

    /**
     * 获取全局设置
     */
    public static ConfigManager.GlobalSettings getGlobalSettings() {
        return getMod().getConfigManager().getGlobalSettings();
    }
}
