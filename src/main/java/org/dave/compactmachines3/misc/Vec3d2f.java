package org.dave.compactmachines3.misc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

public class Vec3d2f {
    public final Vec3d position;
    public final float yaw;
    public final float pitch;

    public Vec3d2f(EntityPlayer player) {
        this(player.getPositionVector(), player);
    }

    public Vec3d2f(Vec3d position, EntityPlayer player) {
        this(position, player.rotationYawHead, player.rotationPitch);
    }

    public Vec3d2f(Vec3d position, float yaw, float pitch) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Vec3d getPosition() {
        return position;
    }

    public double getX() {
        return position.x;
    }

    public double getY() {
        return position.y;
    }

    public double getZ() {
        return position.z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPlayerLocation(EntityPlayerMP player) {
        player.connection.setPlayerLocation(position.x, position.y, position.z, yaw, pitch);
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setTag("position", getVec3dTag(position));
        tag.setFloat("yaw", yaw);
        tag.setFloat("pitch", pitch);

        return tag;
    }

    public static Vec3d2f fromTag(NBTTagCompound tag) {
        if (tag.isEmpty())
            return null;

        return new Vec3d2f(getPosition(tag.getCompoundTag("position")), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    private static NBTTagCompound getVec3dTag(Vec3d position) {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setDouble("x", position.x);
        tag.setDouble("y", position.y);
        tag.setDouble("z", position.z);

        return tag;
    }

    private static Vec3d getPosition(NBTTagCompound tag) {
        return new Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}
