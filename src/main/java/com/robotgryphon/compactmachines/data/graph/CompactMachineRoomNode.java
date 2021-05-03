package com.robotgryphon.compactmachines.data.graph;

import net.minecraft.util.math.ChunkPos;

import java.util.Objects;

/**
 * Represents the inside of a Compact Machine.
 */
public class CompactMachineRoomNode implements IMachineGraphNode {

    private final ChunkPos pos;

    public CompactMachineRoomNode(ChunkPos pos) {
        this.pos = pos;
    }

    @Override
    public String getId() {
        return getIdFor(this.pos);
    }

    @Override
    public String label() {
        return String.format("Compact Machine {%s,%s}", pos.x, pos.z);
    }

    public static String getIdFor(ChunkPos pos) {
        long v = pos.toLong();
        return "internal_" + (v < 0 ? "N" : "") + Math.abs(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactMachineRoomNode that = (CompactMachineRoomNode) o;
        return pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos);
    }

    public ChunkPos getChunk() {
        return pos;
    }
}
