package com.robotgryphon.compactmachines.reference;

import com.mojang.serialization.Codec;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public enum EnumMachineSize implements IStringSerializable {
    TINY    ("tiny", 3),
    SMALL   ("small", 5),
    NORMAL  ("normal", 7),
    LARGE   ("large", 9),
    GIANT   ("giant", 11),
    MAXIMUM ("maximum", 13);

    private String name;
    private int internalSize;

    public static final Codec<EnumMachineSize> CODEC = IStringSerializable.fromEnum(
            EnumMachineSize::values, EnumMachineSize::getFromSize);

    EnumMachineSize(String name, int internalSize) {
        this.name = name;
        this.internalSize = internalSize;
    }

    public static EnumMachineSize maximum() {
        return MAXIMUM;
    }

    public String getName() {
        return this.name;
    }

    public AxisAlignedBB getBounds(BlockPos center) {
        AxisAlignedBB bounds = new AxisAlignedBB(center);
        return bounds.inflate(Math.floorDiv(internalSize, 2));
    }

    public int getInternalSize() {
        return this.internalSize;
    }

    public static EnumMachineSize getFromSize(String size) {
        switch (size.toLowerCase()) {
            case "tiny": return TINY;
            case "small": return SMALL;
            case "normal": return NORMAL;
            case "large": return LARGE;
            case "giant": return GIANT;
            case "maximum": return MAXIMUM;
        }

        return TINY;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
