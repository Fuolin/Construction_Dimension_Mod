package com.constructiondimension.network;

import com.constructiondimension.ConstructionDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.constructiondimension.ConstructionDimension.CONSTRUCTION_DIM_KEY;


public record TeleportEvent() implements CustomPacketPayload{
    private static final Logger LOGGER = LogManager.getLogger();//logger

    //TYPE
    public static final Type<TeleportEvent> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    ConstructionDimension.MOD_ID,
                    "teleport"
            )
    );
    //StreamCodec
    public static final StreamCodec<FriendlyByteBuf, TeleportEvent> STREAM_CODEC =
            StreamCodec.unit(new TeleportEvent());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    //执行传送操作
    public void handle(IPayloadContext context){
        context.enqueueWork(() ->{
            //确保是服务端玩家实体
            if (!(context.player() instanceof ServerPlayer player))return;
            teleportSafely(player);
        });
    }

    private void teleportSafely(ServerPlayer player) {
        try {
            if(player.level().dimension().equals(CONSTRUCTION_DIM_KEY)){//出建造维度
                ResourceKey<Level> toDim = LatestPos.getDim(player);
                Vec3 toPos = LatestPos.getPos(player);
                if (toDim == null || toPos == null)//找不到目标维度或者目标位置
                    teleportToRespawnPos(player);
                else{//有目标维度和目标位置
                    ServerLevel toDimLevel = player.server.getLevel(toDim);
                    if(toDimLevel == null){
                        LOGGER.error("Target Level is null");
                        return;
                    }
                    Vec3 safeToPos = safePos(toPos,toDimLevel);
                    player.teleportTo(toDimLevel,safeToPos.x,safeToPos.y,safeToPos.z,player.getYRot(), player.getXRot());
                }
            }else {//进建造维度
                ServerLevel CDLevel =  player.server.getLevel(CONSTRUCTION_DIM_KEY);

                Vec3 toCDPos = LatestPos.getCDPos(player);

                //获取安全位置
                if(toCDPos == null) toCDPos=new Vec3(0,1,0);
                if(CDLevel == null){
                    LOGGER.error("{}Level is null", CONSTRUCTION_DIM_KEY);
                    return;
                }
                Vec3 safeCDPos = safePos(toCDPos,CDLevel);
                //执行传送
                player.teleportTo(CDLevel,safeCDPos.x,safeCDPos.y,safeCDPos.z,player.getYRot(), player.getXRot());

            }
        } catch (RuntimeException e) {
            LOGGER.error(e);
        }
    }

    private void teleportToRespawnPos(ServerPlayer player) throws RuntimeException {
        //优先先用个人重生点
        if (player.getRespawnPosition() != null) {
            ResourceKey<Level> RespawnDim = player.getRespawnDimension();
            Vec3 RespawnPos = player.getRespawnPosition().getCenter();
            player.teleportTo(
                    Objects.requireNonNull(player.server.getLevel(RespawnDim)),
                    RespawnPos.x,
                    RespawnPos.y,
                    RespawnPos.z,
                    player.getYRot(),
                    player.getXRot()
            );
            return;
        }
        //其次用主世界默认重生点
        Level overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld instanceof ServerLevel serverOverworld) {
            Vec3 SharedSpawnPos = overworld.getSharedSpawnPos().getCenter();
            player.teleportTo(
                    serverOverworld,
                    SharedSpawnPos.x,
                    SharedSpawnPos.y,
                    SharedSpawnPos.z,
                    player.getYRot(),
                    player.getXRot()
            );
            return;
        }
        LOGGER.error("overworld not found");
    }


    private Vec3 safePos(Vec3 pos, Level level) {
        //如果原位置安全就返回原位置
        if(isSafe(pos.x,pos.y,pos.z,level)) return pos;

        int sourceX = (int) pos.x;
        int sourceY = (int) pos.y;
        int sourceZ = (int) pos.z;

        // 获取世界高度范围
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        // 检查Y坐标是否在有效范围内
        if (sourceY < minY || sourceY > maxY) {
            // 如果初始Y坐标无效，从最近的有效Y坐标开始搜索
            sourceY = Math.max(minY, Math.min(sourceY, maxY));
        }

        // 队列存储待检查的坐标偏移量，实现BFS
        Queue<CoordinateOffset> queue = new LinkedList<>();
        // 集合记录已检查的坐标，避免重复检查
        Set<Long> checked = new HashSet<>();

        // 初始位置入队（自身位置）
        queue.add(new CoordinateOffset(0, 0, 0));
        checked.add(getCoordinateKey(sourceX, sourceY, sourceZ));

        // 定义搜索方向：6个基本方向（前后左右上下）
        int[][] directions = {
                {0, 0, 1},  // 前
                {0, 0, -1}, // 后
                {1, 0, 0},  // 右
                {-1, 0, 0}, // 左
                {0, 1, 0},  // 上
                {0, -1, 0}  // 下
        };
        int maxQueueSize = 2000;
        // BFS搜索
        while (!queue.isEmpty()) {
            CoordinateOffset offset = queue.poll();
            int currentX = sourceX + offset.x;
            int currentY = sourceY + offset.y;
            int currentZ = sourceZ + offset.z;

            // 检查当前位置是否安全
            if (isSafe(currentX, currentY, currentZ, level))
                return centerPos(currentX, currentY, currentZ);

            if (queue.size()>maxQueueSize)break;//防止卡死

            // 生成下一层搜索位置（距离+1）
            for (int[] dir : directions) {
                int newX = offset.x + dir[0];
                int newY = offset.y + dir[1];
                int newZ = offset.z + dir[2];

                int checkX = sourceX + newX;
                int checkY = sourceY + newY;
                int checkZ = sourceZ + newZ;

                // 检查Y坐标是否在有效范围内
                if (checkY < minY || checkY > maxY) continue;

                // 生成唯一键用于判断是否已检查
                long key = getCoordinateKey(checkX, checkY, checkZ);
                if (!checked.contains(key)) {
                    checked.add(key);
                    queue.add(new CoordinateOffset(newX, newY, newZ));
                }
            }
        }

        // 如果找不到安全位置，返回原位置
        return centerPos(sourceX, sourceY, sourceZ);
    }



    // 辅助类：存储坐标偏移量
    private static class CoordinateOffset {
        int x, y, z;

        CoordinateOffset(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // 生成坐标的唯一键，用于去重
    private long getCoordinateKey(int x, int y, int z) {
        // 使用位运算组合坐标为一个长整数（假设坐标值在合理范围内）
        return ((long) (x & 0xFFFF) << 48) |
                ((long) (y & 0xFFFF) << 32) |
                ((long) (z & 0xFFFF) << 16);
    }

    // 计算方块中心坐标
    private Vec3 centerPos(int x, int y, int z) {
        return new Vec3(x + 0.5, y + 0.5, z + 0.5);
    }

    // 核心安全判断
    private boolean isSafe(int x,int y, int z, Level level) {

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        // 1. 世界高度范围检查
        if (y < minY || y > maxY) return false;

        // 2. 脚下有方块（非空气）
        BlockPos feetPos = new BlockPos(x, y - 1, z);
        if (level.getBlockState(feetPos).isAir()) return false;

        // 3. 身体和头部无完全实心方块
        BlockPos bodyPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        BlockState bodyBlock = level.getBlockState(bodyPos);
        BlockState headBlock = level.getBlockState(headPos);

        return !bodyBlock.isCollisionShapeFullBlock(level, bodyPos) && !headBlock.isCollisionShapeFullBlock(level, headPos);
    }

    private boolean isSafe(double x, double y, double z, Level level) {
        //计算水平范围（X/Z）
        double xMin = x - 0.3;
        double xMax = x + 0.3;
        double zMin = z - 0.3;
        double zMax = z + 0.3;

        //计算X/Z方块坐标（去重，避免重复）
        int xBlockMin = (int) Math.floor(xMin);
        int xBlockMax = (int) Math.floor(xMax);
        int zBlockMin = (int) Math.floor(zMin);
        int zBlockMax = (int) Math.floor(zMax);

        //计算Y方块坐标
        int yBlock = ((int) Math.floor(y - 1e-9)) + 1;

        for (int CX = xBlockMin; CX <= xBlockMax; CX++) {
            for (int CZ = zBlockMin; CZ <= zBlockMax; CZ++) {
                if(isSafe(CX,yBlock,CZ,level))return true;
            }
        }

        return false;
    }
}

