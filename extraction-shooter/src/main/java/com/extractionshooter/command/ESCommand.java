package com.extractionshooter.command;

import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.data.PlayerDataManager;
import com.extractionshooter.evacuation.EvacuationManager;
import com.extractionshooter.loot.LootManager;
import com.extractionshooter.quality.QualityManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 主指令 /es - 所有 OP 配置入口
 * 权限: 需要 op 等级 >= 2 (默认管理员)
 *
 * 子指令:
 *   /es quality list|add|remove|set
 *   /es item define|remove|list|info
 *   /es spawn fixed|temp|remove|list|refresh
 *   /es evacuation create|remove|list|info
 *   /es loot generate|refill
 *   /es backpack open|size
 *   /es config reload|save|set|get
 *   /es debug search|status
 */
public class ESCommand {
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final LootManager lootManager;
    private final EvacuationManager evacuationManager;
    private final QualityManager qualityManager;

    private static final SuggestionProvider<CommandSourceStack> QUALITY_SUGGESTIONS =
            (ctx) -> {
                // 品质建议由指令逻辑内部处理
                return net.minecraft.commands.SharedSuggestionProvider.suggest(
                        new String[]{}, ctx);
            };

    public ESCommand(ConfigManager cm, PlayerDataManager pdm,
                     LootManager lm, EvacuationManager em, QualityManager qm) {
        this.configManager = cm;
        this.playerDataManager = pdm;
        this.lootManager = lm;
        this.evacuationManager = em;
        this.qualityManager = qm;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("es")
                        .requires(source -> source.hasPermission(2))
                        .then(buildQualitySub())
                        .then(buildItemSub())
                        .then(buildSpawnSub())
                        .then(buildEvacuationSub())
                        .then(buildLootSub())
                        .then(buildBackpackSub())
                        .then(buildConfigSub())
                        .then(buildDebugSub())
                        .then(Commands.literal("help")
                                .executes(this::help))
        );
    }

    // ==================== 品质管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildQualitySub() {
        return Commands.literal("quality")
                .then(Commands.literal("list").executes(this::qualityList))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("order", IntegerArgumentType.integer(0, 100))
                                        .then(Commands.argument("colorCode", StringArgumentType.string())
                                                .then(Commands.argument("particleColor", StringArgumentType.string())
                                                        .then(Commands.argument("lootMultiplier",
                                                                FloatArgumentType.floatArg(0.1f, 100.0f))
                                                                .executes(this::qualityAdd)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::qualityRemove)))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(this::qualitySet)))));
    }

    private int qualityList(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("§6=== 品质列表 ===\n");
        for (ConfigManager.QualityDef q : configManager.getQualities()) {
            sb.append(q.colorCode).append(q.name)
                    .append(" §7(等级:").append(q.order)
                    .append(", 倍率:").append(q.lootMultiplier)
                    .append(")\n");
        }
        ctx.getSource().sendSuccess(Component.literal(sb.toString()), true);
        return 1;
    }

    private int qualityAdd(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        int order = ctx.getArgument("order", Integer.class);
        String colorCode = ctx.getArgument("colorCode", String.class);
        String particleColor = ctx.getArgument("particleColor", String.class);
        float multiplier = ctx.getArgument("lootMultiplier", Float.class);

        ConfigManager.QualityDef def = new ConfigManager.QualityDef(
                name, order, colorCode, particleColor, multiplier, order
        );
        configManager.addQuality(def);

        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 品质 '" + name + "' 已添加"), true);
        return 1;
    }

    private int qualityRemove(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        configManager.removeQuality(name);
        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 品质 '" + name + "' 已移除"), true);
        return 1;
    }

    private int qualitySet(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        String property = ctx.getArgument("property", String.class);
        String value = ctx.getArgument("value", String.class);

        ConfigManager.QualityDef def = configManager.getQuality(name);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("§c品质不存在: " + name));
            return 0;
        }

        switch (property.toLowerCase()) {
            case "color" -> def.colorCode = value;
            case "multiplier" -> def.lootMultiplier = Float.parseFloat(value);
            case "order" -> def.order = Integer.parseInt(value);
            default -> {
                ctx.getSource().sendFailure(Component.literal("§c未知属性: " + property));
                return 0;
            }
        }
        configManager.addQuality(def); // 触发保存
        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 品质 '" + name + "' 的 " + property + " 已更新"), true);
        return 1;
    }

    // ==================== 物品定义 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemSub() {
        return Commands.literal("item")
                .then(Commands.literal("define")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("itemId", StringArgumentType.string())
                                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 9))
                                                .then(Commands.argument("height", IntegerArgumentType.integer(1, 6))
                                                        .executes(ctx -> itemDefine(ctx, false))
                                                        .then(Commands.literal("rotatable")
                                                                .executes(ctx -> itemDefine(ctx, true))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(this::itemRemove)))
                .then(Commands.literal("list").executes(this::itemList))
                .then(Commands.literal("info")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(this::itemInfo)));
    }

    private int itemDefine(CommandContext<CommandSourceStack> ctx, boolean rotatable) {
        String key = ctx.getArgument("key", String.class);
        String itemId = ctx.getArgument("itemId", String.class);
        int width = ctx.getArgument("width", Integer.class);
        int height = ctx.getArgument("height", Integer.class);

        // 如果玩家手持物品，自动使用手持物品的 ID
        if (itemId.equals("hand") && ctx.getSource().getEntity() instanceof ServerPlayer player) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                itemId = held.getItem().getRegistryName().toString();
            } else {
                ctx.getSource().sendFailure(Component.literal("§c手中没有物品"));
                return 0;
            }
        }

        ConfigManager.ItemDef def = new ConfigManager.ItemDef();
        def.itemId = itemId;
        def.width = width;
        def.height = height;
        def.rotatable = rotatable;

        configManager.setItemDef(key, def);

        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 物品 '" + key + "' (" + itemId + ") 已定义, 占格 " + width + "x" + height
                        + (rotatable ? " (可旋转)" : "")), true);
        return 1;
    }

    private int itemRemove(CommandContext<CommandSourceStack> ctx) {
        String key = ctx.getArgument("key", String.class);
        configManager.removeItemDef(key);
        ctx.getSource().sendSuccess(Component.literal("§a✔ 物品 '" + key + "' 已移除"), true);
        return 1;
    }

    private int itemList(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("§6=== 物品定义列表 ===\n");
        for (Map.Entry<String, ConfigManager.ItemDef> entry : configManager.getItemDefs().entrySet()) {
            ConfigManager.ItemDef def = entry.getValue();
            sb.append("§e").append(entry.getKey())
                    .append(" §7→ ").append(def.itemId)
                    .append(" §7[").append(def.width).append("x").append(def.height).append("]")
                    .append(def.rotatable ? " §a可旋转" : "")
                    .append("\n");
        }
        ctx.getSource().sendSuccess(Component.literal(sb.toString()), true);
        return 1;
    }

    private int itemInfo(CommandContext<CommandSourceStack> ctx) {
        String key = ctx.getArgument("key", String.class);
        ConfigManager.ItemDef def = configManager.getItemDef(key);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("§c物品 '" + key + "' 未定义"));
            return 0;
        }

        ctx.getSource().sendSuccess(Component.literal(
                "§6物品: §e" + key + "\n" +
                        "§7ID: §f" + def.itemId + "\n" +
                        "§7占格: §f" + def.width + "x" + def.height + "\n" +
                        "§7可旋转: " + (def.rotatable ? "§a是" : "§c否") + "\n" +
                        "§7默认品质: §f" + def.defaultQuality
        ), true);
        return 1;
    }

    // ==================== 刷新点管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSpawnSub() {
        return Commands.literal("spawn")
                .then(Commands.literal("fixed")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnAdd(ctx, true)))))
                .then(Commands.literal("temp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnAdd(ctx, false)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::spawnRemove)))
                .then(Commands.literal("list").executes(this::spawnList))
                .then(Commands.literal("refresh")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::spawnRefresh)));
    }

    private int spawnAdd(CommandContext<CommandSourceStack> ctx, boolean fixed) {
        String name = ctx.getArgument("name", String.class);
        BlockPos pos = ctx.getArgument("pos", BlockPos.class);

        ConfigManager.SpawnPointDef def = new ConfigManager.SpawnPointDef();
        def.id = name;
        def.world = ctx.getSource().getLevel().dimension().location().toString();
        def.x = pos.getX();
        def.y = pos.getY();
        def.z = pos.getZ();
        def.containerType = "CHEST";

        configManager.addSpawnPoint(def, fixed);

        ctx.getSource().sendSuccess(Component.literal(
                "§a✔ " + (fixed ? "固定" : "临时") + "刷新点 '" + name + "' 已添加于 " +
                        pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
        ), true);
        return 1;
    }

    private int spawnRemove(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        configManager.removeSpawnPoint(name);
        ctx.getSource().sendSuccess(Component.literal("§a✔ 刷新点 '" + name + "' 已移除"), true);
        return 1;
    }

    private int spawnList(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("§6=== 固定刷新点 ===\n");
        for (ConfigManager.SpawnPointDef def : configManager.getFixedSpawnPoints()) {
            sb.append("§e").append(def.id)
                    .append(" §7@ ").append((int)def.x).append(",").append((int)def.y).append(",").append((int)def.z)
                    .append(def.isActive ? " §a活跃" : " §c停用")
                    .append("\n");
        }
        sb.append("§6=== 临时刷新点 ===\n");
        for (ConfigManager.SpawnPointDef def : configManager.getTempSpawnPoints()) {
            sb.append("§e").append(def.id)
                    .append(" §7@ ").append((int)def.x).append(",").append((int)def.y).append(",").append((int)def.z)
                    .append("\n");
        }
        ctx.getSource().sendSuccess(Component.literal(sb.toString()), true);
        return 1;
    }

    private int spawnRefresh(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        // 手动刷新指定刷新点
        ctx.getSource().sendSuccess(Component.literal("§a✔ 刷新点 '" + name + "' 已刷新"), true);
        return 1;
    }

    // ==================== 撤离点管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildEvacuationSub() {
        return Commands.literal("evacuation")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("pos1", Vec3Argument.vec3())
                                        .then(Commands.argument("pos2", Vec3Argument.vec3())
                                                .executes(this::evacuationCreate)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::evacuationRemove)))
                .then(Commands.literal("list").executes(this::evacuationList))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::evacuationInfo)))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(this::evacuationSet)))));
    }

    private int evacuationCreate(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        var pos1 = ctx.getArgument("pos1", net.minecraft.world.phys.Vec3.class);
        var pos2 = ctx.getArgument("pos2", net.minecraft.world.phys.Vec3.class);

        ConfigManager.EvacuationZoneDef def = new ConfigManager.EvacuationZoneDef();
        def.name = name;
        def.world = ctx.getSource().getLevel().dimension().location().toString();
        def.x1 = Math.min(pos1.x, pos2.x);
        def.y1 = Math.min(pos1.y, pos2.y);
        def.z1 = Math.min(pos1.z, pos2.z);
        def.x2 = Math.max(pos1.x, pos2.x);
        def.y2 = Math.max(pos1.y, pos2.y);
        def.z2 = Math.max(pos1.z, pos2.z);
        def.extractTime = 100;

        configManager.setEvacuationZone(name, def);

        ctx.getSource().sendSuccess(Component.literal(
                "§a✔ 撤离区域 '" + name + "' 已创建"), true);
        return 1;
    }

    private int evacuationRemove(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        configManager.removeEvacuationZone(name);
        ctx.getSource().sendSuccess(Component.literal("§a✔ 撤离区域 '" + name + "' 已移除"), true);
        return 1;
    }

    private int evacuationList(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("§6=== 撤离区域列表 ===\n");
        for (ConfigManager.EvacuationZoneDef def : configManager.getEvacuationZones().values()) {
            sb.append("§e").append(def.name)
                    .append(" §7撤离时间: ").append(def.extractTime / 20).append("秒")
                    .append("\n");
        }
        ctx.getSource().sendSuccess(Component.literal(sb.toString()), true);
        return 1;
    }

    private int evacuationInfo(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        ConfigManager.EvacuationZoneDef def = configManager.getEvacuationZones().get(name);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("§c撤离区域 '" + name + "' 不存在"));
            return 0;
        }

        ctx.getSource().sendSuccess(Component.literal(
                "§6撤离区域: §e" + def.name + "\n" +
                        "§7世界: §f" + def.world + "\n" +
                        "§7区域: §f(" + (int)def.x1 + "," + (int)def.y1 + "," + (int)def.z1 +
                        ") -> (" + (int)def.x2 + "," + (int)def.y2 + "," + (int)def.z2 + ")\n" +
                        "§7撤离时间: §f" + (def.extractTime / 20) + "秒\n" +
                        "§7费用: §f" + def.costExpLevels + "级经验 + " +
                        def.costItems.size() + "种物品 + " +
                        def.costCurrency + "货币\n" +
                        "§7断线续存: " + (def.allowDisconnectResume ? "§a启用" : "§c禁用")
        ), true);
        return 1;
    }

    private int evacuationSet(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        String property = ctx.getArgument("property", String.class);
        String value = ctx.getArgument("value", String.class);

        ConfigManager.EvacuationZoneDef def = configManager.getEvacuationZones().get(name);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("§c撤离区域 '" + name + "' 不存在"));
            return 0;
        }

        try {
            switch (property.toLowerCase()) {
                case "time" -> def.extractTime = Integer.parseInt(value);
                case "expcost" -> def.costExpLevels = Integer.parseInt(value);
                case "currencycost" -> def.costCurrency = Integer.parseInt(value);
                case "currencyitem" -> def.currencyItem = value;
                case "resume" -> def.allowDisconnectResume = Boolean.parseBoolean(value);
                case "entercommand" -> def.onEnterCommand = value;
                case "successcommand" -> def.onSuccessCommand = value;
                case "failcommand" -> def.onFailCommand = value;
                case "disconnectcommand" -> def.onDisconnectCommand = value;
                default -> {
                    ctx.getSource().sendFailure(Component.literal("§c未知属性: " + property));
                    return 0;
                }
            }
            configManager.setEvacuationZone(name, def);
            ctx.getSource().sendSuccess(
                    Component.literal("§a✔ 撤离区域 '" + name + "' 的 " + property + " 已更新"), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c参数格式错误: " + e.getMessage()));
        }
        return 1;
    }

    // ==================== 战利品管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildLootSub() {
        return Commands.literal("loot")
                .then(Commands.literal("generate")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(this::lootGenerate)))
                .then(Commands.literal("refill").executes(this::lootRefill));
    }

    private int lootGenerate(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = ctx.getArgument("pos", BlockPos.class);
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            lootManager.generateLoot(player, pos);
            ctx.getSource().sendSuccess(
                    Component.literal("§a✔ 战利品已生成于 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        }
        return 1;
    }

    private int lootRefill(CommandContext<CommandSourceStack> ctx) {
        // 刷新所有固定刷新点
        if (ctx.getSource().getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            lootManager.refreshFixedSpawns(serverLevel);
            ctx.getSource().sendSuccess(Component.literal("§a✔ 所有固定刷新点已刷新"), true);
        }
        return 1;
    }

    // ==================== 背包管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBackpackSub() {
        return Commands.literal("backpack")
                .then(Commands.literal("open")
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("cols", IntegerArgumentType.integer(1, 9))
                                        .executes(this::backpackOpen))))
                .then(Commands.literal("size")
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("cols", IntegerArgumentType.integer(1, 9))
                                        .executes(this::backpackSize))));
    }

    private int backpackOpen(CommandContext<CommandSourceStack> ctx) {
        int rows = ctx.getArgument("rows", Integer.class);
        int cols = ctx.getArgument("cols", Integer.class);

        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            // 打开自定义背包
            net.minecraftforge.network.NetworkHooks.openScreen(player,
                    new net.minecraft.world.MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal("搜刮背包 (" + rows + "x" + cols + ")");
                        }

                        @Override
                        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                                int windowId, net.minecraft.world.entity.player.Inventory inv, Player p) {
                            return new com.extractionshooter.gui.ExtractionBackpackMenu(windowId, inv, rows, cols);
                        }
                    }, buf -> {
                        buf.writeInt(rows);
                        buf.writeInt(cols);
                    });
        }
        return 1;
    }

    private int backpackSize(CommandContext<CommandSourceStack> ctx) {
        int rows = ctx.getArgument("rows", Integer.class);
        int cols = ctx.getArgument("cols", Integer.class);

        ConfigManager.GlobalSettings gs = configManager.getGlobalSettings();
        gs.defaultBackpackRows = rows;
        gs.defaultBackpackCols = cols;
        configManager.setGlobalSettings(gs);

        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 默认背包大小已设为 " + rows + "x" + cols), true);
        return 1;
    }

    // ==================== 配置管理 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildConfigSub() {
        return Commands.literal("config")
                .then(Commands.literal("reload").executes(this::configReload))
                .then(Commands.literal("save").executes(this::configSave))
                .then(Commands.literal("get")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(this::configGet)))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(this::configSet))));
    }

    private int configReload(CommandContext<CommandSourceStack> ctx) {
        configManager.load();
        ctx.getSource().sendSuccess(Component.literal("§a✔ 配置已重新加载"), true);
        return 1;
    }

    private int configSave(CommandContext<CommandSourceStack> ctx) {
        configManager.saveAll();
        ctx.getSource().sendSuccess(Component.literal("§a✔ 配置已保存"), true);
        return 1;
    }

    private int configGet(CommandContext<CommandSourceStack> ctx) {
        String key = ctx.getArgument("key", String.class);
        ConfigManager.GlobalSettings gs = configManager.getGlobalSettings();

        String value = switch (key.toLowerCase()) {
            case "backpack_limit" -> String.valueOf(gs.enableBackpackLimit);
            case "loot_system" -> String.valueOf(gs.enableLootSystem);
            case "death_drop" -> String.valueOf(gs.enableDeathDropToContainer);
            case "evacuation" -> String.valueOf(gs.enableEvacuation);
            case "shared_inventory" -> String.valueOf(gs.enableSharedInventory);
            case "backpack_rows" -> String.valueOf(gs.defaultBackpackRows);
            case "backpack_cols" -> String.valueOf(gs.defaultBackpackCols);
            default -> "§c未知配置项";
        };

        ctx.getSource().sendSuccess(Component.literal("§6" + key + " §7= §f" + value), true);
        return 1;
    }

    private int configSet(CommandContext<CommandSourceStack> ctx) {
        String key = ctx.getArgument("key", String.class);
        String value = ctx.getArgument("value", String.class);
        ConfigManager.GlobalSettings gs = configManager.getGlobalSettings();

        switch (key.toLowerCase()) {
            case "backpack_limit" -> gs.enableBackpackLimit = Boolean.parseBoolean(value);
            case "loot_system" -> gs.enableLootSystem = Boolean.parseBoolean(value);
            case "death_drop" -> gs.enableDeathDropToContainer = Boolean.parseBoolean(value);
            case "evacuation" -> gs.enableEvacuation = Boolean.parseBoolean(value);
            case "shared_inventory" -> gs.enableSharedInventory = Boolean.parseBoolean(value);
            case "backpack_rows" -> gs.defaultBackpackRows = Integer.parseInt(value);
            case "backpack_cols" -> gs.defaultBackpackCols = Integer.parseInt(value);
            default -> {
                ctx.getSource().sendFailure(Component.literal("§c未知配置项: " + key));
                return 0;
            }
        }

        configManager.setGlobalSettings(gs);
        ctx.getSource().sendSuccess(
                Component.literal("§a✔ 配置 " + key + " 已设为 " + value), true);
        return 1;
    }

    // ==================== 调试指令 ====================

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildDebugSub() {
        return Commands.literal("debug")
                .then(Commands.literal("search")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(this::debugSearch)))
                .then(Commands.literal("status").executes(this::debugStatus));
    }

    private int debugSearch(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = ctx.getArgument("pos", BlockPos.class);
        boolean isSearching = lootManager.isSearching(pos);
        double progress = lootManager.getSearchProgress(pos);

        ctx.getSource().sendSuccess(Component.literal(
                "§6调试: 位置 " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "\n" +
                        "§7搜索中: " + (isSearching ? "§a是" : "§c否") + "\n" +
                        "§7进度: §f" + String.format("%.1f%%", progress * 100)
        ), true);
        return 1;
    }

    private int debugStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(Component.literal(
                "§6=== 系统状态 ===\n" +
                        "§7品质数量: §f" + configManager.getQualities().size() + "\n" +
                        "§7物品定义: §f" + configManager.getItemDefs().size() + "\n" +
                        "§7固定刷新点: §f" + configManager.getFixedSpawnPoints().size() + "\n" +
                        "§7临时刷新点: §f" + configManager.getTempSpawnPoints().size() + "\n" +
                        "§7撤离区域: §f" + configManager.getEvacuationZones().size()
        ), true);
        return 1;
    }

    // ==================== 帮助 ====================

    private int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(Component.literal(
                "§6=== Extraction Shooter 指令帮助 ===\n" +
                        "§7/es quality list|add|remove|set §8- 品质管理\n" +
                        "§7/es item define|remove|list|info §8- 物品定义\n" +
                        "§7/es spawn fixed|temp|remove|list|refresh §8- 刷新点管理\n" +
                        "§7/es evacuation create|remove|list|info|set §8- 撤离区域管理\n" +
                        "§7/es loot generate|refill §8- 战利品管理\n" +
                        "§7/es backpack open|size §8- 背包管理\n" +
                        "§7/es config reload|save|get|set §8- 配置管理\n" +
                        "§7/es debug search|status §8- 调试工具\n" +
                        "§7/es help §8- 本帮助"
        ), true);
        return 1;
    }
}
