package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;

public final class BlastMessages {
    private BlastMessages() {}

    public static String get(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Blast message key cannot be empty");
        }
        return Messages.get("blast." + key, args);
    }
}
