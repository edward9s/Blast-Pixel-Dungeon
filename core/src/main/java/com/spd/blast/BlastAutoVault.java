package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;

/** One-turn vault machinery forecast; never assumes test invulnerability protects the hero. */
final class BlastAutoVault {
    private BlastAutoVault() {}

    static boolean[] imminent(Level level) {
        boolean[] danger = new boolean[level.length()];
        if (!(level instanceof VaultLevel)) return danger;

        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames != null) {
            if (flames.cur == null || flames.cur.length != danger.length
                    || (flames.afterTriggerCooldowns != null
                    && (flames.afterTriggerCooldowns.length != danger.length
                    || flames.curCooldowns == null
                    || flames.curCooldowns.length != danger.length)))
                throw new IllegalStateException("Invalid vault flame grid");
            for (int cell = 0; cell < danger.length; cell++) {
                if (flames.cur[cell] > 1) danger[cell] = true;
                if (flames.afterTriggerCooldowns != null
                        && flames.afterTriggerCooldowns[cell] >= 0
                        && flames.curCooldowns[cell] <= 1) danger[cell] = true;
            }
        }

        for (Mob mob : level.mobs) {
            if (!mob.isAlive()) continue;
            if (mob instanceof VaultLaser) {
                VaultLaser laser = (VaultLaser) mob;
                if (laser.curCooldown > 2) continue;
                if (laser.laserDirs == null || laser.laserDirs.length == 0)
                    throw new IllegalStateException("Vault laser has no directions");
                Ballistica beam = new Ballistica(laser.pos,
                        laser.laserDirs[laser.laserDirIdx], Ballistica.STOP_SOLID);
                for (int cell : beam.subPath(1, beam.dist)) danger[cell] = true;
            } else if (mob instanceof VaultSentry) {
                VaultSentry sentry = (VaultSentry) mob;
                if (sentry.curCooldown > 2) continue;
                if (sentry.scanDirs == null || sentry.scanDirs.length == 0)
                    throw new IllegalStateException("Vault sentry has no scan directions");
                for (int direction : sentry.scanDirs[sentry.scanDirIdx]) {
                    Ballistica aim = new Ballistica(sentry.pos, direction, Ballistica.WONT_STOP);
                    ConeAOE scan = new ConeAOE(aim, sentry.scanLength, sentry.scanWidth,
                            Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
                    if (scan.cells.isEmpty() && aim.path.size() >= 2)
                        scan.cells.add(aim.path.get(1));
                    for (int cell : scan.cells) {
                        // When the sentry FOV has not been initialized, conservatively block the cone.
                        danger[cell] = true;
                    }
                }
            }
        }
        return danger;
    }
}
