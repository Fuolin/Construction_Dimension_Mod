package com.constructiondimension.network;

import com.constructiondimension.ConstructionDimensionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record TeleportToDimensionPacket() implements CustomPacketPayload {
    private static final Logger LOGGER = LogManager.getLogger();

    // NBT存储键名
    private static final String SAVED_POSITION_KEY = "ConstructionDimSavedPos";
    private static final String SAVED_CONSTRUCTION_POS_KEY = "ConstructionDimSavedConstructionPos";

    // 使用 TYPE 替代 ID
    public static final Type<TeleportToDimensionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    ConstructionDimensionMod.MOD_ID,
                    "teleport"
            )
    );

    // 使用 StreamCodec 进行编解码
    public static final StreamCodec<FriendlyByteBuf, TeleportToDimensionPacket> STREAM_CODEC =
            StreamCodec.unit(new TeleportToDimensionPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleportToDimensionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ResourceKey<Level> constructionDim = ConstructionDimensionMod.CONSTRUCTION_DIM_KEY;
                ResourceKey<Level> currentDim = player.level().dimension();
                ServerLevel targetLevel = null;

                // 确定目标维度
                if (currentDim.equals(constructionDim)) {
                    // 保存当前建造维度位置
                    savePlayerDimensionPosition(player);

                    // 获取并验证目标维度
                    ResourceKey<Level> savedDim = getSavedDimension(player);
                    if (savedDim == null) {
                        savedDim = Level.OVERWORLD;
                    }

                    targetLevel = player.server.getLevel(savedDim);
                    if (targetLevel == null) {
                        LOGGER.error("Target dimension {} not found! Using overworld as fallback.", savedDim.location());
                        targetLevel = player.server.getLevel(Level.OVERWORLD);
                    }

                    // 执行传送（带位置恢复）
                    teleportWithSavedPosition(player, targetLevel);
                } else {
                    // 保存当前维度位置
                    savePlayerPosition(player);

                    // 获取建造维度
                    ServerLevel dimTargetLevel = player.server.getLevel(constructionDim);
                    if (dimTargetLevel == null) {
                        LOGGER.error("Construction dimension not found! Aborting teleport.");
                        return;
                    }

                    // 获取保存的建造维度位置或默认安全位置
                    double[] safePos = getSavedConstructionPosition(player, dimTargetLevel);

                    // 执行传送
                    player.teleportTo(
                            dimTargetLevel,
                            safePos[0],
                            safePos[1],
                            safePos[2],
                            player.getYRot(),
                            player.getXRot()
                    );
                }
            }
        });
    }

    /**
     * 保存玩家当前位置和维度到NBT
     */
    private static void savePlayerPosition(ServerPlayer player) {
        CompoundTag savedData = new CompoundTag();

        // 保存位置
        CompoundTag pos = new CompoundTag();
        pos.putDouble("x", player.getX());
        pos.putDouble("y", player.getY());
        pos.putDouble("z", player.getZ());
        savedData.put("position", pos);

        // 保存维度
        savedData.putString("dimension", player.level().dimension().location().toString());

        // 写入玩家NBT
        player.getPersistentData().put(SAVED_POSITION_KEY, savedData);
        LOGGER.debug("Saved position for {} in dimension {}",
                player.getScoreboardName(),
                player.level().dimension().location());
    }

    /**
     * 保存玩家在建造维度的位置
     */
    private static void savePlayerDimensionPosition(ServerPlayer player) {
        CompoundTag savedData = new CompoundTag();

        // 保存位置到"position"子标签
        CompoundTag pos = new CompoundTag();
        pos.putDouble("x", player.getX());
        pos.putDouble("y", player.getY());
        pos.putDouble("z", player.getZ());
        savedData.put("position", pos);

        player.getPersistentData().put(SAVED_CONSTRUCTION_POS_KEY, savedData);
        LOGGER.debug("Saved construction dimension position for {}", player.getScoreboardName());
    }

    /**
     * 从NBT获取保存的维度
     */
    private static ResourceKey<Level> getSavedDimension(ServerPlayer player) {
        CompoundTag savedData = player.getPersistentData().getCompound(SAVED_POSITION_KEY);
        if (savedData.contains("dimension", Tag.TAG_STRING)) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(savedData.getString("dimension"));
            return dimLoc != null ? ResourceKey.create(Registries.DIMENSION, dimLoc) : null;
        }
        return null;
    }

    /**
     * 获取建造维度安全位置（带边界检查）
     */
    private static double[] getSavedConstructionPosition(ServerPlayer player, ServerLevel targetLevel) {
        CompoundTag savedData = player.getPersistentData().getCompound(SAVED_CONSTRUCTION_POS_KEY);
        double x = 0;
        double y = 1.5;
        double z = 0;

        // 尝试从NBT读取位置
        if (savedData.contains("position", Tag.TAG_COMPOUND)) {
            CompoundTag pos = savedData.getCompound("position");
            if (pos.contains("x", Tag.TAG_DOUBLE)) x = pos.getDouble("x");
            if (pos.contains("y", Tag.TAG_DOUBLE)) y = pos.getDouble("y");
            if (pos.contains("z", Tag.TAG_DOUBLE)) z = pos.getDouble("z");
        }

        // 确保位置在世界边界内
        if (!targetLevel.getWorldBorder().isWithinBounds(x, z)) {
            LOGGER.warn("Saved position outside world border, using safe default");
            x = targetLevel.getSharedSpawnPos().getX() + 0.5;
            y = targetLevel.getSharedSpawnPos().getY();
            z = targetLevel.getSharedSpawnPos().getZ() + 0.5;
        }

        // 确保Y坐标有效
        if (y <= targetLevel.getMinBuildHeight()) {
            y = targetLevel.getSeaLevel();
        }

        return new double[]{x, y, z};
    }

    /**
     * 使用保存的位置进行传送
     */
    private static void teleportWithSavedPosition(ServerPlayer player, ServerLevel targetLevel) {
        CompoundTag savedData = player.getPersistentData().getCompound(SAVED_POSITION_KEY);
        double x = 0;
        double y = targetLevel.getSeaLevel() + 1;
        double z = 0;

        // 尝试读取位置
        if (savedData.contains("position", Tag.TAG_COMPOUND)) {
            CompoundTag pos = savedData.getCompound("position");
            if (pos.contains("x", Tag.TAG_DOUBLE)) x = pos.getDouble("x");
            if (pos.contains("y", Tag.TAG_DOUBLE)) y = pos.getDouble("y");
            if (pos.contains("z", Tag.TAG_DOUBLE)) z = pos.getDouble("z");
            LOGGER.debug("Restoring saved position for {}", player.getScoreboardName());
        } else {
            LOGGER.warn("No saved position found for {}, using safe default", player.getScoreboardName());
        }

        // 确保位置在世界边界内
        if (!targetLevel.getWorldBorder().isWithinBounds(x, z)) {
            LOGGER.warn("Position outside world border, teleporting to spawn");
            x = targetLevel.getSharedSpawnPos().getX() + 0.5;
            y = targetLevel.getSharedSpawnPos().getY();
            z = targetLevel.getSharedSpawnPos().getZ() + 0.5;
        }

        // 确保Y坐标有效
        if (y <= targetLevel.getMinBuildHeight()) {
            y = targetLevel.getSeaLevel();
        }

        // 执行传送
        player.teleportTo(
                targetLevel,
                x, y, z,
                player.getYRot(),
                player.getXRot()
        );

        // 清理保存的数据
        player.getPersistentData().remove(SAVED_POSITION_KEY);
        LOGGER.info("Teleported {} back to dimension {}",
                player.getScoreboardName(),
                targetLevel.dimension().location());
    }
}