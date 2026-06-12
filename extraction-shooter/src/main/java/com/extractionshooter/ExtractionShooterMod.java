package com.extractionshooter;

import com.extractionshooter.core.ConfigManager;
import com.extractionshooter.core.RegistryHandler;
import com.extractionshooter.command.ESCommand;
import com.extractionshooter.data.PlayerDataManager;
import com.extractionshooter.evacuation.EvacuationManager;
import com.extractionshooter.loot.LootManager;
import com.extractionshooter.network.NetworkHandler;
import com.extractionshooter.quality.QualityManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ExtractionShooterMod.MOD_ID)
public class ExtractionShooterMod {
    public static final String MOD_ID = "extraction_shooter";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static ExtractionShooterMod instance;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private LootManager lootManager;
    private EvacuationManager evacuationManager;
    private QualityManager qualityManager;

    public ExtractionShooterMod() {
        instance = this;

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);

        // 注册方块、物品、容器等
        RegistryHandler.register(modBus);

        // 注册事件总线
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Extraction Shooter Mod 初始化中...");

        // 初始化网络
        NetworkHandler.register();

        // 初始化配置
        configManager = new ConfigManager();
        configManager.load();

        // 初始化品质系统
        qualityManager = new QualityManager(configManager);
        qualityManager.load();

        // 初始化玩家数据
        playerDataManager = new PlayerDataManager(configManager);
        playerDataManager.loadAll();

        // 初始化战利品管理器
        lootManager = new LootManager(configManager, qualityManager);
        lootManager.load();

        // 初始化撤离管理器
        evacuationManager = new EvacuationManager(configManager, playerDataManager);
        evacuationManager.load();

        // 注册事件监听
        MinecraftForge.EVENT_BUS.register(new com.extractionshooter.core.EventListener(
                playerDataManager, lootManager, evacuationManager, qualityManager));

        LOGGER.info("Extraction Shooter Mod 初始化完成！");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        new ESCommand(configManager, playerDataManager, lootManager,
                evacuationManager, qualityManager).register(event.getDispatcher());
    }

    public static ExtractionShooterMod getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public LootManager getLootManager() { return lootManager; }
    public EvacuationManager getEvacuationManager() { return evacuationManager; }
    public QualityManager getQualityManager() { return qualityManager; }
}
