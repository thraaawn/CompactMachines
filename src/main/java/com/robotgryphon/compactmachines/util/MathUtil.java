package com.robotgryphon.compactmachines.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3i;

public class MathUtil {
    public static Vector3i getRegionPositionByIndex(int i) {
        int index = i + 1;
        // wth
        int s = (int) Math.ceil(Math.sqrt(index)) + (int) ((Math.ceil(Math.sqrt(index)) % 2 + 1) % 2);
        int ringIndex = 0;
        int p = 1;
        if (s > 1) {
            ringIndex = i - (s - 2) * (s - 2);
            p = s * s - (s - 2) * (s - 2);
        }

        int ri = (ringIndex + (int) (s / 2)) % p;

        int x = 0;
        if (s > 1) {
            if (ri < (p / 4)) x = ri;
            else {
                if (ri <= (p / 4 * 2 - 1)) x = p / 4;
                else {
                    if (ri <= (p / 4 * 3)) x = (p / 4 * 3) - ri;
                    else x = 0;
                }
            }
        }

        int y = 0;
        if (s > 1)
            y = ri < (p / 4) ? 0 :
                    (ri <= (p / 4 * 2 - 1) ? (ri - (p / 4)) :
                            (ri <= (p / 4 * 3) ? (p / 4) :
                                    (p - ri)));

        x -= (int) (s / 2);
        y -= (int) (s / 2);

        return new Vector3i(x, 0, y);
    }

    public static BlockPos getCenterWithY(ChunkPos chunk, int y) {
        return chunk.getWorldPosition()
                .offset(new BlockPos(8, y, 8));
    }

    public static BlockPos getCenterWithY(Vector3i regionIndex, int y) {
        ChunkPos chunk = new ChunkPos(
                regionIndex.getX() * 64,
                regionIndex.getZ() * 64);

        return getCenterWithY(chunk, y);
    }

    public static ChunkPos getChunkForRoomIndex(int roomIndex) {
        Vector3i region = getRegionPositionByIndex(roomIndex);
        BlockPos roomCenter = getCenterWithY(region, 0);
        return new ChunkPos(roomCenter);
    }
}
