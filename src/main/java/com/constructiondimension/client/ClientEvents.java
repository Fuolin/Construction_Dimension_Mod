package com.constructiondimension.client;

import com.constructiondimension.ConstructionDimensionMod;
import com.constructiondimension.network.TeleportToDimensionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ConstructionDimensionMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    // 冷却时间设置（2秒）
    private static final long COOLDOWN_MS = 2000;
    private static long lastPressTime = 0;


    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // 确保玩家存在且不在GUI界面中
        if (player == null || minecraft.screen != null) return;

        // 检查按键是否被按下（并消耗按键状态）
        if (KeyBindings.TELEPORT_KEY.consumeClick()) {
            handleTeleportKeyPress(player);
        }
    }

    private static void handleTeleportKeyPress(LocalPlayer player) {
        // 检查冷却时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPressTime < COOLDOWN_MS) {
            double remaining = (COOLDOWN_MS - (currentTime - lastPressTime)) / 1000.0;
            player.displayClientMessage(
                    Component.translatable("message.constructiondimension.cooldown", String.format("%.1f", remaining)),
                    true
            );
            return;
        }

        // 更新最后按键时间
        lastPressTime = currentTime;

        // 发送传送数据包到服务器
        PacketDistributor.sendToServer(new TeleportToDimensionPacket());

        // 给玩家视觉反馈
        player.displayClientMessage(
                Component.translatable("message.constructiondimension.teleporting"),
                true
        );
    }
}