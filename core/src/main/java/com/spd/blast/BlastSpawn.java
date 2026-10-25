package com.spd.blast;

/** Scales vanilla natural respawning without changing mob types or placement. */
public final class BlastSpawn {
    private BlastSpawn() {}

    public static int mobLimit(int original) {
        if (original < 0) {
            throw new IllegalArgumentException("Negative vanilla mob limit: " + original);
        }
        int multiplier = BlastSettings.spawnMultiplier();
        if (original > Integer.MAX_VALUE / multiplier) {
            throw new IllegalArgumentException("Natural mob limit overflow: " + original);
        }
        return original * multiplier;
    }

    public static float cooldown(float original) {
        if (original < 0f || Float.isNaN(original) || Float.isInfinite(original)) {
            throw new IllegalArgumentException("Invalid vanilla spawn cooldown: " + original);
        }
        return original / BlastSettings.spawnMultiplier();
    }
}
