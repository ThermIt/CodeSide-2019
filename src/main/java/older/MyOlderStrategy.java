package older;

import model.*;
import strategy.DistanceMap;
import strategy.FlatWorldStrategy;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

import java.util.*;

public class MyOlderStrategy implements Strategy {

    public static final double HEALTH_TO_LOOK_FOR_HEAL = 0.90;
    public static final double MAX_SCORE = 100000.0;
    private static final double EPSILON = 0.000000001;
    private static LootBox willTakeThis;
    private static int willTakeUnitId = -1;

    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;
    private double updatesPerSecond;
    private double halfUnitSizeX;
    private double halfUnitSizeY;
    private double fullUnitSizeX;
    private double fullUnitSizeY;
    private Tile[][] tiles;
    private Tile[][] jumppads;
    private Player me;
    private FlatWorldStrategy strat = new FlatWorldStrategy();
    private DistanceMap distanceMap;

    @Override
    public Map<Integer, UnitAction> getAllActions(PlayerView playerView, Debug debug) {
        strat.UpdateTick(playerView, debug);
        Map<Integer, UnitAction> actions = new HashMap<>();
        for (model.Unit unit : playerView.getGame().getUnits()) {
            if (unit.getPlayerId() == playerView.getMyId()) {
                actions.put(unit.getId(), getAction(unit, playerView.getGame()));
            }
        }
        return actions;
    }

    public UnitAction getAction(Unit unit, Game game) {
        this.unit = unit;
        this.game = game;
        this.updatesPerSecond = game.getProperties().getTicksPerSecond() * (double) game.getProperties().getUpdatesPerTick();
        this.fullUnitSizeX = game.getProperties().getUnitSize().getX();
        this.fullUnitSizeY = game.getProperties().getUnitSize().getY();
        this.halfUnitSizeX = fullUnitSizeX / 2.0;
        this.halfUnitSizeY = fullUnitSizeY / 2.0;
        this.tiles = game.getLevel().getTiles();
        if (this.jumppads == null) {
            Tile[][] jumppads = new Tile[this.tiles.length][this.tiles[0].length];
            for (int i = 0; i < this.tiles.length; i++) {
                for (int j = 0; j < this.tiles[0].length; j++) {
                    if (isJumppadNearby(i, j)) {
                        jumppads[i][j] = Tile.JUMP_PAD;
                    } else {
                        jumppads[i][j] = Tile.EMPTY;
                    }
                }
            }
            this.jumppads = jumppads;
        }
        if (this.distanceMap == null) {
            this.distanceMap = new DistanceMap(tiles, strat);
        }
        this.distanceMap.tickUpdate(game, unit);
        me = strat.getMe();
        UnitAction action = new UnitAction();
        action.setSwapWeapon(false);
        Unit nearestEnemy = getNearestEnemy();
        DistanceMap.TargetType type = DistanceMap.TargetType.EMPTY;
        LootBox nearestWeapon = getNearestWeapon(null);
        willTakeThis = null;
        willTakeUnitId = -1;
        LootBox nearestLauncher = null;
        LootBox nearestMineBox = getNearestMineBox();
        LootBox nearestHealthPack = getNearestHealthPack();
        Vec2Double runningPos = unit.getPosition();
        if (unit.getHealth() < game.getProperties().getUnitMaxHealth() * HEALTH_TO_LOOK_FOR_HEAL && nearestHealthPack != null) {
            runningPos = nearestHealthPack.getPosition();
            type = DistanceMap.TargetType.HEALTH;
        } else if (unit.getWeapon() == null && nearestWeapon != null) {
            willTakeThis = nearestWeapon;
            willTakeUnitId = unit.getId();
            runningPos = nearestWeapon.getPosition();
        } else if (nearestMineBox != null && unit.getMines() < ((unit.getWeapon().getTyp() == WeaponType.ROCKET_LAUNCHER) ? 1 : 2)) {
            runningPos = nearestMineBox.getPosition();
            type = DistanceMap.TargetType.MINE;
        } else if (nearestEnemy != null) {
            runningPos = nearestEnemy.getPosition();
        }
        Vec2Double aim = new Vec2Double(0, 0);
        if (nearestEnemy != null) {
            aim = findMeanAim(nearestEnemy);
            if (vecUtil.length(aim) < EPSILON) {
                aim = vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition());
            }
        }

        this.distanceMap.updateTarget(type, runningPos, game);
        neoAddon(action, 66);

        if (unit.getWeapon() != null) {
            if (unit.getWeapon().getLastAngle() != null) {
                aim = vecUtil.add(vecUtil.normalize(aim, 10), vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 5)); // smooth hack
            }

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
            } else if (unit.getWeapon().getTyp() == WeaponType.PISTOL) {
                if (hitPNew.getEnemyHitProbability() - hitPNew.getAllyHitProbability() > 0.3) {
                    action.setShoot(true);
                }
            } else if (hitPNew.getEnemyHitProbability() - hitPNew.getAllyHitProbability() > 0.1) {
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

        return action;
    }

    private boolean isJumppadNearby(int x, int y) {
        if (jumppads != null) {
            return jumppads[x][y] == Tile.JUMP_PAD;
        }
        return isJumppad(x - 1, y)
                || isJumppad(x - 1, y + 1)
                || isJumppad(x - 1, y + 2)
                || isJumppad(x, y)
                || isJumppad(x, y + 1)
                || isJumppad(x, y + 2)
                || isJumppad(x + 1, y)
                || isJumppad(x + 1, y + 1)
                || isJumppad(x + 1, y + 2);
    }

    private boolean isJumppad(int x, int y) {
        if (x < 0 || x >= tiles.length || y < 0 || y >= tiles[0].length) {
            return false;
        }
        return tiles[x][y] == Tile.JUMP_PAD;
    }

    private void neoAddon(UnitAction action, int depth) {
        List<DummyMine> mines = new LinkedList<>();
        for (Mine mine : game.getMines()) {
            DummyMine dummyMine = new DummyMine(mine);
            mines.add(dummyMine);
        }

        List<DummyBullet> bullets = new LinkedList<>();
        for (Bullet bullet : game.getBullets()) {
            if (bullet.getUnitId() != unit.getId()) {
                DummyBullet dummyBullet = new DummyBullet(bullet, true);
                bullets.add(dummyBullet);
            } else if (bullet.getExplosionParams() != null && bullet.getExplosionParams().getRadius() > 0) {
                DummyBullet dummyBullet = new DummyBullet(bullet, true);
                bullets.add(dummyBullet);
            }
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
        dummies.add(new Dummy(unit, new PlatformJumpStrat()));
        dummies.add(new Dummy(unit, new PlatformJumpStratLeft()));
        dummies.add(new Dummy(unit, new PlatformJumpStratRight()));
        dummies.add(new Dummy(unit, new LeftAlternatingJumpStrat()));
        dummies.add(new Dummy(unit, new RightAlternatingJumpStrat()));

        HashMap<Dummy, Double> scores = new HashMap<>();
        dummies.forEach(item -> scores.put(item, 100000.0));

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
            dummies = new HashSet<>(survivors);
            for (Dummy dum : dummies) {
                int finalTick = tick;
                scores.compute(dum, (k, v) -> Math.min(finalTick / 100.0 + distanceMap.getDistanceFromTarget(k.getPosition()), v));
            }
            for (int j = 0; j < game.getProperties().getUpdatesPerTick(); j++) {
                for (DummyMine mine : mines) {
                    mine.tick();
                    if (mine.state == MineState.IDLE) {
                        for (Dummy dummy : dummies) {
                            mine.trigger(dummy);
                        }
                    }
                    if (mine.explodes() && mine.explode()) {
                        for (Dummy dummy : dummies) {
                            if (mine.isHittingDummyWithExplosion(dummy)) {
                                dummy.catchExplosion(mine, game.getProperties().getMineExplosionParams().getDamage());
                                if (survivors.contains(dummy)) {
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
                }
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
                for (Dummy other : allDummies) {
                    other.moveOneUpdateHorizontally();
                    other.moveOneUpdateVertically();
                }
            }
        }

        Dummy choosenOne = null;

        double choosenScore = MAX_SCORE;
        if (survivors.size() > 0) {
            for (Dummy survivor : survivors) {
                double surScore = scores.get(survivor);
                if (choosenOne == null || surScore < choosenScore) {
                    choosenOne = survivor;
                    choosenScore = surScore;
                }
            }
        } else {
            for (Dummy survivor : lastDead) {
                Double surScore = scores.get(survivor);
                if (choosenOne == null || surScore < choosenScore) {
                    choosenOne = survivor;
                    choosenScore = surScore;
                }
            }
        }

        if (choosenOne != null) {
            choosenOne.getStrat().reset(new Dummy(unit));
            action.setVelocity(choosenOne.getStrat().getVelocity());
            action.setJump(choosenOne.getStrat().isJumpUp());
            action.setJumpDown(choosenOne.getStrat().isJumpDown());
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
            if (survivors.contains(dummy)) {
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
                    dummy.catchExplosion(bullet, bullet.getExplosionDamage());
                    if (survivors.contains(dummy)) {
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
                    iterator.remove();
                    hitCount++;
                    hitVector = vecUtil.add(bullet.position, hitVector);
                } else if (bullet.isHittingAWall()) {
                    if (bullet.isHittingTheDummyWithExplosion(enemyDummy)) {
                        hitCount++;
                        hitVector = vecUtil.add(bullet.position, hitVector);
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
            Dummy dummy;
            if (unit.getPlayerId() == me.getId() && unit.getId() > this.unit.getId()) {
                // stuck prevention hack
                dummy = new Dummy(unit, new JumpingStrat());
            } else {
                dummy = new Dummy(unit);
            }
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
        double baseAngle = vecUtil.getAngle(aim);
        List<DummyBullet> bullets = createDummyBullets(unitCenter, maxHitCount, baseAngle, spread);
        Dummy[] dummies = createAllDummies();
        HitProbabilities hitProbabilities = new HitProbabilities(maxHitCount);
        while (bullets.size() > 0) { // update
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                boolean hitDetected = checkDummiesForHit(unitCenter, dummies, hitProbabilities, bullet)
                        || moveBulletHorizontally(bullet)
                        || checkDummiesForHit(unitCenter, dummies, hitProbabilities, bullet)
                        || bullet.isHittingAWall();
                if (hitDetected) {
                    explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                    iterator.remove();
                }
            }
            for (Dummy dummy : dummies) {
                dummy.moveOneUpdateHorizontally();
            }
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                boolean hitDetected = checkDummiesForHit(unitCenter, dummies, hitProbabilities, bullet)
                        || moveBulletVertically(bullet)
                        || checkDummiesForHit(unitCenter, dummies, hitProbabilities, bullet)
                        || bullet.isHittingAWall();
                if (hitDetected) {
                    explodeBullet(unitCenter, hitProbabilities, dummies, bullet);
                    iterator.remove();
                }
            }
            for (Dummy dummy : dummies) {
                dummy.moveOneUpdateVertically();
            }
        }
        return hitProbabilities;
    }

    private boolean moveBulletVertically(DummyBullet bullet) {
        bullet.moveOneUpdateVertically();
        return false;
    }

    private boolean moveBulletHorizontally(DummyBullet bullet) {
        bullet.moveOneUpdateHorizontally();
        return false;
    }

    private boolean checkDummiesForHit(Vec2Double unitCenter, Dummy[] dummies, HitProbabilities hitProbabilities, DummyBullet bullet) {
        for (Dummy dummy : dummies) {
            if (bullet.isHittingTheDummy(dummy)) {
                onBulletHittingADummy(unitCenter, hitProbabilities, bullet, dummy);
                return true;
            }
        }
        return false;
    }

    private List<DummyBullet> createDummyBullets(Vec2Double from, int numBullets, double baseAngle, double spread) {
        int maxSpreadNum = (numBullets - 1) / 2;
        List<DummyBullet> bullets = new LinkedList<>();
        for (int i = -maxSpreadNum; i <= maxSpreadNum; i++) {
            Vec2Double adjustedDirection = vecUtil.fromAngle(baseAngle + i / (double) maxSpreadNum * spread, 10.0);
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unit.getWeapon().getParams().getExplosion(), from, adjustedDirection, unit.getId());
            bullets.add(dummyBullet);
        }
        return bullets;
    }

    private void explodeBullet(Vec2Double unitCenter, HitProbabilities hitProbabilities, Dummy[] dummies, DummyBullet bullet) {
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
            hitProbabilities.allyHitCount++;
        } else {
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
                    if (nearestWeapon == null ||
                            distanceMap.getDistance(lootBox.getPosition()) < distanceMap.getDistance(nearestWeapon.getPosition())) {
                        if (willTakeUnitId == unit.getId() || willTakeThis == null || !willTakeThis.getPosition().equals(lootBox.getPosition())) {
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
                if (nearestMineLootBox == null || distanceMap.getDistance(lootBox.getPosition()) < distanceMap.getDistance(nearestMineLootBox.getPosition())) {
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
                if (nearestHealth == null || distanceMap.getDistance(lootBox.getPosition()) < distanceMap.getDistance(nearestHealth.getPosition())) {
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
                if (nearestEnemy == null || distanceMap.getDistance(other.getPosition()) < distanceMap.getDistance(nearestEnemy.getPosition())) {
                    nearestEnemy = other;
                }
            }
        }
        return nearestEnemy;
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


    class PlatformJumpStratLeft extends PlatformJumpStrat {
        @Override
        double getVelocity() {
            return -game.getProperties().getUnitMaxHorizontalSpeed() / 2.0;
        }
    }

    class PlatformJumpStratRight extends PlatformJumpStrat {
        @Override
        double getVelocity() {
            return game.getProperties().getUnitMaxHorizontalSpeed() / 2.0;
        }
    }

    class PlatformJumpStrat extends DummyStrat {
        @Override
        boolean isJumpUp() {
            double x = dummy.getPosition().getX();
            double y = dummy.getPosition().getY();
            boolean jump = true;
            if (dummy.canJumpForSeconds < game.getProperties().getUnitJumpTime()
                    && ((int) y - (int) (y + EPSILON - game.getProperties().getUnitJumpSpeed() / game.getProperties().getTicksPerSecond()) == 1)
                    && (getTile(x - halfUnitSizeX + EPSILON, y + EPSILON - game.getProperties().getUnitJumpSpeed() / game.getProperties().getTicksPerSecond()) == Tile.PLATFORM
                    || getTile(x + halfUnitSizeX - EPSILON, y + EPSILON - game.getProperties().getUnitJumpSpeed() / game.getProperties().getTicksPerSecond()) == Tile.PLATFORM)) {
                jump = false;
            }

            return jump;
        }
    }

    class DummyStrat {
        int tick = 0;

        Dummy dummy;

        public void setDummy(Dummy dummy) {
            this.dummy = dummy;
        }

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

        void reset(Dummy dummy) {
            this.tick = 0;
            this.dummy = dummy;
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

    class LeftAlternatingJumpStrat extends JumpingStrat {
        int direction = -1;
        private int alternatingInterval = (int) (game.getProperties().getUnitJumpTime() * game.getProperties().getTicksPerSecond() / 2.0);

        @Override
        double getVelocity() {
            return (double) direction * game.getProperties().getUnitMaxHorizontalSpeed();
        }

        @Override
        void nextTick() {
            super.nextTick();
            if (tick % alternatingInterval == 0) {
                direction = direction * -1;
            }
        }
    }

    class RightAlternatingJumpStrat extends LeftAlternatingJumpStrat {
        @Override
        double getVelocity() {
            return -super.getVelocity();
        }
    }

    class DummyMine {
        private Vec2Double position;
        private double timer;
        private model.MineState state;
        private Mine mine;

        public DummyMine(Mine mine) {
            position = mine.getPosition();
            Double timer = mine.getTimer();
            this.timer = timer == null ? -1.0 : timer;
            state = mine.getState();
            this.mine = mine;
        }

        public Vec2Double getPosition() {
            return position;
        }

        public void tick() {
            if (timer >= 0.0) {
                timer = timer - 1.0 / updatesPerSecond;
                if (state == MineState.PREPARING && timer <= EPSILON) {
                    state = MineState.IDLE;
                }
            }
        }

        public boolean explode() {
            if (state != MineState.EXPLODED) {
                state = MineState.EXPLODED;
                return true;
            }
            return false;
        }

        public void trigger(Dummy dummy) {
            if (state == MineState.IDLE) {
                double x = dummy.getPosition().getX();
                double y = dummy.getPosition().getY() + halfUnitSizeY;

                if (Math.abs(x - position.getX()) <= (halfUnitSizeX + mine.getTriggerRadius())
                        && Math.abs(y - position.getY()) <= (halfUnitSizeY + mine.getTriggerRadius())) {
                    state = MineState.TRIGGERED;
                    timer = game.getProperties().getMineTriggerTime();
                }
            }
        }

        public boolean isHittingDummyWithExplosion(Dummy dummy) {
            return (Math.abs(dummy.getPosition().getX() - position.getX()) <= (halfUnitSizeX + mine.getExplosionParams().getRadius())
                    && Math.abs(dummy.getPosition().getY() + halfUnitSizeY - position.getY()) <= (halfUnitSizeY + mine.getExplosionParams().getRadius()));
        }

        public boolean explodes() {
            return state == MineState.TRIGGERED && timer <= EPSILON;
        }
    }

    class DummyBullet {
        private Vec2Double velocity;
        private Vec2Double position;
        private double size;
        private double radius;
        private double explosionRadius = -1.0;
        private int unitId;
        private boolean trace;
        private Vec2Double startPosition;
        private int damage;
        private int explosionDamage = 0;

        public DummyBullet(Bullet bullet, boolean increaseSize) {
            this.damage = bullet.getDamage();
            this.position = vecUtil.clone(bullet.getPosition());
            this.velocity = vecUtil.clone(bullet.getVelocity());
            this.size = increaseSize ? bullet.getSize() + 0.005 : bullet.getSize();
            this.radius = this.size / 2.0;
            if (bullet.getExplosionParams() != null) {
                this.explosionRadius = increaseSize ? bullet.getExplosionParams().getRadius() + 0.05 : bullet.getExplosionParams().getRadius();
                this.explosionDamage = bullet.getExplosionParams().getDamage();
            }
            this.unitId = bullet.getUnitId();

            this.startPosition = bullet.getPosition();
            this.trace = true;
        }

        public DummyBullet(BulletParams bulletParams, ExplosionParams explosion, Vec2Double position, Vec2Double direction, int unitId) {
            this.damage = bulletParams.getDamage();
            this.position = vecUtil.clone(position);
            this.unitId = unitId;
            this.velocity = vecUtil.normalize(direction, bulletParams.getSpeed());
            this.size = bulletParams.getSize();
            this.radius = this.size / 2.0;
            if (explosion != null) {
                this.explosionRadius = explosion.getRadius();
                this.explosionDamage = explosion.getDamage();
            }
        }

        public int getExplosionDamage() {
            return explosionDamage;
        }

        public int getDamage() {
            return damage;
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

        public boolean isHittingAWall() {
            int xMinusSize = (int) (position.getX() - radius);
            int xPlusSize = (int) (position.getX() + radius);
            int yMinusSize = (int) (position.getY() - radius);
            int yPlusSize = (int) (position.getY() + radius);
            boolean hit = tiles[xMinusSize][yMinusSize] == Tile.WALL
                    || tiles[xPlusSize][yMinusSize] == Tile.WALL
                    || tiles[xMinusSize][yPlusSize] == Tile.WALL
                    || tiles[xPlusSize][yPlusSize] == Tile.WALL;
            return hit;
        }

        public boolean isHittingTheDummy(Dummy dummy) {
            if (dummy.unit.getId() == unitId) {
                return false;
            }
            boolean hit = Math.abs(dummy.getPosition().getX() - position.getX()) <= halfUnitSizeX + radius
                    && Math.abs(dummy.getPosition().getY() + halfUnitSizeY - position.getY()) <= halfUnitSizeY + radius;
            return hit;
        }

        public boolean isHittingTheDummyWithExplosion(Dummy dummy) {
            return Math.abs(dummy.getPosition().getX() - position.getX()) <= (halfUnitSizeX + explosionRadius)
                    && Math.abs(dummy.getPosition().getY() + halfUnitSizeY - position.getY()) <= (halfUnitSizeY + explosionRadius);
        }
    }

    class Dummy {
        Set<Object> bulletsCaught = new HashSet<>();
        Set<Object> explosionsCaught = new HashSet<>();
        private double damage = 0.0;
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
            strat.setDummy(this);
            this.strat = strat;
        }

        public Dummy(Unit unit) {
            this.position = vecUtil.clone(unit.getPosition());
            this.unit = unit;
            JumpState jumpState = this.unit.getJumpState();
            this.canJumpForSeconds = jumpState.getMaxTime();
            this.jumpSpeed = jumpState.getSpeed();
            this.canCancelJump = jumpState.isCanCancel();
        }

        public void setOtherDummies(Dummy[] otherDummies) {
            this.otherDummies = otherDummies;
        }

        public boolean isHittingTheDummy(Dummy dummy, Vec2Double position) {
            if (dummy.unit.getId() == unit.getId()) {
                return false;
            }
            return Math.abs(dummy.getPosition().getX() - position.getX()) <= fullUnitSizeX
                    && Math.abs(dummy.getPosition().getY() - position.getY()) <= fullUnitSizeY;
        }

        void catchBullet(DummyBullet bullet) {
            if (!bulletsCaught.contains(bullet)) {
                bulletsCaught.add(bullet);
                damage += bullet.getDamage();
            }
        }

        void catchExplosion(Object from, int damage) {
            if (!explosionsCaught.contains(from)) {
                this.explosionsCaught.add(from);
                this.damage += damage;
            }
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
            boolean positionAffectedByLadder = isPositionAffectedByLadder(position);
            if (isPositionAffectedByJumpPad(position, positionAffectedByLadder)) {
                canCancelJump = false;
                canJumpForSeconds = game.getProperties().getJumpPadJumpTime();
                jumpSpeed = game.getProperties().getJumpPadJumpSpeed();
            }
            if (positionAffectedByLadder) {
                // ползаем по лестнице
                if (jumpingUp) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, game.getProperties().getUnitJumpSpeed() / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        position = newPosition;
                    }
                } else if (jumpingDown) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, -game.getProperties().getUnitFallSpeed() / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        position = newPosition;
                    }
                }
                resetJumpState();
            } else {
                // прыгаем
                boolean isJumping = false;
                if (canJumpForSeconds > EPSILON && (!canCancelJump || jumpingUp)) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, jumpSpeed / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        canJumpForSeconds -= 1.0 / updatesPerSecond;
                        position = newPosition;
                        isJumping = true;
                    }
                }
                // падаем
                if (!isJumping) {
                    boolean isStandingPosition = isStandingPosition(position, positionAffectedByLadder);
                    if (jumpingDown || !isStandingPosition) {
                        resetJumpStateToFreeFall();
                        Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, -game.getProperties().getUnitFallSpeed() / updatesPerSecond));
                        if (isPositionPossible(newPosition)) {
                            position = newPosition;
                        } else {
                            resetJumpState();
                        }
                    } else if (isStandingPosition) {
                        resetJumpState();
                    }
                }
            }
        }

        private void resetJumpState() {
            canCancelJump = true;
            canJumpForSeconds = game.getProperties().getUnitJumpTime();
            jumpSpeed = game.getProperties().getUnitJumpSpeed();
        }

        private void resetJumpStateToFreeFall() {
            canCancelJump = false;
            canJumpForSeconds = 0.0;
            jumpSpeed = 0.0;
        }

        public boolean isPositionPossible(Vec2Double position) {
            for (Dummy other : otherDummies) {
                if (isHittingTheDummy(other, position)) {
                    return false;
                }
            }
            return getTile(position.getX() - halfUnitSizeX, position.getY()) != Tile.WALL
                    && getTile(position.getX() + halfUnitSizeX, position.getY()) != Tile.WALL
                    && getTile(position.getX() - halfUnitSizeX, position.getY() + halfUnitSizeY) != Tile.WALL
                    && getTile(position.getX() + halfUnitSizeX, position.getY() + halfUnitSizeY) != Tile.WALL
                    && getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY()) != Tile.WALL
                    && getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY()) != Tile.WALL;
        }

        public boolean isPositionAffectedByJumpPad(Vec2Double position, boolean positionAffectedByLadder) {
            if (positionAffectedByLadder || !isJumppadNearby((int) position.getX(), (int) position.getY())) {
                return false;
            }
            Tile tile00 = getTile(position.getX() - halfUnitSizeX, position.getY());
            Tile tile10 = getTile(position.getX() + halfUnitSizeX, position.getY());
            Tile tile01 = getTile(position.getX() - halfUnitSizeX, position.getY() + halfUnitSizeY);
            Tile tile11 = getTile(position.getX() + halfUnitSizeX, position.getY() + halfUnitSizeY);
            Tile tile02 = getTile(position.getX() - halfUnitSizeX, position.getY() + unit.getSize().getY());
            Tile tile12 = getTile(position.getX() + halfUnitSizeX, position.getY() + unit.getSize().getY());
            return tile00 == Tile.JUMP_PAD || tile10 == Tile.JUMP_PAD || tile01 == Tile.JUMP_PAD ||
                    tile11 == Tile.JUMP_PAD || tile02 == Tile.JUMP_PAD || tile12 == Tile.JUMP_PAD;
        }

        public boolean isPositionAffectedByLadder(Vec2Double position) {
            return getTile(position.getX(), position.getY() + halfUnitSizeY) == Tile.LADDER
                    || getTile(position.getX(), position.getY() - EPSILON) == Tile.LADDER;
        }

        public boolean isStandingPosition(Vec2Double position, boolean positionAffectedByLadder) {
            if (positionAffectedByLadder) {
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