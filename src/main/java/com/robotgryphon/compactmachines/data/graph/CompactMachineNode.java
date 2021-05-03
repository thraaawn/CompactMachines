package com.robotgryphon.compactmachines.data.graph;

import com.google.common.graph.MutableGraph;

import java.util.Objects;

/**
 * Represents a machine's external point. This can be either inside a machine or in a dimension somewhere.
 */
public class CompactMachineNode implements IMachineGraphNode {
    private final int machineId;

    public CompactMachineNode(int machine) {
        this.machineId = machine;
    }

    @Override
    public String label() {
        return "Compact Machine #" + machineId;
    }

    @Override
    public String getId() {
        return "machine" + machineId;
    }

    public int getMachineId() { return machineId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactMachineNode that = (CompactMachineNode) o;
        return machineId == that.machineId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineId);
    }
}
