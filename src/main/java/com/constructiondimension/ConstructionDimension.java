package com.constructiondimension;

import com.constructiondimension.client.KeyBindings;
import com.constructiondimension.network.LatestPos;
import com.constructiondimension.network.TeleportEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(ConstructionDimension.MOD_ID)
public class ConstructionDimension {
    public static final String MOD_ID = "constructiondimension";
    private static final Logger LOGGER = LogManager.getLogger();//logger

    // 维度资源键
    public static final ResourceKey<Level> CONSTRUCTION_DIM_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse(MOD_ID + ":construction_dim")
    );

    public ConstructionDimension(IEventBus modEventBus) {
        // 注册按键绑定
        modEventBus.addListener(KeyBindings::onRegisterKeyBindings);

        // 注册数据包处理器
        modEventBus.addListener(this::registerPacketHandlers);

        //监听传送事件
        NeoForge.EVENT_BUS.addListener(this::handleDimensionChangeFly);
        NeoForge.EVENT_BUS.addListener(this::handleDeathCancelFly);
        NeoForge.EVENT_BUS.addListener(this::handleDimensionChangeSave);
    }


    private void registerPacketHandlers(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);

        //注册游戏数据包
        registrar.playToServer(
                TeleportEvent.TYPE, // 使用 TYPE 对象
                TeleportEvent.STREAM_CODEC,
                TeleportEvent::handle
        );
    }

    // 维度传送事件监听
    @SubscribeEvent
    public void handleDimensionChangeFly(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!( event.getEntity() instanceof ServerPlayer player)) return;//服务端玩家实体

        boolean fromCD = event.getFrom().equals(CONSTRUCTION_DIM_KEY);
        boolean toCD = event.getTo().equals(CONSTRUCTION_DIM_KEY);
        double fly;
        if (toCD && !fromCD)// 进入,开启飞行
            fly = 1.0;
        else if(!toCD && fromCD)//离开,关闭飞行
            fly = 0.0;
        else return;
        AttributeInstance flightAttrib = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flightAttrib != null) {
            flightAttrib.setBaseValue(fly);
        } else {
            LOGGER.error("flightAttrib is null");
        }

    }
    //死亡事件监听
    @SubscribeEvent
    private void handleDeathCancelFly(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AttributeInstance flightAttrib = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flightAttrib != null) {
            flightAttrib.setBaseValue(0.0);
            return;
        }
        LOGGER.error("flightAttrib is null when death");
    }

    //维度传送前监听
    @SubscribeEvent
    public void handleDimensionChangeSave(EntityTravelToDimensionEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;//服务端玩家实体

        boolean toCD = event.getDimension().equals(CONSTRUCTION_DIM_KEY);
        boolean fromCD = player.level().dimension().equals(CONSTRUCTION_DIM_KEY);

        if (toCD && !fromCD)// 进入,保存位置
            LatestPos.savePos(player);
        if (!toCD && fromCD) {// 离开,保存位置
            LatestPos.saveCDPos(player);
        }

    }

}