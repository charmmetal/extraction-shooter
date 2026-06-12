# Extraction Shooter Mod

**一个搜打撤玩法的 Forge 1.20.1 Mod**

---

## 概述

Extraction Shooter 是一个以"搜刮-战斗-撤离"为核心循环的 Minecraft Forge Mod。它改造了原版背包系统，引入了物品占格与旋转机制，并提供了完整的搜刮流程、品质系统、死亡掉落、刷新点管理和撤离规则，适合用于 PvE 生存服务器、战术竞技地图或硬核生存玩法。

---

## 核心玩法

### 1. 背包容量限制
- 物品按**长×宽**占格，不再统一占1格
- 支持物品**旋转摆放**，优化空间利用率
- 背包大小可通过指令自定义（默认 6×9）

### 2. 搜刮流程
- **未搜索** → **搜索中** → **可拾取** 三阶段
- 搜索时播放进度提示与音效反馈
- 容器内战利品随机生成，品质随机分配

### 3. 品质系统
- 5个默认品质等级：普通、优秀、稀有、史诗、传说
- 品质影响物品显示颜色、粒子效果、掉落倍率
- 品质可完全自定义（名称、颜色、倍率、等级）

### 4. 死亡掉落改造
- 玩家死亡后，背包物品自动转入**死亡搜刮容器**
- 容器在死亡位置生成，其他玩家可搜刮
- 支持配置是否启用

### 5. 刷新点系统
- **固定刷新点**：周期性刷新战利品，适合地图资源投放
- **临时刷新点**：一次性刷新，适合战斗掉落
- 支持设置刷新间隔、容器类型、战利品表

### 6. 撤离系统
- 在指定区域触发撤离读条
- 支持撤离费用（经验等级 + 物品 + 自定义货币）
- 撤离成功/失败/断线均可执行自定义命令
- **断线续存**：掉线后撤离进度保留，重连可继续
- 撤离倒计时实时显示

### 7. 多人模式
- 支持**共享库存**（所有玩家共用）或**独立库存**
- 每个玩家有独立的背包布局数据

---

## 指令文档

所有指令需要 **OP 等级 ≥ 2**（管理员权限）。

### 品质管理

```
/es quality list
  - 列出所有品质

/es quality add <名称> <等级> <颜色代码> <粒子颜色> <掉落倍率>
  - 添加新品质
  - 示例: /es quality add 神话 5 §4 dark_red 8.0

/es quality remove <名称>
  - 移除品质

/es quality set <名称> <属性> <值>
  - 修改品质属性
  - 属性: color, multiplier, order
  - 示例: /es quality set 传说 multiplier 6.0
```

### 物品定义

```
/es item define <键名> <物品ID> <宽度> <高度> [rotatable]
  - 定义物品的占格大小
  - 物品ID 可用 "hand" 自动使用手持物品
  - 示例: /es item define rifle minecraft:bow 2 3 rotatable

/es item remove <键名>
  - 移除物品定义

/es item list
  - 列出所有物品定义

/es item info <键名>
  - 查看物品定义详情
```

### 刷新点管理

```
/es spawn fixed <名称> <坐标>
  - 添加固定刷新点
  - 示例: /es spawn fixed village_chest 100 64 200

/es spawn temp <名称> <坐标>
  - 添加临时刷新点

/es spawn remove <名称>
  - 移除刷新点

/es spawn list
  - 列出所有刷新点

/es spawn refresh <名称>
  - 手动刷新指定刷新点
```

### 撤离区域管理

```
/es evacuation create <名称> <坐标1> <坐标2>
  - 创建撤离区域（两个对角坐标定义长方体区域）
  - 示例: /es evacuation create zone1 10 60 10 20 70 20

/es evacuation remove <名称>
  - 移除撤离区域

/es evacuation list
  - 列出所有撤离区域

/es evacuation info <名称>
  - 查看撤离区域详情

/es evacuation set <名称> <属性> <值>
  - 修改撤离区域属性
  - 属性:
    - time        - 撤离读条时间（tick，20=1秒）
    - expcost     - 经验等级费用
    - currencycost - 货币费用数量
    - currencyitem - 货币物品ID
    - resume      - 是否允许断线续存 (true/false)
    - entercommand - 进入区域时执行的命令
    - successcommand - 撤离成功时执行的命令
    - failcommand   - 撤离失败时执行的命令
    - disconnectcommand - 断线时执行的命令
  - 示例: /es evacuation set zone1 time 200
  - 命令中可使用 %player% 占位符
```

### 战利品管理

```
/es loot generate <坐标>
  - 在指定位置生成战利品

/es loot refill
  - 刷新所有固定刷新点
```

### 背包管理

```
/es backpack open <行数> <列数>
  - 打开自定义大小的背包（仅自己）
  - 示例: /es backpack open 6 9

/es backpack size <行数> <列数>
  - 设置默认背包大小
```

### 配置管理

```
/es config reload
  - 重新加载所有配置

/es config save
  - 保存所有配置

/es config get <键>
  - 查看配置项
  - 键: backpack_limit, loot_system, death_drop,
        evacuation, shared_inventory, backpack_rows, backpack_cols

/es config set <键> <值>
  - 修改配置项
  - 示例: /es config set backpack_limit false
```

### 调试工具

```
/es debug search <坐标>
  - 查看指定位置的搜索状态

/es debug status
  - 查看系统整体状态
```

### 帮助

```
/es help
  - 显示指令帮助
```

---

## 配置文件

所有配置文件位于 `./config/extraction_shooter/` 目录：

| 文件 | 说明 |
|------|------|
| `global_settings.json` | 全局开关与默认值 |
| `qualities.json` | 品质定义 |
| `item_defs.json` | 物品占格定义 |
| `fixed_spawns.json` | 固定刷新点 |
| `temp_spawns.json` | 临时刷新点 |
| `evacuation_zones.json` | 撤离区域 |
| `playerdata/<uuid>.json` | 玩家数据（自动生成） |

---

## 开发环境

- Minecraft 版本: 1.20.1
- Forge 版本: 47.2.0+
- JDK: 17+
- 构建工具: Gradle (ForgeGradle 5.1+)

### 编译

```bash
cd extraction-shooter
gradlew build
```

编译产物位于 `build/libs/extraction-shooter-1.0.0.jar`

---

## 对外 API

其他 Mod 可通过 `ExtractionShooterAPI` 类与本 Mod 交互：

```java
// 为物品应用随机品质
ExtractionShooterAPI.applyRandomQuality(itemStack);

// 获取物品品质名称
String quality = ExtractionShooterAPI.getQualityName(itemStack);

// 获取玩家货币
int currency = ExtractionShooterAPI.getPlayerCurrency(playerUUID);

// 增加玩家货币
ExtractionShooterAPI.addPlayerCurrency(playerUUID, 10);
```

---

## 注意事项

1. 本 Mod 为**双端 Mod**，客户端和服务端都需要安装
2. 修改配置后使用 `/es config reload` 重新加载
3. 物品占格大小仅影响自定义背包，原版背包格子不受影响
4. 撤离区域使用长方体判定，建议不要设置过大范围
5. 断线续存功能需要在撤离区域内掉线才生效
