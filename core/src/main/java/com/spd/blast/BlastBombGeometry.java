package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

/** Physical explosion propagation, shared by damage, warnings, and terrain effects. */
public final class BlastBombGeometry {
    private static final int MAX_RADIUS = 20;

    private BlastBombGeometry() {}

    /**
     * Trace rays between tile centers. Solid endpoints can be hit, but block cells
     * behind them. One solid tile may be grazed at a corner; two solid tiles
     * touching at a corner block diagonal propagation between them.
     */
    public static boolean[] reached(Level level, int center, int radius) {
        int length = level.length();
        if (center < 0 || center >= length || radius < 0) {
            throw new IllegalArgumentException("Invalid blast projection parameters");
        }
        int width = level.width();
        int height = level.height();
        int reach = Math.min(radius, MAX_RADIUS);
        int diameter = 2 * reach + 1;
        int limitSquared = diameter * diameter;
        int sourceX = center % width;
        int sourceY = center / width;
        boolean[] cells = new boolean[length];
        cells[center] = true;

        for (int offsetY = -reach; offsetY <= reach; offsetY++) {
            int targetY = sourceY + offsetY;
            if (targetY < 0 || targetY >= height) continue;
            for (int offsetX = -reach; offsetX <= reach; offsetX++) {
                int targetX = sourceX + offsetX;
                if (targetX < 0 || targetX >= width
                        || 4 * (offsetX * offsetX + offsetY * offsetY) > limitSquared) continue;
                if (clearRay(level.solid, width, sourceX, sourceY, targetX, targetY)) {
                    cells[targetY * width + targetX] = true;
                }
            }
        }
        return cells;
    }

    /** Exact integer grid traversal handles diagonal corners without rounding error. */
    private static boolean clearRay(boolean[] solid, int width, int sourceX, int sourceY,
                                    int targetX, int targetY) {
        int deltaX = targetX - sourceX;
        int deltaY = targetY - sourceY;
        int stepX = Integer.compare(deltaX, 0);
        int stepY = Integer.compare(deltaY, 0);
        int distanceX = Math.abs(deltaX);
        int distanceY = Math.abs(deltaY);
        int x = sourceX;
        int y = sourceY;
        int crossedX = 0;
        int crossedY = 0;

        while (x != targetX || y != targetY) {
            int nextX = (2 * crossedX + 1) * distanceY;
            int nextY = (2 * crossedY + 1) * distanceX;
            if (nextX < nextY) {
                x += stepX;
                crossedX++;
            } else if (nextY < nextX) {
                y += stepY;
                crossedY++;
            } else {
                // Exactly crosses a corner: require both flanks to be solid.
                if (solid[y * width + x + stepX] && solid[(y + stepY) * width + x]) {
                    return false;
                }
                x += stepX;
                y += stepY;
                crossedX++;
                crossedY++;
            }
            // An obstacle at the destination can be hit, but cannot be crossed.
            if ((x != targetX || y != targetY) && solid[y * width + x]) return false;
        }
        return true;
    }
}
