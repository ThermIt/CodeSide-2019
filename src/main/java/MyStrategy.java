import model.*;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

import java.util.*;

public class MyStrategy implements Strategy {

    public static final double HEALTH_TO_LOOK_FOR_HEAL = 0.90;
    private static final double EPSILON = 0.000000001;
    private static final float OLD_DEBUG_TRANSPARENCY = 0.2f;

    private static LootBox willTakeThis;

    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;
    private Debug debug;
    private double updatesPerSecond;
    private double halfUnitSizeX;
    private Tile[][] tiles;
    private Player me;
    private Player enemy;


    static double distanceSqr(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }


    double[][] getField(Vec2Double runningPos) {
        int myX = (int) unit.getPosition().getX();
        int myY = (int) unit.getPosition().getY();

        int sizeX = tiles.length;
        int sizeY = tiles[0].length;
        double[][] field = new double[sizeX][sizeY];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                if (y == sizeY - 1) {
                    field[x][y] = -1;
                }
                if (tiles[x][y] == Tile.WALL) {
                    field[x][y] = -1;
                    if (y > 0) {
                        field[x][y - 1] = -1;
                    }
                }
            }
        }
        return field;
    }

    @Override
    public UnitAction getAction(Unit unit, Game game, Debug debug) {
        // убрать самовыпиливание на минах, сделать нормальный подрыв
        // TODO: бои вплотную сделать менее дёргарыми
        // TODO: 1. поиск пути между мин и врагов
        // TODO: 5. столкновение с другими игроками
        // TODO: научить модель прыгать по головам
        // TODO: 2. минимальный aim
        // TODO: 3. отстрел мин
        // TODO: 4. предсказание взрывов мин
        // разделение коробок между своими
        // ограничить глубину просчёта пуль самым дальним юнитом+радиус взрыва пули
        // высчитывать взрывы, кто выиграет

        this.unit = unit;
        this.game = game;
        this.updatesPerSecond = game.getProperties().getTicksPerSecond() * (double) game.getProperties().getUpdatesPerTick();
        this.halfUnitSizeX = game.getProperties().getUnitSize().getX() / 2.0;
        this.debug = debug;
        this.tiles = game.getLevel().getTiles();

        if (game.getPlayers()[0].getId() == unit.getPlayerId()) {
            me = game.getPlayers()[0];
            enemy = game.getPlayers()[1];
        } else {
            me = game.getPlayers()[1];
            enemy = game.getPlayers()[0];
        }

//        this.debug.enable();

        UnitAction action = new UnitAction();
        action.setSwapWeapon(false);

        Unit nearestEnemy = getNearestEnemy();
        Unit nearestAlly = getNearestAlly();

//        boolean nearGround = unit.isOnGround() || (0 != ((int) (unit.getPosition().getY()) - (int) (unit.getPosition().getY() - 2.0 * game.getProperties().getUnitFallSpeed() / updatesPerSecond)));

        if (unit.getMines() > 0) {
            Set<Unit> unitsInMineRadius = getUnitsInMineRadius();
            double damageToMy = 0.0;
            double damageToEnemy = 0.0;
            for (Unit someUnit : unitsInMineRadius) {
                double damage = Math.min(game.getProperties().getMineExplosionParams().getDamage(), someUnit.getHealth());
                if (me.getId() == someUnit.getPlayerId()) {
                    damageToMy += damage;
                } else {
                    damageToEnemy += damage;
                }
            }

            if (damageToEnemy > 0) {
                if (/*me.getScore() + damageToEnemy > enemy.getScore() || */damageToEnemy > damageToMy) {
                    action.setPlantMine(true);
                }
            }
        }
/*
        if (unitsInMineRadius.size() > 1) {
                    && (nearGround)
//                && unit.getWeapon() != null
                    && nearestEnemy != null
                    && (nearestAlly == null || vecUtil.linearLength(vecUtil.substract(nearestAlly.getPosition(), unit.getPosition())) > game.getProperties().getMineExplosionParams().getRadius())
//                && (unit.getWeapon().getFireTimer() == null || unit.getWeapon().getFireTimer() < 2.0 / game.getProperties().getTicksPerSecond())
//                && unit.getWeapon().getMagazine() > 0
                    && vecUtil.linearLength(vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition())) < game.getProperties().getMineExplosionParams().getRadius()
            ) {
//            if (debug.isEnabled()) {
//                System.out.println("FIRE IN THE HOOLE!!!");
//            }
//            action.setAim(new Vec2Double(0.0, -10.0));
                if (unit.getWeapon().getFireTimer() == null || unit.getWeapon().getFireTimer() < EPSILON) {
                    action.setPlantMine(true);
//                action.setShoot(true);
                }

//            return action;
            }
        }
*/

        LootBox nearestWeapon = getNearestWeapon(null);
        willTakeThis = null; // reset every tick
        LootBox nearestLauncher = null; // getNearestWeapon(WeaponType.PISTOL);
        LootBox nearestMineBox = null; // getNearestMineBox();
        LootBox nearestHealthPack = getNearestHealthPack();
        Vec2Double runningPos = unit.getPosition();
        if (unit.getWeapon() == null && nearestWeapon != null) {
            willTakeThis = nearestWeapon;

            runningPos = nearestWeapon.getPosition();
        } else if (unit.getWeapon() != null && unit.getWeapon().getTyp() != WeaponType.PISTOL && nearestLauncher != null) {
            runningPos = nearestLauncher.getPosition();
            action.setSwapWeapon(true);
        } else if (nearestMineBox != null && unit.getMines() < 2) {
            runningPos = nearestMineBox.getPosition();
        } else if (unit.getHealth() < game.getProperties().getUnitMaxHealth() * HEALTH_TO_LOOK_FOR_HEAL && nearestHealthPack != null) {
            runningPos = nearestHealthPack.getPosition();
        } else if (nearestEnemy != null) {
            runningPos = nearestEnemy.getPosition();
        }
        Vec2Double aim = new Vec2Double(0, 0);
        if (nearestEnemy != null) {
//            if (Math.abs(unit.getPosition().getY() - nearestEnemy.getPosition().getY()) <= game.getProperties().getUnitSize().getY() * 1.1
//                    && Math.abs(unit.getPosition().getX() - nearestEnemy.getPosition().getX()) <= game.getProperties().getUnitSize().getX() * 1.1) {
//                aim = vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition());
//                System.out.println("aim");
//            } else {
            aim = findMeanAim(nearestEnemy);
            if (vecUtil.length(aim) < EPSILON) {
                aim = vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition());
            }
//            }
        }
        boolean jump = runningPos.getY() > unit.getPosition().getY();
        if (runningPos.getX() > unit.getPosition().getX() && getTile(unit.getPosition().getX() + 1, unit.getPosition().getY()) == Tile.WALL) {
            jump = true;
        }
        if (runningPos.getX() < unit.getPosition().getX() && getTile(unit.getPosition().getX() - 1, unit.getPosition().getY()) == Tile.WALL) {
            jump = true;
        }

        runningPos = jumpPadHack(runningPos);
        setJumpAndVelocity(runningPos, jump, action);

        if (unit.getWeapon() != null) {
            double spread = unit.getWeapon().getSpread();
            if (unit.getWeapon().getLastAngle() != null) {
                spread = Math.min(spread + vecUtil.angleBetween(aim, vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0)), unit.getWeapon().getParams().getMaxSpread());
            }
            HitProbabilities hitPNew = hitProbability(aim, spread);
            HitProbabilities hitPOld = HitProbabilities.EMPTY;
            if (unit.getWeapon().getLastAngle() != null) {
                hitPOld = hitProbability(vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0), unit.getWeapon().getSpread());
            }
            if (hitPNew.getEnemyHitProbability() - hitPOld.getEnemyHitProbability() >= 0.02 || unit.getWeapon() == null || unit.getWeapon().getLastAngle() == null) {
                action.setAim(vecUtil.normalize(aim, 10.0));
            } else {
                hitPNew = hitPOld;
                Vec2Double lastAim = vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0);
                if (vecUtil.angleBetween(aim, lastAim) < Math.PI / 4.0) {
                    action.setAim(lastAim);
                } else {
                    action.setAim(vecUtil.normalize(aim, 10.0));
                }
            }
            if (unit.getWeapon().getTyp() == WeaponType.ROCKET_LAUNCHER) {
                if (hitPNew.getAllyExplosionProbability() > 0.05) {
                    action.setShoot(false);
                } else if (hitPNew.getEnemyHitProbability() > 0.3) {
                    if (hitPNew.getEnemyHitProbability() >= hitPNew.getAllyHitProbability()) {
                        action.setShoot(true);
                    }
                }
//            } else if (unit.getWeapon().getTyp() == WeaponType.PISTOL) {
//                if (hitPNew.getEnemyHitProbability() > 0.3) {
//                    action.setShoot(true);
//                } else {
//                    System.out.println(unit.getWeapon().getFireTimer() + " " + hitPNew.getEnemyHitProbability());
//                }
            } else if (hitPNew.getEnemyHitProbability() > 0.05) {
                action.setShoot(true);
            }

            if (unit.getWeapon().getMagazine() == 0) {
                action.setReload(true);
            }
        } else {
            action.setReload(false);
            action.setShoot(false);
            action.setAim(vecUtil.normalize(aim, 10.0));
        }

        if (debug.isEnabled()) {
            System.out.println("" + game.getCurrentTick() + ":" + unit.getId() + ":" + action + ":" + unit.getWeapon());
        }

        return action;
    }

    private Set<Unit> getUnitsInMineRadius() {
        Vec2Double position = vecUtil.add(unit.getPosition(), new Vec2Double(0, game.getProperties().getMineSize().getY() / 2.0));
        Set<Unit> units = new HashSet<>();
        for (Unit other : game.getUnits()) {
            Vec2Double otherCenter = vecUtil.getCenter(other.getPosition(), other.getSize());

            if (Math.abs(otherCenter.getX() - position.getX()) <= (other.getSize().getX() / 2.0 + game.getProperties().getMineExplosionParams().getRadius() - game.getProperties().getUnitMaxHorizontalSpeed())
                    && Math.abs(otherCenter.getY() - position.getY()) <= (other.getSize().getY() / 2.0 + game.getProperties().getMineExplosionParams().getRadius()) - game.getProperties().getUnitMaxHorizontalSpeed()) {
                units.add(other);
            }
        }
        return units;
    }

    private void setJumpAndVelocity(Vec2Double runningPos, boolean jump, UnitAction action) {
        action.setVelocity(Math.signum(runningPos.getX() - unit.getPosition().getX()) * game.getProperties().getUnitMaxHorizontalSpeed());
        action.setJump(jump);
        action.setJumpDown(!jump);

        neoAddon(runningPos, action, 100);
    }

    private void neoAddon(Vec2Double runningPos, UnitAction action, int depth) {

        if (game.getBullets().length == 0) {
            return;
        }

        List<DummyBullet> bullets = new ArrayList<>();
        for (Bullet bullet : game.getBullets()) {
            if (bullet.getUnitId() != unit.getId()) {
                DummyBullet dummyBullet = new DummyBullet(bullet, true);
                bullets.add(dummyBullet);
            } else if (bullet.getExplosionParams() != null && bullet.getExplosionParams().getRadius() > 0) {
                DummyBullet dummyBullet = new DummyBullet(bullet, true);
                bullets.add(dummyBullet);
            }
        }

        if (bullets.size() == 0) {
            return;
        }

        Dummy[] allDummies = createAllDummies();
        Set<Dummy> dummies = new HashSet<>();
        dummies.add(new Dummy(unit, new DummyStrat()));
        dummies.add(new Dummy(unit, new LeftJumpingStrat()));
        dummies.add(new Dummy(unit, new LeftRunningStrat()));
        dummies.add(new Dummy(unit, new LeftFallingStrat()));
        dummies.add(new Dummy(unit, new RightJumpingStrat()));
        dummies.add(new Dummy(unit, new RightRunningStrat()));
        dummies.add(new Dummy(unit, new RightFallingStrat()));
        dummies.add(new Dummy(unit, new JumpingStrat()));
        dummies.add(new Dummy(unit, new FallingStrat()));
        dummies.add(new Dummy(unit, new AbortJumpAndThenJumpStrat()));

        for (Dummy dummy : dummies) {
            dummy.setOtherDummies(allDummies);
        }
        for (Dummy dummy : dummies) {
            dummy.isPositionPossible(dummy.position);
        }
        Set<Dummy> lastDead = new HashSet<>();
        int lastDeadTick = 0;
        Set<Dummy> survivors = new HashSet<>(dummies);

        for (int tick = 0; tick < depth; tick++) {
            dummies = new HashSet<>(survivors); // hack optimization
            for (int j = 0; j < game.getProperties().getUpdatesPerTick(); j++) {
                for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                    DummyBullet bullet = iterator.next();
                    bullet.moveOneUpdateHorizontally();
                    lastDeadTick = checkBulletCollisions(dummies, lastDead, lastDeadTick, survivors, tick, bullet);
                    if (bullet.isHittingAWall()) {
                        // explosion
                        lastDeadTick = checkExplosion(dummies, lastDead, lastDeadTick, survivors, tick, bullet);
                        iterator.remove();
                    }
                }
                for (Dummy dummy : dummies) {
                    dummy.moveOneUpdateHorizontally();
                    lastDeadTick = checkBulletsCollisions(bullets, lastDead, lastDeadTick, survivors, tick, dummy);
                }

                for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                    DummyBullet bullet = iterator.next();
                    bullet.moveOneUpdateVertically();
                    lastDeadTick = checkBulletCollisions(dummies, lastDead, lastDeadTick, survivors, tick, bullet);
                    if (bullet.isHittingAWall()) {
                        // explosion
                        lastDeadTick = checkExplosion(dummies, lastDead, lastDeadTick, survivors, tick, bullet);
                        iterator.remove();
                    }
                }
                for (Dummy dummy : dummies) {
                    dummy.moveOneUpdateVertically();
                    lastDeadTick = checkBulletsCollisions(bullets, lastDead, lastDeadTick, survivors, tick, dummy);
                }
            }
        }

        if (lastDead.size() > 0) {
            Dummy choosenOne = null;

            // collision imminent
            if (survivors.size() > 0) {
                for (Dummy survivor : survivors) {
                    if (choosenOne == null || vecUtil.length(survivor.getPosition(), runningPos) < vecUtil.length(choosenOne.getPosition(), runningPos)) {
                        choosenOne = survivor;
                    }
                }
            } else {
                for (Dummy survivor : lastDead) {
                    if (choosenOne == null || vecUtil.length(survivor.getPosition(), runningPos) < vecUtil.length(choosenOne.getPosition(), runningPos)) {
                        choosenOne = survivor;
                    }
                }
            }

            if (choosenOne != null) {
                choosenOne.getStrat().resetTick();
                action.setVelocity(choosenOne.getStrat().getVelocity());
                action.setJump(choosenOne.getStrat().isJumpUp());
                action.setJumpDown(choosenOne.getStrat().isJumpDown());
            }
        }
    }

    private int checkBulletsCollisions(List<DummyBullet> bullets, Set<Dummy> lastDead, int lastDeadTick, Set<Dummy> survivors, int tick, Dummy dummy) {
        for (DummyBullet bullet : bullets) {
            lastDeadTick = checkOneBulletCollision(lastDead, lastDeadTick, survivors, tick, bullet, dummy);
        }
        return lastDeadTick;
    }

    private int checkBulletCollisions(Collection<Dummy> dummies, Set<Dummy> lastDead, int lastDeadTick, Set<Dummy> survivors, int tick, DummyBullet bullet) {
        for (Dummy dummy : dummies) {
            lastDeadTick = checkOneBulletCollision(lastDead, lastDeadTick, survivors, tick, bullet, dummy);
        }
        return lastDeadTick;
    }

    private int checkOneBulletCollision(Set<Dummy> lastDead, int lastDeadTick, Set<Dummy> survivors, int tick, DummyBullet bullet, Dummy dummy) {
        if (bullet.isHittingTheDummy(dummy)) {
            dummy.catchBullet(bullet);
            if (dummy.bulletCount() == 1) {
                if (lastDeadTick != tick) {
                    lastDead.clear();
                    lastDeadTick = tick;
                }
                lastDead.add(dummy);
                survivors.remove(dummy);
            }
        }
        return lastDeadTick;
    }

    private int checkExplosion(Collection<Dummy> dummies, Set<Dummy> lastDead, int lastDeadTick, Set<Dummy> survivors, int tick, DummyBullet bullet) {
        if (bullet.getExplosionRadius() > 0.0) {
            for (Dummy dummy : dummies) {
                if (bullet.isHittingTheDummyWithExplosion(dummy)) {
                    dummy.catchBullet(bullet);
                    if (dummy.bulletCount() == 1) {
                        if (lastDeadTick != tick) {
                            lastDead.clear();
                            lastDeadTick = tick;
                        }
                        lastDead.add(dummy);
                        survivors.remove(dummy);
                    }
                }
            }
        }
        return lastDeadTick;
    }

    private Vec2Double jumpPadHack(Vec2Double runningPos) {
        if (getTile(runningPos.getX() - 1.0, runningPos.getY()) == Tile.JUMP_PAD) {
            return new Vec2Double(runningPos.getX() + 0.5, runningPos.getY());
        } else if (getTile(runningPos.getX() + 1.0, runningPos.getY()) == Tile.JUMP_PAD) {
            return new Vec2Double(runningPos.getX() - 0.5, runningPos.getY());
        }
        return runningPos;
    }

    private Vec2Double findMeanAim(Unit enemy) {
        if (unit.getWeapon() == null) {
            return new Vec2Double(0.0, 0.0);
        }
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 5.0 / game.getProperties().getTicksPerSecond()) || unit.getWeapon().getMagazine() == 0) {
            return new Vec2Double(0.0, 0.0);
        }
        Vec2Double unitCenter = vecUtil.getCenter(unit);
        int hitCount = 0;
        int maxHitCount = 51;
        List<DummyBullet> bullets = new ArrayList<>();
        Vec2Double direction = vecUtil.substract(enemy.getPosition(), unit.getPosition());
        double baseAngle = vecUtil.getAngle(direction);
        int maxSpread = (maxHitCount - 1) / 2;
        for (int i = -maxSpread; i <= maxSpread; i++) {
            Vec2Double adjustedDirection = vecUtil.fromAngle(baseAngle + i / (double) maxSpread * Math.PI / 3.0, 10.0);
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unit.getWeapon().getParams().getExplosion(), unitCenter, adjustedDirection, unit.getId());
            bullets.add(dummyBullet);
        }

        Dummy[] dummies = createAllDummies();
        Dummy enemyDummy = Arrays.stream(dummies).filter(dummy -> dummy.unit.getId() == enemy.getId()).findFirst().get();

        Vec2Double hitVector = new Vec2Double(0, 0);
        while (bullets.size() > 0) {
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                bullet.moveOneUpdateHorizontally();
                bullet.moveOneUpdateVertically();
                if (bullet.isHittingTheDummy(enemyDummy)) {
                    if (debug.isEnabled()) {
                        debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(bullet.position),
                                0.05f, new ColorFloat(0, 1, 1, 0.1f)));
                    }
                    iterator.remove();
                    hitCount++;
                    hitVector = vecUtil.add(bullet.position, hitVector);
                } else if (bullet.isHittingAWall()) {
                    if (bullet.isHittingTheDummyWithExplosion(enemyDummy)) {
                        if (debug.isEnabled()) {
                            debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(bullet.position),
                                    0.05f, new ColorFloat(0, 1, 1, 0.1f)));
                        }
                        hitCount++;
                        hitVector = vecUtil.add(bullet.position, hitVector);
                    } else if (debug.isEnabled()) {
                        debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(bullet.position),
                                0.05f, new ColorFloat(1, 0, 1, OLD_DEBUG_TRANSPARENCY)));
                    }
                    iterator.remove();
                }
            }
            enemyDummy.moveOneUpdateHorizontally();
            enemyDummy.moveOneUpdateVertically();
        }
        if (hitCount == 0) {
            return new Vec2Double(0.0, 0.0);
        }
        return vecUtil.substract(vecUtil.scale(hitVector, 1.0 / (double) hitCount), unitCenter);
    }

    private Dummy[] createAllDummies() {
        Dummy[] dummies = new Dummy[game.getUnits().length];
        Unit[] units = game.getUnits();
        for (int i = 0; i < units.length; i++) {
            Unit unit = units[i];
            Dummy dummy = new Dummy(unit);
            dummies[i] = dummy;
            dummy.setOtherDummies(dummies);
        }
        return dummies;
    }

    private HitProbabilities hitProbability(Vec2Double aim, double spread) {
        if (unit.getWeapon() == null) {
            return HitProbabilities.EMPTY;
        }
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 3.0 / game.getProperties().getTicksPerSecond()) || unit.getWeapon().getMagazine() == 0) {
            return HitProbabilities.EMPTY;
        }
        Vec2Double unitCenter = vecUtil.getCenter(unit);
        int maxHitCount = Math.max(5, Math.min(51, 1 + 2 * ((int) (180.0 * spread / Math.PI))));
        if (debug.isEnabled()) {
            System.out.println(maxHitCount);
        }
        List<DummyBullet> bullets = new ArrayList<>();
        double baseAngle = vecUtil.getAngle(aim);
        int maxSpread = (maxHitCount - 1) / 2;
        for (int i = -maxSpread; i <= maxSpread; i++) {
            Vec2Double adjustedDirection = vecUtil.fromAngle(baseAngle + i / (double) maxSpread * spread, 10.0);
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unit.getWeapon().getParams().getExplosion(), unitCenter, adjustedDirection, unit.getId());
            bullets.add(dummyBullet);
        }
        Set<Dummy> dummies = new HashSet<>();
        for (Unit unit : game.getUnits()) {
            dummies.add(new Dummy(unit));
        }
        HitProbabilities hitProbabilities = new HitProbabilities(maxHitCount);
        while (bullets.size() > 0) { // update
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                boolean hitDetected = false;
                bullet.moveOneUpdateHorizontally();
                for (Dummy dummy : dummies) {
                    if (bullet.isHittingTheDummy(dummy)) {
                        onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                        explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                        hitDetected = true;
                    }
                }
                if (bullet.isHittingAWall()) {
                    hitDetected = true;
                    explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                }
                if (hitDetected) {
                    iterator.remove();
                }
            }
            for (Dummy dummy : dummies) {
                dummy.moveOneUpdateHorizontally();
                for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                    DummyBullet bullet = iterator.next();
                    if (bullet.isHittingTheDummy(dummy)) {
                        onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                        explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                        iterator.remove();
                    }
                }
            }

            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                boolean hitDetected = false;
                bullet.moveOneUpdateVertically();
                for (Dummy dummy : dummies) {
                    if (bullet.isHittingTheDummy(dummy)) {
                        onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                        explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                        hitDetected = true;
                    }
                }
                if (bullet.isHittingAWall()) {
                    hitDetected = true;
                    explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                }
                if (hitDetected) {
                    iterator.remove();
                }
            }
            for (Dummy dummy : dummies) {
                dummy.moveOneUpdateVertically();
                for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                    DummyBullet bullet = iterator.next();
                    if (bullet.isHittingTheDummy(dummy)) {
                        onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                        explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                        iterator.remove();
                    }
                }
            }
        }
        return hitProbabilities;
    }

    private void explodeBullet(Vec2Double unitCenter, HitProbabilities hitProbabilities, Set<Dummy> dummies, DummyBullet bullet) {
        int explodedEnemies = 0;
        int explodedAllies = 0;

        for (Dummy dummy : dummies) {
            if (bullet.isHittingTheDummyWithExplosion(dummy)) {
                onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                if (dummy.unit.getPlayerId() == unit.getPlayerId()) {
                    explodedAllies++;
                } else {
                    explodedEnemies++;
                }
            }
        }
        if (explodedAllies > explodedEnemies) {
            hitProbabilities.allyExplosionCount++;
        }
    }

    private void onBulletHittingADummy(Vec2Double unitCenter, HitProbabilities hitProbabilities, DummyBullet bullet, Dummy dummy) {
        if (dummy.unit.getPlayerId() == unit.getPlayerId()) {
            if (debug.isEnabled()) {
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(bullet.position),
                        0.05f, new ColorFloat(0, 0, 1, OLD_DEBUG_TRANSPARENCY)));
            }
            hitProbabilities.allyHitCount++;
        } else {
            if (debug.isEnabled()) {
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(bullet.position),
                        0.05f, new ColorFloat(0, 1, 0, OLD_DEBUG_TRANSPARENCY)));
            }
            hitProbabilities.enemyHitCount++;
        }
    }

    private Tile getTile(Vec2Double location) {
        return tiles[(int) location.getX()][(int) location.getY()];
    }

    private Tile getTile(double x, double y) {
        return tiles[(int) x][(int) y];
    }

    private LootBox getNearestWeapon(model.WeaponType weaponType) {
        LootBox nearestWeapon = null;
        for (LootBox lootBox : game.getLootBoxes()) {
            if (lootBox.getItem() instanceof Item.Weapon) {
                if (weaponType == null || weaponType == ((Item.Weapon) lootBox.getItem()).getWeaponType()) {
                    if (nearestWeapon == null || distanceSqr(unit.getPosition(),
                            lootBox.getPosition()) < distanceSqr(unit.getPosition(), nearestWeapon.getPosition())) {
                        if (willTakeThis == null || !willTakeThis.getPosition().equals(lootBox.getPosition())) {
                            nearestWeapon = lootBox;
                        }
                    }
                }
            }
        }
        return nearestWeapon;
    }

    private LootBox getNearestMineBox() {
        LootBox nearestMineLootBox = null;
        for (LootBox lootBox : game.getLootBoxes()) {
            if (lootBox.getItem() instanceof Item.Mine) {
                if (nearestMineLootBox == null || distanceSqr(unit.getPosition(),
                        lootBox.getPosition()) < distanceSqr(unit.getPosition(), nearestMineLootBox.getPosition())) {
                    nearestMineLootBox = lootBox;
                }
            }
        }
        return nearestMineLootBox;
    }

    private LootBox getNearestHealthPack() {
        LootBox nearestHealth = null;
        for (LootBox lootBox : game.getLootBoxes()) {
            if (lootBox.getItem() instanceof Item.HealthPack) {
                if (nearestHealth == null || distanceSqr(unit.getPosition(),
                        lootBox.getPosition()) < distanceSqr(unit.getPosition(), nearestHealth.getPosition())) {
                    nearestHealth = lootBox;
                }
            }
        }
        return nearestHealth;
    }

    private Unit getNearestEnemy() {
        Unit nearestEnemy = null;
        for (Unit other : game.getUnits()) {
            if (other.getPlayerId() != unit.getPlayerId()) {
                if (nearestEnemy == null || distanceSqr(unit.getPosition(),
                        other.getPosition()) < distanceSqr(unit.getPosition(), nearestEnemy.getPosition())) {
                    nearestEnemy = other;
                }
            }
        }
        return nearestEnemy;
    }

    private Unit getNearestAlly() {
        Unit nearestAlly = null;
        for (Unit other : game.getUnits()) {
            if (other.getPlayerId() == unit.getPlayerId() && unit.getId() != other.getId()) {
                if (nearestAlly == null || distanceSqr(unit.getPosition(),
                        other.getPosition()) < distanceSqr(unit.getPosition(), nearestAlly.getPosition())) {
                    nearestAlly = other;
                }
            }
        }
        return nearestAlly;
    }

    static class HitProbabilities {
        static final HitProbabilities EMPTY = new HitProbabilities(1);
        int enemyHitCount = 0;
        int allyHitCount = 0;
        int allyExplosionCount = 0;
        int maxHitCount;

        public HitProbabilities(int maxHitCount) {
            this.maxHitCount = maxHitCount;
        }

        public double getEnemyHitProbability() {
            return enemyHitCount / (double) maxHitCount;
        }

        public double getAllyHitProbability() {
            return allyHitCount / (double) maxHitCount;
        }

        public double getAllyExplosionProbability() {
            return allyExplosionCount / (double) maxHitCount;
        }
    }

    class DummyStrat {
        int tick = 0;

        double getVelocity() {
            return 0.0;
        }

        boolean isJumpUp() {
            return false;
        }

        boolean isJumpDown() {
            return false;
        }

        void nextTick() {
            tick++;
        }

        void resetTick() {
            tick = 0;
        }
    }

    class LeftRunningStrat extends DummyStrat {
        @Override
        double getVelocity() {
            return -game.getProperties().getUnitMaxHorizontalSpeed();
        }
    }

    class RightRunningStrat extends DummyStrat {
        @Override
        double getVelocity() {
            return game.getProperties().getUnitMaxHorizontalSpeed();
        }
    }

    class LeftJumpingStrat extends LeftRunningStrat {
        @Override
        boolean isJumpUp() {
            return true;
        }
    }

    class RightJumpingStrat extends RightRunningStrat {
        @Override
        boolean isJumpUp() {
            return true;
        }
    }

    class LeftFallingStrat extends LeftRunningStrat {
        @Override
        boolean isJumpDown() {
            return true;
        }
    }

    class RightFallingStrat extends RightRunningStrat {
        @Override
        boolean isJumpDown() {
            return true;
        }
    }

    class JumpingStrat extends DummyStrat {
        @Override
        boolean isJumpUp() {
            return true;
        }
    }

    class FallingStrat extends DummyStrat {
        @Override
        boolean isJumpDown() {
            return true;
        }
    }

    class AbortJumpAndThenJumpStrat extends DummyStrat {
        @Override
        boolean isJumpUp() {
            return tick != 0;
        }

        @Override
        boolean isJumpDown() {
            return tick == 0;
        }
    }

    class DummyBullet {
        private Vec2Double velocity;
        private Vec2Double position;
        private double size;
        private double explosionRadius = -1.0;
        private int unitId;
        private boolean trace;
        private Vec2Double startPosition;


        public DummyBullet(Bullet bullet) {
            this(bullet, false);
        }

        public DummyBullet(Bullet bullet, boolean increaseSize) {
            this.position = vecUtil.clone(bullet.getPosition());
            this.velocity = vecUtil.clone(bullet.getVelocity());
            this.size = increaseSize ? bullet.getSize() + 0.005 : bullet.getSize();
            if (bullet.getExplosionParams() != null) {
                this.explosionRadius = increaseSize ? bullet.getExplosionParams().getRadius() + 0.05 : bullet.getExplosionParams().getRadius();
            }
            this.unitId = bullet.getUnitId();

            this.startPosition = bullet.getPosition();
            this.trace = true;
        }

        public DummyBullet(BulletParams bulletParams, ExplosionParams explosion, Vec2Double position, Vec2Double direction, int unitId) {
            this.position = vecUtil.clone(position);
            this.unitId = unitId;
            this.velocity = vecUtil.normalize(direction, bulletParams.getSpeed());
            this.size = bulletParams.getSize();
            if (explosion != null) {
                this.explosionRadius = explosion.getRadius();
            }
        }

        public double getExplosionRadius() {
            return explosionRadius;
        }

        private void moveOneUpdateVertically() {
            position.setY(position.getY() + velocity.getY() / updatesPerSecond);
        }

        private void moveOneUpdateHorizontally() {
            position.setX(position.getX() + velocity.getX() / updatesPerSecond);
        }

        public Vec2Double getPosition() {
            return position;
        }

        public Mine isHittingAMine() {
            Mine[] mines = game.getMines();
            for (int i = 0; i < mines.length; i++) {
                Mine mine = mines[i];
                Vec2Double distance = vecUtil.substract(mine.getPosition(), position);
                if (Math.abs(distance.getX()) <= mine.getSize().getX() + size / 2.0 && Math.abs(distance.getY()) <= mine.getSize().getY() + size / 2.0) {
                    return mine;
                }
            }
            return null;
        }

        public boolean isHittingAWall() {
            int xMinusSize = (int) (position.getX() - size / 2.0);
            int xPlusSize = (int) (position.getX() + size / 2.0);
            int yMinusSize = (int) (position.getY() - size / 2.0);
            int yPlusSize = (int) (position.getY() + size / 2.0);
            Tile tile1 = tiles[xMinusSize][yMinusSize];
            Tile tile2 = tiles[xPlusSize][yMinusSize];
            Tile tile3 = tiles[xMinusSize][yPlusSize];
            Tile tile4 = tiles[xPlusSize][yPlusSize];
            boolean hit = tile1 == Tile.WALL || tile2 == Tile.WALL || tile3 == Tile.WALL || tile4 == Tile.WALL;
            if (debug.isEnabled() && hit && trace) {
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(startPosition), vecUtil.toFloatVector(position), (float) size, new ColorFloat(1, 1, 1, 0.2f)));
            }
            return hit;
        }

        public boolean isHittingTheDummy(Dummy dummy) {
            if (dummy.unit.getId() == unitId) {
                return false;
            }
            Vec2Double dummyCenter = vecUtil.getCenter(dummy.getPosition(), dummy.unit.getSize());
            boolean hit = Math.abs(dummyCenter.getX() - position.getX()) <= (dummy.unit.getSize().getX() + size) / 2.0
                    && Math.abs(dummyCenter.getY() - position.getY()) <= (dummy.unit.getSize().getY() + size) / 2.0;
            if (debug.isEnabled() && hit && trace) {
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(startPosition), vecUtil.toFloatVector(position), (float) size, new ColorFloat(0, 1, 1, 0.2f)));
            }

            return hit;
        }

        public boolean isHittingTheDummyWithExplosion(Dummy dummy) {
            Vec2Double dummyCenter = vecUtil.getCenter(dummy.getPosition(), dummy.unit.getSize());

            return Math.abs(dummyCenter.getX() - position.getX()) <= (dummy.unit.getSize().getX() / 2.0 + explosionRadius)
                    && Math.abs(dummyCenter.getY() - position.getY()) <= (dummy.unit.getSize().getY() / 2.0 + explosionRadius);
        }
    }

    class Dummy {
        Set<DummyBullet> bulletsCaught = new HashSet<>();
        private Vec2Double position;
        private Unit unit;
        private double canJumpForSeconds;
        private double jumpSpeed;
        private boolean canCancelJump;
        private long microTick = 0;
        private double velocity = 0.0;
        private DummyStrat strat = new DummyStrat();
        private boolean jumpingUp;
        private boolean jumpingDown;
        private Vec2Double oldPosition;
        private Dummy[] otherDummies = new Dummy[0];

        public Dummy(Unit unit, DummyStrat strat) {
            this(unit);
            this.strat = strat;
        }

        public Dummy(Unit unit) {
            this.position = vecUtil.clone(unit.getPosition());
            this.unit = unit;
            JumpState jumpState = this.unit.getJumpState();
            this.canJumpForSeconds = jumpState.getMaxTime();
            this.jumpSpeed = jumpState.getSpeed();
            this.canCancelJump = jumpState.isCanCancel();
/*
            debug.draw(new CustomData.PlacedText("" + jumpState.isCanJump(), vecUtil.toFloatVector(unit.getPosition()), TextAlignment.CENTER, 20.0f, new ColorFloat(1,1,1,1)));
            debug.draw(new CustomData.PlacedText("" + jumpState.isCanCancel(), vecUtil.toFloatVector(vecUtil.add(unit.getPosition(), new Vec2Double(0.0,0.4))), TextAlignment.CENTER, 20.0f, new ColorFloat(1,1,1,1)));
            debug.draw(new CustomData.PlacedText("" + jumpState.getSpeed(), vecUtil.toFloatVector(vecUtil.add(unit.getPosition(), new Vec2Double(0.0,0.8))), TextAlignment.CENTER, 20.0f, new ColorFloat(1,1,1,1)));
            debug.draw(new CustomData.PlacedText("" + jumpState.getMaxTime(), vecUtil.toFloatVector(vecUtil.add(unit.getPosition(), new Vec2Double(0.0,1.2))), TextAlignment.CENTER, 20.0f, new ColorFloat(1,1,1,1)));
*/
        }

        public Dummy[] getOtherDummies() {
            return otherDummies;
        }

        public void setOtherDummies(Dummy[] otherDummies) {
            this.otherDummies = otherDummies;
        }

        public boolean isHittingTheDummy(Dummy dummy, Vec2Double position) {
            if (dummy.unit.getId() == unit.getId()) {
                return false;
            }
            return Math.abs(dummy.getPosition().getX() - position.getX()) <= dummy.unit.getSize().getX()
                    && Math.abs(dummy.getPosition().getY() - position.getY()) <= dummy.unit.getSize().getY();
        }

        void catchBullet(DummyBullet bullet) {
            bulletsCaught.add(bullet);
        }

        int bulletCount() {
            return bulletsCaught.size();
        }

        public Vec2Double getPosition() {
            return position;
        }

        public void moveOneUpdateHorizontally() {
            if (microTick == 0) {
                oldPosition = position;

                velocity = strat.getVelocity();
                jumpingUp = strat.isJumpUp();
                jumpingDown = strat.isJumpDown();
                strat.nextTick();
            }
            microTick++;
            if (microTick == game.getProperties().getUpdatesPerTick()) {
                microTick = 0;
            }

            if (velocity != 0) {
                Vec2Double newPosition = vecUtil.add(position, new Vec2Double(velocity / updatesPerSecond, 0));
                if (isPositionPossible(newPosition)) {
                    position = newPosition;
                }
            }
        }

        public void moveOneUpdateVertically() {
            boolean isFalling = true;
            if (isPositionAffectedByLadder(position)) {
                isFalling = false;
                if (jumpingUp) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, game.getProperties().getUnitJumpSpeed() / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        position = newPosition;
                    }
                }
            } else if (canJumpForSeconds > 0.0) {
                if (canCancelJump && jumpingDown) {
                    canJumpForSeconds = 0.0;
                } else if (jumpingUp || !canCancelJump) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, jumpSpeed / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        position = newPosition;
                        isFalling = false;
                    } else {
                        canCancelJump = true;
                        canJumpForSeconds = 0.0;
                        jumpSpeed = 0.0;
                    }
                    canJumpForSeconds -= 1.0 / updatesPerSecond;
                } else {
                    canJumpForSeconds -= 1.0 / updatesPerSecond;
                }
            }
            if ((isFalling && !isStandingPosition(position)) || jumpingDown) {
                Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, -game.getProperties().getUnitFallSpeed() / updatesPerSecond));
                if (isPositionPossible(newPosition)) {
                    position = newPosition;
                }
            }
            if (debug.isEnabled() && microTick == 0 && vecUtil.length(vecUtil.substract(position, oldPosition)) > EPSILON) {
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(oldPosition), vecUtil.toFloatVector(position), 0.05f, new ColorFloat(bulletsCaught.size() / 2.0f, 0, 1, 1)));
            }
            if (isStandingPosition(position) && /*not jumping*/(canCancelJump || canJumpForSeconds < EPSILON)) {
                canCancelJump = true;
                canJumpForSeconds = game.getProperties().getUnitJumpTime();
                jumpSpeed = game.getProperties().getUnitJumpSpeed();
            }
            if (isPositionAffectedByJumpPad(position)) {
                canCancelJump = false;
                canJumpForSeconds = game.getProperties().getJumpPadJumpTime();
                jumpSpeed = game.getProperties().getJumpPadJumpSpeed();
            }
        }

        public boolean isPositionPossible(Vec2Double position) {
            for (Dummy other : otherDummies) {
                if (isHittingTheDummy(other, position)) {
                    return false;
                }
            }
            Tile tile00 = getTile(position.getX() - halfUnitSizeX, position.getY());
            Tile tile10 = getTile(position.getX() + halfUnitSizeX, position.getY());
            Tile tile01 = getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY() / 2.0);
            Tile tile11 = getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY() / 2.0);
            Tile tile02 = getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY());
            Tile tile12 = getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY());
            return tile00 != Tile.WALL && tile10 != Tile.WALL && tile01 != Tile.WALL && tile11 != Tile.WALL && tile02 != Tile.WALL && tile12 != Tile.WALL;
        }

        public boolean isPositionAffectedByJumpPad(Vec2Double position) {
            Tile tile00 = getTile(position.getX() - halfUnitSizeX, position.getY());
            Tile tile10 = getTile(position.getX() + halfUnitSizeX, position.getY());
            Tile tile01 = getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY() / 2.0);
            Tile tile11 = getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY() / 2.0);
            Tile tile02 = getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY());
            Tile tile12 = getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY());
            return (tile00 == Tile.JUMP_PAD || tile10 == Tile.JUMP_PAD || tile01 == Tile.JUMP_PAD ||
                    tile11 == Tile.JUMP_PAD || tile02 == Tile.JUMP_PAD || tile12 == Tile.JUMP_PAD)
                    && !isPositionAffectedByLadder(position);
        }


        public boolean isPositionAffectedByLadder(Vec2Double position) {
            Tile centalTile = getTile(vecUtil.getCenter(position, unit.getSize()));
            Tile tileUnderCenter = getTile(vecUtil.add(position, new Vec2Double(0.0, -EPSILON)));
            return centalTile == Tile.LADDER || tileUnderCenter == Tile.LADDER;
        }

        public boolean isStandingPosition(Vec2Double position) {
            if (isPositionAffectedByLadder(position)) {
                return true;
            }

            boolean sameVerticalTile = (0 == ((int) (position.getY()) - (int) (position.getY() - game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            Tile tile0u = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, -game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            Tile tile1u = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, -game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            return !sameVerticalTile && (tile0u == Tile.WALL || tile1u == Tile.WALL || tile0u == Tile.PLATFORM || tile1u == Tile.PLATFORM);
        }

        public DummyStrat getStrat() {
            return strat;
        }
    }
}