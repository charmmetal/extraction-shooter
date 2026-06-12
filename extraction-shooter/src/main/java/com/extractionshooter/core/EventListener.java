package com.extractionshooter.core;

import com.extractionshooter.data.PlayerDataManager;
import com.extractionshooter.evacuation.EvacuationManager;
import com.extractionshooter.loot.LootManager;
import com.extractionshooter.quality.QualityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

public class EventListener {
    private final PlayerDataManager playerDataManager;
    private final LootManager lootManager;
    private final EvacuationManager evacuationManager;
    private final QualityManager qualityManager;

    public EventListener(PlayerDataManager pdm, LootManager lm,
                         EvacuationManager em, QualityManager qm) {
        this.playerDataManager = pdm;
        this.lootManager = lm;
        this.evacuationManager = em;
        this.qualityManager = qm;
    }

    /**
     * 玩家死亡事件 - 将玩家背包物品转入搜刮容器
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!playerDataManager.getGlobalConfig().enableDeathDropToContainer) return;

        // 将玩家背包物品存入死亡容器
        lootManager.storePlayerDeathLoot(player);
    }

    /**
     * 玩家加入事件 - 恢复断线数据
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        playerDataManager.loadPlayer(player.getUUID());
        evacuationManager.resumePlayer(player);
    }

    /**
     * 玩家退出事件 - 保存数据
     */
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        playerDataManager.savePlayer(player.getUUID());
        evacuationManager.handleDisconnect(player);
    }

    /**
     * 世界 Tick - 撤离倒计时、刷新点刷新等
     */
    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.side != LogicalSide.SERVER) return;
        if (event.phase != TickEvent.Phase.END) return;

        // 每 tick 更新撤离状态
        evacuationManager.tick(event.level);
    }
}
