package com.constructiondimension.client;

import com.constructiondimension.ConstructionDimension;
import com.constructiondimension.network.TeleportEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ConstructionDimension.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // 确保玩家存在且不在GUI界面中
        if (player == null || minecraft.screen != null) return;

        // 检查按键是否被按下（并消耗按键状态）
        if (KeyBindings.TELEPORT_KEY.consumeClick()) {
            // 发送传送数据包到服务器
            PacketDistributor.sendToServer(new TeleportEvent());

            // 给玩家视觉反馈
            player.displayClientMessage(
                    Component.translatable("message.constructiondimension.teleporting"),
                    true
            );
        }
    }

}
