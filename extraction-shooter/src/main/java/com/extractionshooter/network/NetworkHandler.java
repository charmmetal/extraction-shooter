package com.extractionshooter.network;

import com.extractionshooter.ExtractionShooterMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通信处理器 - 管理客户端与服务端之间的数据包通信
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1.0";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExtractionShooterMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        // 注册数据包
        // 例如：搜索进度同步包、撤离状态同步包、GUI 操作包等

        // CHANNEL.registerMessage(packetId++,
        //         SearchProgressPacket.class,
        //         SearchProgressPacket::encode,
        //         SearchProgressPacket::decode,
        //         SearchProgressPacket::handle);

        // CHANNEL.registerMessage(packetId++,
        //         EvacuationStatusPacket.class,
        //         EvacuationStatusPacket::encode,
        //         EvacuationStatusPacket::decode,
        //         EvacuationStatusPacket::handle);

        ExtractionShooterMod.LOGGER.info("网络处理器初始化完成");
    }
}
