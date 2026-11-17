package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ArtifactRecharge;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Berserk;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Daze;
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
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.DamageWand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

/** Non-bomb combat: estimate damage without rolling an actual attack or consuming charges. */
final class BlastAutoCombat {
    enum Action { NONE, QUEUED, STARTED }

    private BlastAutoCombat() {}

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Mob target = findEnemy(hero);
        if (target == null) return Action.NONE;

        boolean ranged = BlastAutoSacrifice.isRangedEnemy(target.getClass());
        boolean adjacent = Dungeon.level.adjacent(hero.pos, target.pos);
        boolean highEvasion = BlastAutoPlay.highEvasion(hero, target);
        Choice missile = bestMissile(hero, target);
        Choice wand = bestWand(hero, target);
        float melee = hero.canAttack(target) ? meleeDamage(hero, target) : 0f;

        // Prefer accurate magic against evasive enemies before damage ranking.
        if (highEvasion && wand != null) return zap(hero, target, wand);

        if (!ranged) {
            if (!adjacent) {
                if (missile != null) return throwMissile(hero, target, missile);
                if (wand != null) return zap(hero, target, wand);
            } else {
                // Point-blank throwing is poor unless accuracy is explicitly improved.
                if (pointBlankAccurate(hero) && missile != null
                        && missile.damage > Math.max(melee, wand == null ? 0f : wand.damage)) {
                    return throwMissile(hero, target, missile);
                }
                if (wand != null && wand.damage > melee) return zap(hero, target, wand);
            }
            return melee > 0f ? attack(hero, target) : Action.NONE;
        }

        // Against ranged enemies, attack immediately with the highest expected
        // damage available. Melee includes weapons with extended attack reach.
        Choice remote = better(wand, missile);
        if (remote != null && remote.damage >= melee) {
            return remote.missile != null
                    ? throwMissile(hero, target, remote) : zap(hero, target, remote);
        }
        if (melee > 0f) return attack(hero, target);

        // No attack can connect yet: first approach a position where the
        // selected bomb can reach this ranged target, without closing to melee.
        if (!hero.rooted) {
            int destination = BlastAutoBombAI.rangedBlastCell(
                    hero, target, safe, distances);
            if (destination < 0) {
                destination = BlastAutoPlay.nearCell(
                        hero.pos, target.pos, safe, distances);
            }
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

    private static Mob findEnemy(Hero hero) {
        Mob best = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0 || !hero.fieldOfView[mob.pos]
                    || !BlastAutoPlay.isAwake(mob) || BlastAutoPlay.isProtectedNpc(mob)
                    || hero.isCharmedBy(mob) || mob.isInvulnerable(Hero.class)) continue;
            boolean immediate = Dungeon.level.adjacent(hero.pos, mob.pos)
                    && mob.state == mob.HUNTING && mob.isTargeting(hero);
            int priority = BlastAutoPlay.combatPriority(mob, immediate);
            int distance = Dungeon.level.distance(hero.pos, mob.pos);
            if (priority < bestPriority || (priority == bestPriority && distance < bestDistance)) {
                best = mob;
                bestPriority = priority;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static final class Choice {
        final MissileWeapon missile;
        final Wand wand;
        final float damage;

        Choice(MissileWeapon missile, Wand wand, float damage) {
            this.missile = missile;
            this.wand = wand;
            this.damage = damage;
        }
    }

    private static Choice better(Choice a, Choice b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.damage >= b.damage ? a : b;
    }

    private static Choice bestMissile(Hero hero, Mob target) {
        if (hero.belongings.lostInventory() || hero.pos == target.pos
                || new Ballistica(hero.pos, target.pos, Ballistica.PROJECTILE).collisionPos != target.pos) {
            return null;
        }
        Choice best = null;
        for (Item item : hero.belongings) {
            if (!(item instanceof MissileWeapon) || !hero.belongings.backpack.contains(item)
                    || item.quantity() <= 0) continue;
            MissileWeapon weapon = (MissileWeapon) item;
            // The standard manual throw warns before destroying a valuable last throw.
            // Auto-play must not bypass that warning.
            if (item.quantity() == 1 && !weapon.extraThrownLeft
                    && ((weapon.levelKnown && weapon.level() > 0)
                        || weapon.hasGoodEnchant() || weapon.masteryPotionBonus
                        || weapon.enchantHardened)
                    && weapon.durabilityLeft() <= weapon.durabilityPerUse()) continue;
            float damage = physicalDamage(hero, target, weapon);
            if (damage > 0f && (best == null || damage > best.damage)) {
                best = new Choice(weapon, null, damage);
            }
        }
        return best;
    }

    private static Choice bestWand(Hero hero, Mob target) {
        if (hero.buff(MagicImmune.class) != null || hero.pos == target.pos) return null;
        Choice best = null;
        if (hero.belongings.weapon() instanceof MagesStaff) {
            Wand staffWand = ((MagesStaff) hero.belongings.weapon()).blastAutoPlayWand();
            best = evaluateWand(hero, target, staffWand, best);
        }
        if (!hero.belongings.lostInventory()) {
            for (Item item : hero.belongings) {
                if (item instanceof Wand) {
                    best = evaluateWand(hero, target, (Wand) item, best);
                }
            }
        }
        return best;
    }

    private static Choice evaluateWand(Hero hero, Mob target, Wand wand, Choice best) {
        if (!(wand instanceof DamageWand) || wand.cursed || wand.curCharges <= 0
                || !safeDirectWand(wand, target)
                || new Ballistica(hero.pos, target.pos, wand.collisionProperties(target.pos))
                        .collisionPos != target.pos) return best;
        DamageWand damageWand = (DamageWand) wand;
        float average = (damageWand.min() + damageWand.max()) / 2f;
        WandEmpower empower = hero.buff(WandEmpower.class);
        if (empower != null) average += empower.dmgBoost;
        if (best == null || average > best.damage) return new Choice(null, wand, average);
        return best;
    }

    private static boolean safeDirectWand(Wand wand, Mob target) {
        return wand instanceof WandOfMagicMissile
                || wand instanceof WandOfFrost
                || wand instanceof WandOfPrismaticLight
                || (wand instanceof WandOfTransfusion
                    && target.properties().contains(Char.Property.UNDEAD));
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

    private static float physicalDamage(Hero hero, Mob target, MissileWeapon weapon) {
        float damage = weaponDamage(hero, weapon);
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

    private static float basicAccuracy(Hero hero, Mob target) {
        float accuracy = (hero.lvl + 9f) * RingOfAccuracy.accuracyMultiplier(hero);
        if (hero.buff(Bless.class) != null) accuracy *= 1.25f;
        if (hero.buff(Hex.class) != null) accuracy *= 0.8f;
        if (hero.buff(Daze.class) != null) accuracy *= 0.5f;
        if (hero.heroClass != HeroClass.DUELIST) {
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

    private static float hitChance(Hero hero, Mob target, KindOfWeapon weapon, boolean thrown) {
        float accuracy = basicAccuracy(hero, target);
        if (weapon != null) accuracy *= weapon.accuracyFactor(hero, target);
        // The extra shooting talent is included in the native missile accuracy factor
        // for adjacent targets, and not applied to unrelated melee strikes.
        com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Momentum momentum =
                hero.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Momentum.class);
        if (thrown && momentum != null && momentum.freerunning()
                && hero.pointsInTalent(Talent.PROJECTILE_MOMENTUM) > 0) {
            accuracy *= 1f + hero.pointsInTalent(Talent.PROJECTILE_MOMENTUM) / 2f;
        }
        float evasion = target.defenseSkill(hero);
        if (target.buff(Bless.class) != null) evasion *= 1.25f;
        if (target.buff(Hex.class) != null) evasion *= 0.8f;
        if (target.buff(Daze.class) != null) evasion *= 0.5f;
        return estimatedHitChance(accuracy, evasion);
    }

    private static boolean pointBlankAccurate(Hero hero) {
        return hero.pointsInTalent(Talent.POINT_BLANK) > 0
                || RingOfAccuracy.accuracyMultiplier(hero) > 1f;
    }

    private static Action attack(Hero hero, Mob target) {
        BlastAutoPlay.rememberCombatTarget(target);
        hero.curAction = new HeroAction.Attack(target);
        return Action.QUEUED;
    }

    private static Action zap(Hero hero, Mob target, Choice choice) {
        if (!choice.wand.blastAutoZap(hero, target.pos)) return Action.NONE;
        BlastAutoPlay.rememberCombatTarget(target);
        return Action.STARTED;
    }

    private static Action throwMissile(Hero hero, Mob target, Choice choice) {
        MissileWeapon weapon = choice.missile;
        if (!hero.belongings.backpack.contains(weapon)
                || new Ballistica(hero.pos, target.pos, Ballistica.PROJECTILE).collisionPos != target.pos) {
            return Action.NONE;
        }
        BlastAutoPlay.rememberCombatTarget(target);
        weapon.parent = null;
        weapon.setCurrent(hero);
        weapon.cast(hero, target.pos);
        return Action.STARTED;
    }
}
