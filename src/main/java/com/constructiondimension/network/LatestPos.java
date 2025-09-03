package com.constructiondimension.network;

import com.constructiondimension.ConstructionDimension;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class LatestPos {
    private static final String LatestPosNBT = ConstructionDimension.MOD_ID+":Latest_Pos";
    private static final String LatestDimNBT = ConstructionDimension.MOD_ID+":Latest_Dim";
    private static final String LatestCDPosNBT = ConstructionDimension.MOD_ID+":Latest_Dim";

    public static void savePos(ServerPlayer player) {
        CompoundTag pos = new CompoundTag();
        pos.putDouble("x", player.getX());
        pos.putDouble("y", player.getY());
        pos.putDouble("z", player.getZ());
        player.getPersistentData().put(LatestPosNBT, pos);

        CompoundTag dim = new CompoundTag();
        dim.putString("dim", player.level().dimension().location().toString());
        player.getPersistentData().put(LatestDimNBT, dim);
    }

    public static void saveCDPos(ServerPlayer player){

        CompoundTag pos = new CompoundTag();
        pos.putDouble("x", player.getX());
        pos.putDouble("y", player.getY());
        pos.putDouble("z", player.getZ());
        player.getPersistentData().put(LatestCDPosNBT,pos);

    }

    public static Vec3 getPos(ServerPlayer player) throws RuntimeException{
        CompoundTag posTag = player.getPersistentData().getCompound(LatestPosNBT);
        if (posTag.contains("x", Tag.TAG_DOUBLE) &&
                posTag.contains("y", Tag.TAG_DOUBLE) &&
                posTag.contains("z", Tag.TAG_DOUBLE)) {

            double x = posTag.getDouble("x");
            double y = posTag.getDouble("y");
            double z = posTag.getDouble("z");

            return new Vec3(x, y, z); // 提取x、y、z坐标，按顺序存入数组
        }
        return null;

    }

    public static ResourceKey<Level> getDim(ServerPlayer player) throws RuntimeException{
        CompoundTag dimTag = player.getPersistentData().getCompound(LatestDimNBT);
        if(dimTag.contains("dim", Tag.TAG_STRING)){
            String dimId = dimTag.getString("dim");
            ResourceLocation dimLoc = ResourceLocation.parse(dimId);
            return ResourceKey.create(Registries.DIMENSION, dimLoc);
        }
        return null;

    }

    public static Vec3 getCDPos(ServerPlayer player) throws RuntimeException{
        CompoundTag CDPosTag = player.getPersistentData().getCompound(LatestCDPosNBT);
        if(CDPosTag.contains("x",Tag.TAG_DOUBLE)&&
                CDPosTag.contains("y",Tag.TAG_DOUBLE)&&
                CDPosTag.contains("z",Tag.TAG_DOUBLE)
        ){
            double x = CDPosTag.getDouble("x");
            double y = CDPosTag.getDouble("y");
            double z = CDPosTag.getDouble("z");
            return new Vec3(x, y, z);
        }
        return null;

    }
}
