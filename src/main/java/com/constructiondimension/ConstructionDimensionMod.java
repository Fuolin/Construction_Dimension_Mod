package com.constructiondimension;

import net.minecraft.core.registries.Registries;
import com.constructiondimension.network.TeleportToDimensionPacket;
import com.constructiondimension.client.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mod(ConstructionDimensionMod.MOD_ID)
public class ConstructionDimensionMod {
    public static final String MOD_ID = "constructiondimension";

    // 维度资源键
    public static final ResourceKey<Level> CONSTRUCTION_DIM_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse(MOD_ID + ":construction_dim")
    );

    public ConstructionDimensionMod(IEventBus modEventBus) {
        // 不需要任何额外初始化，维度由JSON注册
        LOGGER.info("{} 已加载！", MOD_ID);

        // 注册按键绑定
        modEventBus.addListener(KeyBindings::onRegisterKeyBindings);

        // 注册数据包处理器
        modEventBus.addListener(this::registerPacketHandlers);
    }

    private void registerPacketHandlers(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID)
                .versioned("1.0.1");

        //注册游戏数据包
        registrar.playToServer(
                TeleportToDimensionPacket.TYPE, // 使用 TYPE 对象
                TeleportToDimensionPacket.STREAM_CODEC,
                TeleportToDimensionPacket::handle
        );
    }

}