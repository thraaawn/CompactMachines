package com.robotgryphon.compactmachines.teleportation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.data.codec.CodecExtensions;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

public class DimensionalPosition implements INBTSerializable<CompoundNBT> {

    private RegistryKey<World> dimension;
    private Vector3d position;
    private Vector3d rotation;

    // Note: We'd use the actual world registry key here, but it static loads the world and does a bunch
    // of initialization, making it impossible to unit test without booting a whole server up.
    public static final Codec<DimensionalPosition> CODEC = RecordCodecBuilder.create(i -> i.group(
            CodecExtensions.WORLD_REGISTRY_KEY.fieldOf("dim").forGetter(DimensionalPosition::getDimension),
            CodecExtensions.VECTOR3D.fieldOf("pos").forGetter(DimensionalPosition::getPosition),
            CodecExtensions.VECTOR3D.optionalFieldOf("rot", Vector3d.ZERO).forGetter(DimensionalPosition::getRotation)
    ).apply(i, DimensionalPosition::new));

    private DimensionalPosition() { }

    public DimensionalPosition(RegistryKey<World> world, BlockPos positionBlock) {
        this(world, Vector3d.ZERO, Vector3d.ZERO);
        this.position = new Vector3d(positionBlock.getX(), positionBlock.getY(), positionBlock.getZ());
    }

    public DimensionalPosition(RegistryKey<World> world, Vector3d positionBlock) {
        this(world, positionBlock, Vector3d.ZERO);
        this.dimension = world;

        this.rotation = Vector3d.ZERO;
    }

    public DimensionalPosition(RegistryKey<World> dim, Vector3d pos, Vector3d rotation) {
        this.dimension = dim;
        this.position = pos;
        this.rotation = rotation;
    }

    public Optional<ServerWorld> getWorld(@Nonnull MinecraftServer server) {
        return Optional.ofNullable(server.getLevel(this.dimension));
    }

    public static DimensionalPosition fromNBT(CompoundNBT nbt) {
        DimensionalPosition dp = new DimensionalPosition();
        dp.deserializeNBT(nbt);

        return dp;
    }

    @Override
    public CompoundNBT serializeNBT() {
        DataResult<INBT> nbt = CODEC.encodeStart(NBTDynamicOps.INSTANCE, this);
        return (CompoundNBT) nbt.result().orElse(null);
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        Optional<DimensionalPosition> dimensionalPosition = CODEC
                .parse(NBTDynamicOps.INSTANCE, nbt)
                .resultOrPartial(CompactMachines.LOGGER::error);

        dimensionalPosition.ifPresent(dp -> {
            this.dimension = dp.dimension;
            this.position = dp.position;
            this.rotation = dp.rotation;
        });
    }

    public RegistryKey<World> getDimension() {
        return this.dimension;
    }

    public Vector3d getPosition() {
        return this.position;
    }

    public Vector3d getRotation() {
        return this.rotation;
    }

    public BlockPos getBlockPosition() {
        return new BlockPos(position.x, position.y, position.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionalPosition that = (DimensionalPosition) o;
        return Objects.equals(dimension, that.dimension) &&
                Objects.equals(position, that.position) &&
                Objects.equals(rotation, rotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, position, rotation);
    }

    @Override
    public String toString() {
        return "DimensionalPosition{" +
                "d=" + dimension +
                ", p=" + position +
                ", r=" + rotation +
                '}';
    }
}
