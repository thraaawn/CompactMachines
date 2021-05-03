package com.robotgryphon.compactmachines.data.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public abstract class CodecExtensions {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING
            .comapFlatMap((s) -> {
                try {
                    return DataResult.success(UUID.fromString(s));
                } catch (Exception ex) {
                    return DataResult.error("Not a valid UUID: " + s + " (" + ex.getMessage() + ")");
                }
            }, UUID::toString).stable();

    public static final Codec<RegistryKey<World>> WORLD_REGISTRY_KEY = ResourceLocation.CODEC
            .xmap(RegistryKey.elementKey(Registry.DIMENSION_REGISTRY), RegistryKey::location);

    public static final Codec<Vector3d> VECTOR3D = DoubleStreamExtensions.CODEC
            .comapFlatMap(i -> DoubleStreamExtensions.fixedDoubleSize(i, 3)
                    .map(out -> new Vector3d(out[0], out[1], out[2])), vec -> DoubleStream.of(vec.x, vec.y, vec.z));

    public static final Codec<ChunkPos> CHUNKPOS = Codec.INT_STREAM
            .comapFlatMap(i -> Util.fixedSize(i, 2)
                    .map(arr -> new ChunkPos(arr[0], arr[1])), pos -> IntStream.of(pos.x, pos.z));
}
