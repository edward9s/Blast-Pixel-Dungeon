package com.spd.blast;

import com.watabou.utils.GameSettings;

/** Persistent Blast settings. Invalid stored values fail immediately. */
public final class BlastSettings {
    /** Controls the Blast tab in both the title-screen and in-game settings. */
    public static final boolean SHOW_BLAST_TAB = true;

    /** Show the original Shattered title/victory support buttons and first Worn Key prompt. */
    public static final boolean SHOW_SHATTERED_SUPPORT = false;

    public static final int MIN_SPAWN_MULTIPLIER = 1;
    public static final int MAX_SPAWN_MULTIPLIER = 10;
    public static final int DEFAULT_SPAWN_MULTIPLIER = 1;

    private static final String SPAWN_MULTIPLIER = "blast_spawn_multiplier";
    private static final String DEBUG_LOG = "blast_debug_log";
    private static final String INFINITE_BOMB = "blast_test_infinite_bomb";
    private static final String SHOW_TESTS = "blast_show_tests";
    private static final String INDESTRUCTIBLE = "blast_test_indestructible";
    private static final String FREE_MOVEMENT = "blast_test_free_movement";
    private static final String REMOTE_THROW = "blast_test_remote_throw";

    private BlastSettings() {}

    public static int spawnMultiplier() {
        int value = GameSettings.getInt(SPAWN_MULTIPLIER, DEFAULT_SPAWN_MULTIPLIER);
        validateMultiplier(value);
        return value;
    }

    public static void setSpawnMultiplier(int value) {
        validateMultiplier(value);
        GameSettings.put(SPAWN_MULTIPLIER, value);
    }

    public static boolean debugLog() {
        return GameSettings.getBoolean(DEBUG_LOG, false);
    }

    public static void setDebugLog(boolean value) {
        GameSettings.put(DEBUG_LOG, value);
    }

    public static boolean testInfiniteBomb() {
        return GameSettings.getBoolean(INFINITE_BOMB, false);
    }

    public static void setTestInfiniteBomb(boolean value) {
        GameSettings.put(INFINITE_BOMB, value);
    }

    public static boolean showTests() {
        return GameSettings.getBoolean(SHOW_TESTS, false);
    }

    public static void toggleTests() {
        GameSettings.put(SHOW_TESTS, !showTests());
    }

    public static boolean testIndestructible() {
        return GameSettings.getBoolean(INDESTRUCTIBLE, false);
    }

    public static void setTestIndestructible(boolean value) {
        GameSettings.put(INDESTRUCTIBLE, value);
    }

    public static boolean testFreeMovement() {
        return GameSettings.getBoolean(FREE_MOVEMENT, false);
    }

    public static void setTestFreeMovement(boolean value) {
        GameSettings.put(FREE_MOVEMENT, value);
    }

    public static boolean testRemoteThrow() {
        return GameSettings.getBoolean(REMOTE_THROW, false);
    }

    public static void setTestRemoteThrow(boolean value) {
        GameSettings.put(REMOTE_THROW, value);
    }

    private static void validateMultiplier(int value) {
        if (value < MIN_SPAWN_MULTIPLIER || value > MAX_SPAWN_MULTIPLIER) {
            throw new IllegalArgumentException("Blast spawn multiplier must be 1..10: " + value);
        }
    }
}
