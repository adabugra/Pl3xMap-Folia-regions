package me.adabugra.pl3xmapfoliaregions.utils;

public final class CoordinateUtils {

    private CoordinateUtils() {
        throw new RuntimeException();
    }

    public static int getChunkX(final long chunkKey) {
        return (int) chunkKey;
    }

    public static int getChunkZ(final long chunkKey) {
        return (int) (chunkKey >>> 32);
    }
}