package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ArtifactRecharge;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Daze;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Corrosion;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Frost;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Chill;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hex;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.PhysicalEmpower;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Recharging;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.WandEmpower;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Eldritch;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Shocking;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Unstable;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.DamageWand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfDisintegration;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorrosion;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfBlastWave;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;

import java.util.ArrayList;
import java.util.Comparator;

/** Non-bomb combat: estimate damage without rolling an actual attack or consuming charges. */
final class BlastAutoCombat {
    enum Action { NONE, QUEUED, STARTED }

    private BlastAutoCombat() {}

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        ArrayList<Mob> targets = enemies(hero);
        if (targets.isEmpty()) return Action.NONE;

        // Reuse equipment candidates across targets; safety and damage stay target-dependent.
        CombatEquipment equipment = new CombatEquipment(hero);

        // Preserve threat priority, but try another enemy before spending a turn moving.
        for (Mob target : targets) {
            Action action = chooseAttack(hero, target, equipment);
            if (action != Action.NONE) return action;
        }

        if (hero.rooted) return Action.NONE;
        for (Mob target : targets) {
            Action approach = approachBombRange(hero, target, safe, distances);
            if (approach != Action.NONE) return approach;

            int destination = BlastAutoPlay.nearCell(hero.pos, target.pos, safe, distances);
            if (destination >= 0 && distances[destination] > 0
                    && distances[destination] < Integer.MAX_VALUE) {
                BlastAutoPlay.rememberCombatTarget(target);
                hero.curAction = new HeroAction.Move(
                        BlastAutoPlay.firstStep(destination, safe, distances));
                return Action.QUEUED;
            }
        }
        return Action.NONE;
    }

    private static Action chooseAttack(Hero hero, Mob target, CombatEquipment equipment) {
        Choice wand = bestWand(hero, target, equipment);
        if (BlastAutoPlay.highEvasion(hero, target) && wand != null) {
            Action action = zap(hero, target, wand);
            if (action != Action.NONE) return action;
            wand = null;
        }

        Choice missile = bestMissile(hero, target, equipment);
        Choice remote = better(wand, missile);
        boolean canMelee = hero.canAttack(target) && meleeCollateralSafe(hero, target);
        float melee = canMelee ? meleeDamage(hero, target) : 0f;
        if (remote != null && preferRemote(remote.damage, melee)) {
            Action action = remote.wand != null
                    ? zap(hero, target, remote) : throwMissile(hero, target, remote);
            if (action != Action.NONE) return action;
        }
        return canMelee ? attack(hero, target) : Action.NONE;
    }

    static boolean preferRemote(float remote, float melee) {
        return remote > 0f && remote >= melee;
    }

    private static Action approachBombRange(Hero hero, Mob target,
                                            boolean[] safe, int[] distances) {
        if (hero.rooted) return Action.NONE;
        int destination = BlastAutoBombAI.bombApproachCell(
                hero, target, safe, distances);
        if (destination < 0) return Action.NONE;

        BlastAutoPlay.rememberCombatTarget(target);
        hero.curAction = new HeroAction.Move(
                BlastAutoPlay.firstStep(destination, safe, distances));
        return Action.QUEUED;
    }

    private static ArrayList<Mob> enemies(Hero hero) {
        ArrayList<Mob> targets = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0 || !hero.fieldOfView[mob.pos]
                    || !BlastAutoPlay.isAwake(mob) || BlastAutoPlay.isAutoAttackExcluded(mob)
                    || BlastAutoPlay.ignored(mob)
                    || hero.isCharmedBy(mob) || mob.isInvulnerable(Hero.class)) continue;
            targets.add(mob);
        }
        targets.sort(Comparator.comparingInt((Mob mob) -> BlastAutoPlay.combatPriority(mob,
                        !BlastAutoSacrifice.isRangedEnemy(mob.getClass())
                        && Dungeon.level.adjacent(hero.pos, mob.pos)
                        && mob.state == mob.HUNTING && mob.isTargeting(hero)))
                .thenComparingInt(mob -> Dungeon.level.distance(hero.pos, mob.pos)));
        return targets;
    }

    /** Candidate inventory is reused across targets, but scanned only when needed. */
    private static final class CombatEquipment {
        final SpiritBow bow;
        final SpiritBow.SpiritArrow arrow;
        private ArrayList<Wand> wands;
        private ArrayList<MissileWeapon> missiles;

        CombatEquipment(Hero hero) {
            bow = hero.belongings.lostInventory() ? null
                    : hero.belongings.getItem(SpiritBow.class);
            arrow = bow == null ? null : bow.knockArrow();
        }

        ArrayList<Wand> wands(Hero hero) {
            if (wands == null) {
                wands = new ArrayList<>();
                // Keep the equipped staff first, ahead of inventory wands.
                if (hero.belongings.weapon() instanceof MagesStaff) {
                    Wand staffWand = ((MagesStaff) hero.belongings.weapon()).blastAutoPlayWand();
                    if (staffWand != null) wands.add(staffWand);
                }
                if (!hero.belongings.lostInventory()) {
                    for (Item item : hero.belongings) {
                        if (item instanceof Wand) wands.add((Wand) item);
                    }
                }
            }
            return wands;
        }

        ArrayList<MissileWeapon> missiles(Hero hero) {
            if (missiles == null) {
                missiles = new ArrayList<>();
                if (!hero.belongings.lostInventory()) {
                    for (Item item : hero.belongings) {
                        if (item instanceof MissileWeapon
                                && hero.belongings.backpack.contains(item)
                                && item.quantity() > 0) {
                            missiles.add((MissileWeapon) item);
                        }
                    }
                }
            }
            return missiles;
        }
    }

    private static final class Choice {
        final MissileWeapon missile;
        final Wand wand;
        final int aim;
        final float damage;

        Choice(MissileWeapon missile, Wand wand, int aim, float damage) {
            this.missile = missile;
            this.wand = wand;
            this.aim = aim;
            this.damage = damage;
        }
    }

    private static Choice better(Choice a, Choice b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.damage >= b.damage ? a : b;
    }

    private static Choice bestMissile(Hero hero, Mob target, CombatEquipment equipment) {
        if (hero.belongings.lostInventory() || hero.pos == target.pos) return null;
        Choice best = null;
        if (equipment.arrow != null) {
            float damage = weaponCollateralSafe(hero, target, equipment.arrow, true)
                    ? physicalDamage(hero, target, equipment.arrow, equipment.bow) : 0f;
            if (damage > 0f) {
                int aim = QuickSlotButton.autoAim(target, equipment.arrow);
                if (aim >= 0) best = new Choice(equipment.arrow, null, aim, damage);
            }
        }
        for (MissileWeapon weapon : equipment.missiles(hero)) {
            if (!hero.belongings.backpack.contains(weapon) || weapon.quantity() <= 0) continue;
            float damage = weaponCollateralSafe(hero, target, weapon, true)
                    ? physicalDamage(hero, target, weapon, weapon) : 0f;
            if (damage > 0f && (best == null || damage > best.damage)) {
                int aim = QuickSlotButton.autoAim(target, weapon);
                if (aim >= 0) best = new Choice(weapon, null, aim, damage);
            }
        }
        return best;
    }

    private static Choice bestWand(Hero hero, Mob target, CombatEquipment equipment) {
        if (hero.buff(MagicImmune.class) != null || hero.pos == target.pos) return null;
        Choice best = null;
        for (Wand wand : equipment.wands(hero)) {
            best = evaluateWand(hero, target, wand, best);
        }
        return best;
    }

    private static Choice evaluateWand(Hero hero, Mob target, Wand wand, Choice best) {
        if (wand == null || !wand.blastAutoCanZap(hero)) return best;

        int aim;
        float damage;
        if (wand instanceof WandOfCorrosion) {
            if (!corrosionSafe(hero, target)) return best;
            aim = QuickSlotButton.autoAim(target, wand);
            // Conservative estimate of two damage ticks, not the whole gas lifetime.
            damage = 2f * Math.max(1, 2 + wand.buffedLvl());
        } else if (wand instanceof DamageWand && (wand instanceof WandOfMagicMissile
                || wand instanceof WandOfFrost || wand instanceof WandOfPrismaticLight
                || wand instanceof WandOfBlastWave
                || wand instanceof WandOfLightning || wand instanceof WandOfDisintegration
                || (wand instanceof WandOfTransfusion
                    && target.properties().contains(Char.Property.UNDEAD)))) {
            DamageWand direct = (DamageWand) wand;
            damage = (direct.min() + direct.max()) / 2f;
            WandEmpower empower = hero.buff(WandEmpower.class);
            if (empower != null) damage += empower.dmgBoost;

            if (wand instanceof WandOfDisintegration) {
                // A piercing beam does not stop at its victim.
                aim = target.pos;
                if (!disintegrationSafe(hero, target, wand)) return best;
            } else {
                aim = QuickSlotButton.autoAim(target, wand);
                if (aim < 0) return best;
                if (wand instanceof WandOfBlastWave
                        && !blastWaveSafe(hero, target, wand, aim)) return best;
                if (wand instanceof WandOfLightning) {
                    float multiplier = lightningMultiplier(hero, target);
                    if (multiplier <= 0f) return best;
                    damage *= multiplier;
                } else if (wand instanceof WandOfFrost) {
                    if (target.buff(Frost.class) != null) return best;
                    Chill chill = target.buff(Chill.class);
                    if (chill != null) damage *= (float) Math.pow(0.9333f,
                            Math.min(10f, chill.cooldown()));
                } else if (wand instanceof WandOfPrismaticLight
                        && (target.properties().contains(Char.Property.UNDEAD)
                            || target.properties().contains(Char.Property.DEMONIC))) {
                    damage *= 1.333f;
                }
            }
        } else {
            // In particular, do not cast Fireblast: it can burn floor scrolls.
            return best;
        }

        if (aim < 0 || aim == hero.pos || damage <= 0f) return best;
        if (best == null || damage > best.damage) return new Choice(null, wand, aim, damage);
        return best;
    }

    /** The native wave only affects the impact cell and its eight neighbours. */
    static boolean blastWaveOutsideRadius(int heroCell, int impactCell, int width) {
        if (heroCell < 0 || impactCell < 0 || width <= 0) {
            throw new IllegalArgumentException("Invalid blast-wave coordinates");
        }
        return Math.abs(heroCell % width - impactCell % width) > 1
                || Math.abs(heroCell / width - impactCell / width) > 1;
    }

    private static boolean blastWaveSafe(Hero hero, Mob target, Wand wand, int aim) {
        int width = Dungeon.level.width();
        int impact = new Ballistica(hero.pos, aim, wand.collisionProperties(aim)).collisionPos;
        if (!Dungeon.level.insideMap(impact)
                || !blastWaveOutsideRadius(hero.pos, impact, width)
                || Math.abs(target.pos % width - impact % width) > 1
                || Math.abs(target.pos / width - impact / width) > 1) return false;

        // Native Blast Wave presses every tile before pushing characters.
        // A triggered trap or displaced neutral could cause indirect harm.
        int x = impact % width;
        int y = impact / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || nx >= width || ny < 0
                        || ny >= Dungeon.level.length() / width) continue;
                int cell = nx + ny * width;
                Trap trap = Dungeon.level.traps.get(cell);
                if (trap != null && trap.active) return false;
                Char occupant = Actor.findChar(cell);
                if (occupant != null && occupant != target
                        && (occupant.alignment != Char.Alignment.ENEMY
                            || (occupant instanceof Mob
                                && BlastAutoPlay.isAutoAttackExcluded((Mob) occupant)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean disintegrationSafe(Hero hero, Mob target, Wand wand) {
        Ballistica beam = new Ballistica(hero.pos, target.pos, wand.collisionProperties(target.pos));
        int length = Math.min(beam.dist, 6 + 2 * wand.buffedLvl());
        boolean hits = false;
        for (int i = 1; i <= length; i++) {
            int cell = beam.path.get(i);
            if (cell == target.pos) hits = true;
            Char ch = Actor.findChar(cell);
            if (ch instanceof Mob && BlastAutoPlay.isAutoAttackExcluded((Mob) ch)) return false;
            if (ch != null && ch.alignment == Char.Alignment.ALLY) return false;
        }
        return hits;
    }

    // Overestimate the native arc graph by ignoring walls. This deliberately
    // rejects some safe shots but never needs a map-sized pathfinding array.
    private static float lightningMultiplier(Hero hero, Mob target) {
        if (target.buff(WandOfLightning.LightningCharge.class) != null) return 0f;
        java.util.Set<Char> chars = Actor.chars();
        ArrayList<Char> affected = new ArrayList<>();
        affected.add(target);
        int charge = hero.buff(WandOfLightning.LightningCharge.class) == null ? 0 : 1;
        for (int i = 0; i < affected.size(); i++) {
            Char source = affected.get(i);
            int radius = (Dungeon.level.water[source.pos] ? 2 : 1) + charge;
            for (Char ch : chars) {
                if (!ch.isAlive() || affected.contains(ch)) continue;
                int distance = Dungeon.level.distance(source.pos, ch.pos);
                if (ch == hero && distance > 1) continue;
                if (distance > radius) continue;
                if (ch == hero || (ch instanceof Mob && BlastAutoPlay.isAutoAttackExcluded((Mob) ch))) {
                    return 0f;
                }
                affected.add(ch);
            }
        }
        if (Dungeon.level.water[target.pos]) return 1f;
        int count = 0;
        for (Char ch : affected) {
            if (ch.alignment != hero.alignment
                    && ch.buff(WandOfLightning.LightningCharge.class) == null) count++;
        }
        return count == 0 ? 0f : 0.4f + 0.6f / count;
    }

    static boolean corrosionDistanceSafe(int distance) {
        return distance >= 4;
    }

    private static boolean corrosionSafe(Hero hero, Mob target) {
        if (!corrosionDistanceSafe(Dungeon.level.distance(hero.pos, target.pos))
                || target.isImmune(CorrosiveGas.class) || target.isImmune(Corrosion.class)
                || target.buff(Corrosion.class) != null
                || Blob.volumeAt(target.pos, CorrosiveGas.class) > 0) return false;
        if (Blob.volumeAt(hero.pos, CorrosiveGas.class) > 0) return false;
        // Gas spreads after the cast. Protect excluded targets with a wider buffer.
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && BlastAutoPlay.isAutoAttackExcluded(mob)
                    && Dungeon.level.distance(target.pos, mob.pos) <= 6) return false;
        }
        return true;
    }

    static boolean meleeCollateralSafe(Hero hero, Mob target) {
        return weaponCollateralSafe(hero, target, hero.belongings.attackingWeapon(), false);
    }

    /**
     * Predict the secondary effects of weapon enchantments before an automatic
     * hit. Native Shocking can chain through any nearby character, including
     * allies; ignore walls to safely overestimate its possible reach.
     */
    private static boolean weaponCollateralSafe(Hero hero, Mob target,
                                                KindOfWeapon source, boolean missile) {
        if (!(source instanceof Weapon)) return true;
        Weapon weapon = (Weapon) source;
        boolean shocking = weapon.hasEnchant(Shocking.class, hero);
        boolean eldritch = weapon.hasEnchant(Eldritch.class, hero);
        boolean unstable = weapon.hasEnchant(Unstable.class, hero);

        // Shared Enchantment can apply the Spirit Bow's enchantment to a thrown
        // weapon, even when that weapon has no enchantment of its own.
        if (missile && hero.pointsInTalent(Talent.SHARED_ENCHANTMENT) > 0) {
            SpiritBow bow = hero.belongings.getItem(SpiritBow.class);
            if (bow != null) {
                shocking |= bow.hasEnchant(Shocking.class, hero);
                eldritch |= bow.hasEnchant(Eldritch.class, hero);
                unstable |= bow.hasEnchant(Unstable.class, hero);
            }
        }
        if (!shocking && !eldritch && !unstable) return true;

        // Eldritch (including Unstable's random Eldritch proc) can inflict
        // Terror on shopkeepers when they can see the attacker or target.
        if (eldritch || unstable) {
            for (Mob mob : Dungeon.level.mobs) {
                if (mob.isAlive() && BlastAutoPlay.isAutoAttackExcluded(mob)
                        && mob.fieldOfView != null
                        && (mob.fieldOfView[hero.pos] || mob.fieldOfView[target.pos])) {
                    return false;
                }
            }
        }
        if (!shocking && !unstable) return true;

        // Native Shocking starts with a two-cell arc, then chains one cell
        // per hit (two on water). All intermediate characters conduct arcs.
        ArrayList<Char> reached = new ArrayList<>();
        reached.add(target);
        java.util.Set<Char> characters = Actor.chars();
        for (int i = 0; i < reached.size(); i++) {
            Char from = reached.get(i);
            int radius = i == 0 ? 2
                    : Dungeon.level.water[from.pos] && !from.flying ? 2 : 1;
            for (Char ch : characters) {
                if (!ch.isAlive() || ch == hero || reached.contains(ch)
                        || Dungeon.level.distance(from.pos, ch.pos) > radius) continue;
                if (ch instanceof Mob && BlastAutoPlay.isAutoAttackExcluded((Mob) ch)) {
                    return false;
                }
                reached.add(ch);
            }
        }
        return true;
    }

    private static float meleeDamage(Hero hero, Mob target) {
        KindOfWeapon weapon = hero.belongings.attackingWeapon();
        float damage;
        if (RingOfForce.fightingUnarmed(hero)) {
            // Ordinary unarmed damage is 1..STR-8; the force ring improves both
            // bounds through the same strength-tier approximation as SPD.
            int force = RingOfForce.armedDamageBonus(hero);
            if (hero.buff(RingOfForce.Force.class) == null) {
                damage = (1f + Math.max(1, hero.STR() - 8)) / 2f;
            } else {
                float tier = Math.max(1f, (hero.STR() - 8) / 2f);
                if (tier > 5f) tier = 5f + (tier - 5f) / 2f;
                if (force <= 0) tier = 1f;
                damage = (Math.round(tier + force)
                        + Math.round(5f * (tier + 1f) + force * (tier + 1f))) / 2f;
                RingOfForce.BrawlersStance stance = hero.buff(RingOfForce.BrawlersStance.class);
                if (stance != null && stance.active) {
                    damage += Math.round(3f + tier + force * (4f + 2f * tier) / 8f);
                }
            }
        } else if (weapon != null) {
            damage = weaponDamage(hero, weapon);
            damage += RingOfForce.armedDamageBonus(hero);
        } else {
            damage = 1f;
        }
        PhysicalEmpower empower = hero.buff(PhysicalEmpower.class);
        if (empower != null) damage += empower.dmgBoost;
        return Math.max(0f, boostedDamage(hero, damage)
                * hitChance(hero, target, weapon, false));
    }

    private static float physicalDamage(Hero hero, Mob target, MissileWeapon weapon,
                                        KindOfWeapon damageSource) {
        float damage = weaponDamage(hero, damageSource);
        PhysicalEmpower empower = hero.buff(PhysicalEmpower.class);
        if (empower != null) damage += empower.dmgBoost;
        return Math.max(0f, boostedDamage(hero, damage)
                * hitChance(hero, target, weapon, true));
    }

    private static float boostedDamage(Hero hero, float damage) {
        if (hero.heroClass != HeroClass.DUELIST
                && hero.hasTalent(Talent.WEAPON_RECHARGING)
                && (hero.buff(Recharging.class) != null
                    || hero.buff(ArtifactRecharge.class) != null)) {
            damage *= 1.025f + 0.025f * hero.pointsInTalent(Talent.WEAPON_RECHARGING);
        }
        Berserk berserk = hero.buff(Berserk.class);
        return berserk == null ? damage : berserk.damageFactor(damage);
    }

    private static float weaponDamage(Hero hero, KindOfWeapon weapon) {
        float min = weapon.min(), max = weapon.max();
        if (weapon instanceof Weapon) {
            Weapon equipped = (Weapon) weapon;
            min = equipped.augment.damageFactor(min);
            max = equipped.augment.damageFactor(max);
            if (hero.STR() > equipped.STRReq()) {
                max += hero.STR() - equipped.STRReq();
            }
        }
        return (min + max) / 2f;
    }

    private static float basicAccuracy(Hero hero, boolean thrown) {
        float accuracy = (hero.lvl + 9f) * RingOfAccuracy.accuracyMultiplier(hero);
        if (hero.buff(Bless.class) != null) accuracy *= 1.25f;
        if (hero.buff(Hex.class) != null) accuracy *= 0.8f;
        if (hero.buff(Daze.class) != null) accuracy *= 0.5f;
        if (!thrown && hero.heroClass != HeroClass.DUELIST) {
            accuracy *= 1f + 0.1f * hero.pointsInTalent(Talent.PRECISE_ASSAULT);
        }
        return accuracy;
    }

    /** Probability that two independent uniform attack/defense rolls compare favorably. */
    static float estimatedHitChance(float accuracy, float evasion) {
        if (accuracy <= 0f || evasion >= Char.INFINITE_EVASION) return 0f;
        if (evasion <= 0f || accuracy >= Char.INFINITE_ACCURACY) return 1f;
        if (accuracy < evasion) return accuracy / (2f * evasion);
        return 1f - evasion / (2f * accuracy);
    }

    static float estimatedAccuracy(Hero hero, Mob target, KindOfWeapon weapon, boolean thrown) {
        float accuracy = basicAccuracy(hero, thrown);
        if (weapon != null && (thrown || !RingOfForce.fightingUnarmed(hero))) {
            accuracy *= weapon.accuracyFactor(hero, target);
        }
        // The extra shooting talent is included in the native missile accuracy factor
        // for adjacent targets, and not applied to unrelated melee strikes.
        com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Momentum momentum =
                hero.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Momentum.class);
        if (thrown && momentum != null && momentum.freerunning()
                && hero.pointsInTalent(Talent.PROJECTILE_MOMENTUM) > 0) {
            accuracy *= 1f + hero.pointsInTalent(Talent.PROJECTILE_MOMENTUM) / 2f;
        }
        return accuracy;
    }

    private static float hitChance(Hero hero, Mob target, KindOfWeapon weapon, boolean thrown) {
        float accuracy = estimatedAccuracy(hero, target, weapon, thrown);
        float evasion = target.defenseSkill(hero);
        if (target.buff(Bless.class) != null) evasion *= 1.25f;
        if (target.buff(Hex.class) != null) evasion *= 0.8f;
        if (target.buff(Daze.class) != null) evasion *= 0.5f;
        return estimatedHitChance(accuracy, evasion);
    }

    private static Action attack(Hero hero, Mob target) {
        BlastAutoPlay.rememberCombatTarget(target);
        hero.curAction = new HeroAction.Attack(target);
        return Action.QUEUED;
    }

    private static Action zap(Hero hero, Mob target, Choice choice) {
        if (!choice.wand.blastAutoZap(hero, choice.aim)) return Action.NONE;
        BlastAutoPlay.rememberCombatTarget(target);
        return Action.STARTED;
    }

    private static Action throwMissile(Hero hero, Mob target, Choice choice) {
        MissileWeapon weapon = choice.missile;
        if (!(weapon instanceof SpiritBow.SpiritArrow)
                && !hero.belongings.backpack.contains(weapon)) return Action.NONE;
        BlastAutoPlay.rememberCombatTarget(target);
        weapon.parent = null;
        weapon.setCurrent(hero);
        weapon.cast(hero, choice.aim);
        return Action.STARTED;
    }
}
