package com.extractionshooter.quality;

import com.extractionshooter.core.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * 品质管理器 - 管理物品的品质标签、外观、掉落倍率等。
 * 品质信息存储在 ItemStack 的 NBT 中: extraction_shooter.quality
 */
public class QualityManager {
    private final ConfigManager configManager;
    private final Random random = new Random();

    public QualityManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void load() {
        // 品质数据由 ConfigManager 管理
    }

    /**
     * 为物品堆应用随机品质（基于权重）
     */
    public void applyRandomQuality(ItemStack stack) {
        List<ConfigManager.QualityDef> qualities = configManager.getQualities();
        if (qualities.isEmpty()) return;

        // 简单权重：品质等级越高概率越低
        float totalWeight = 0;
        for (ConfigManager.QualityDef q : qualities) {
            totalWeight += (qualities.size() - q.order);
        }

        float roll = random.nextFloat() * totalWeight;
        float cumulative = 0;
        ConfigManager.QualityDef chosen = qualities.get(0);

        for (ConfigManager.QualityDef q : qualities) {
            cumulative += (qualities.size() - q.order);
            if (roll <= cumulative) {
                chosen = q;
                break;
            }
        }

        applyQuality(stack, chosen.name);
    }

    /**
     * 为物品堆应用指定品质
     */
    public void applyQuality(ItemStack stack, String qualityName) {
        ConfigManager.QualityDef quality = configManager.getQuality(qualityName);
        if (quality == null) return;

        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag esTag = tag.getCompound("extraction_shooter");
        esTag.putString("quality", qualityName);
        tag.put("extraction_shooter", esTag);
        stack.setTag(tag);

        // 更新显示名称
        updateDisplayName(stack, quality);
    }

    /**
     * 获取物品堆的品质名称
     */
    public String getQualityName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("extraction_shooter")) {
            CompoundTag esTag = tag.getCompound("extraction_shooter");
            if (esTag.contains("quality")) {
                return esTag.getString("quality");
            }
        }
        return "普通";
    }

    /**
     * 获取物品堆的品质定义
     */
    public ConfigManager.QualityDef getQuality(ItemStack stack) {
        String name = getQualityName(stack);
        return configManager.getQuality(name);
    }

    /**
     * 获取品质掉落倍率
     */
    public float getLootMultiplier(ItemStack stack) {
        ConfigManager.QualityDef q = getQuality(stack);
        return q != null ? q.lootMultiplier : 1.0f;
    }

    /**
     * 更新物品的显示名称，加上品质颜色前缀
     */
    private void updateDisplayName(ItemStack stack, ConfigManager.QualityDef quality) {
        MutableComponent original = (MutableComponent) stack.getHoverName();
        Style qualityStyle = Style.EMPTY.withColor(
                parseColorCode(quality.colorCode)
        );
        stack.setHoverName(
                Component.literal("")
                        .append(Component.literal("[" + quality.name + "] ")
                                .setStyle(qualityStyle))
                        .append(original)
        );
    }

    /**
     * 解析颜色代码为 ChatFormatting
     */
    private ChatFormatting parseColorCode(String code) {
        if (code.startsWith("§")) {
            char c = code.charAt(1);
            return ChatFormatting.getByCode(c);
        }
        try {
            return ChatFormatting.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatFormatting.WHITE;
        }
    }
}
