package older;

import model.*;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

import java.util.*;

public class MyOlderStrategy implements Strategy {

    public static final double HEALTH_TO_LOOK_FOR_HEAL = 0.90;
    private static final double EPSILON = 0.000000000001;
    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;
    private double updatesPerSecond;
    private double halfUnitSizeX;
    private Tile[][] tiles;

    static double distanceSqr(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    @Override
    public UnitAction getAction(Unit unit, Game game, Debug debug) {
        this.unit = unit;
        this.game = game;
        this.updatesPerSecond = game.getProperties().getTicksPerSecond() * (double) game.getProperties().getUpdatesPerTick();
        this.halfUnitSizeX = game.getProperties().getUnitSize().getX() / 2.0;
        this.tiles = game.getLevel().getTiles();

        UnitAction action = new UnitAction();
        action.setSwapWeapon(false);

        Unit nearestEnemy = getNearestEnemy();
        LootBox nearestWeapon = getNearestWeapon(null);
        Vec2Double runningPos = unit.getPosition();
        if (unit.getWeapon() == null && nearestWeapon != null) {
            runningPos = nearestWeapon.getPosition();
        } else if (nearestEnemy != null) {
            runningPos = nearestEnemy.getPosition();
            if (unit.getHealth() < game.getProperties().getUnitMaxHealth() * HEALTH_TO_LOOK_FOR_HEAL) {
                LootBox nearestHealthPack = getNearestHealthPack();
                if (nearestHealthPack != null) {
                    runningPos = nearestHealthPack.getPosition();
                }
            }
        }
        Vec2Double aim = new Vec2Double(0, 0);
        if (nearestEnemy != null) {
            aim = findMeanAim(nearestEnemy);
            if (vecUtil.length(aim) < EPSILON) {
                aim = vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition());
            }
        }
        boolean jump = runningPos.getY() > unit.getPosition().getY();
        if (runningPos.getX() > unit.getPosition().getX() && game.getLevel()
                .getTiles()[(int) (unit.getPosition().getX() + 1)][(int) (unit.getPosition().getY())] == Tile.WALL) {
            jump = true;
        }
        if (runningPos.getX() < unit.getPosition().getX() && game.getLevel()
                .getTiles()[(int) (unit.getPosition().getX() - 1)][(int) (unit.getPosition().getY())] == Tile.WALL) {
            jump = true;
        }

        runningPos = jumpPadHack(runningPos);

        setJumpAndVelocity(runningPos, jump, action);
        double hitPNew = hitProbability(nearestEnemy, aim);
        double hitPOld = 0.0;
        if (unit.getWeapon() != null && unit.getWeapon().getLastAngle() != null) {
            hitPOld = hitProbability(nearestEnemy, vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0));
        }
        if (hitPNew - hitPOld >= 0.02 || unit.getWeapon() == null) {
            action.setAim(vecUtil.normalize(aim, 10.0));
        } else {
            hitPNew = hitPOld;
            if (unit.getWeapon().getLastAngle() != null) {
                action.setAim(vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0));
            } else {
                action.setAim(vecUtil.normalize(aim, 10.0));
            }

        }
        if (unit.getWeapon() != null && unit.getWeapon().getTyp() == WeaponType.ROCKET_LAUNCHER) {
            if (hitPNew > 0.3) {
                action.setShoot(true);
            }
        } else if (hitPNew > 0.05) {
            action.setShoot(true);
        }

        if (unit.getWeapon() != null && unit.getWeapon().getMagazine() == 0) {
            action.setReload(true);
        }

        action.setPlantMine(nearestEnemy != null && vecUtil.length(vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition())) < game.getProperties().getMineExplosionParams().getRadius());

//        System.out.println("" + game.getCurrentTick() + ":" + unit.getId() + ":" + action+":"+unit.getWeapon());
        return action;
    }

    private void setJumpAndVelocity(Vec2Double runningPos, boolean jump, UnitAction action) {
        action.setVelocity(Math.signum(runningPos.getX() - unit.getPosition().getX()) * game.getProperties().getUnitMaxHorizontalSpeed());
        action.setJump(jump);
        action.setJumpDown(!jump);

        neoAddon(runningPos, action);
    }

    private void neoAddon(Vec2Double runningPos, UnitAction action) {

        if (game.getBullets().length == 0) {
            return;
        }

        List<DummyBullet> bullets = new ArrayList<>();
        for (Bullet bullet : game.getBullets()) {
            if (bullet.getUnitId() != unit.getId()) {
                DummyBullet dummyBullet = new DummyBullet(bullet);
                bullets.add(dummyBullet);
            } else if (bullet.getExplosionParams() != null && bullet.getExplosionParams().getRadius() > 0) {
                DummyBullet dummyBullet = new DummyBullet(bullet);
                bullets.add(dummyBullet);
            }
        }

        if (bullets.size() == 0) {
            return;
        }


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
        Set<Dummy> lastDead = new HashSet<>();
        int lastDeadTick = 0;
        Set<Dummy> survivors = new HashSet<>(dummies);

        for (int tick = 0; tick < 100; tick++) {
            dummies = new HashSet<>(survivors);
            ; // hack optimization
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
                    dummy.moveOneUpdateVerically();
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
        Vec2Double left10 = vecUtil.add(runningPos, new Vec2Double(-1.0, 0.0));
        Vec2Double left05 = vecUtil.add(runningPos, new Vec2Double(-0.5, 0.0));
        Vec2Double right10 = vecUtil.add(runningPos, new Vec2Double(1.0, 0.0));
        Vec2Double right05 = vecUtil.add(runningPos, new Vec2Double(0.5, 0.0));
        if (getTile(left10) == Tile.JUMP_PAD) {
            runningPos = right05;
        } else if (getTile(right10) == Tile.JUMP_PAD) {
            runningPos = left05;
        }
        return runningPos;
    }

    private Vec2Double findMeanAim(Unit enemy) {
        if (unit.getWeapon() == null) {
            return new Vec2Double(0.0, 0.0);
        }
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 3.0 / game.getProperties().getTicksPerSecond()) || unit.getWeapon().getMagazine() == 0) {
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
        Dummy enemyDummy = new Dummy(enemy);
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
                    if (bullet.isHittingTheDummy(enemyDummy)) {
                        hitCount++;
                        hitVector = vecUtil.add(bullet.position, hitVector);
                    }
                    iterator.remove();
                }
            }
            enemyDummy.moveOneUpdateHorizontally();
            enemyDummy.moveOneUpdateVerically();
        }
        if (hitCount == 0) {
            return new Vec2Double(0.0, 0.0);
        }
        return vecUtil.substract(vecUtil.scale(hitVector, 1.0 / (double) hitCount), unitCenter);
    }

    private double hitProbability(Unit enemy, Vec2Double aim) {
        if (unit.getWeapon() == null) {
            return 0.0;
        }
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 3.0 / game.getProperties().getTicksPerSecond()) || unit.getWeapon().getMagazine() == 0) {
            return 0.0;
        }
        Vec2Double unitCenter = vecUtil.getCenter(unit);
        int hitCount = 0;
        int maxHitCount = 51;
        List<DummyBullet> bullets = new ArrayList<>();
        double baseAngle = vecUtil.getAngle(aim);
        int maxSpread = (maxHitCount - 1) / 2;
        for (int i = -maxSpread; i <= maxSpread; i++) {
            Vec2Double adjustedDirection = vecUtil.fromAngle(baseAngle + i / (double) maxSpread * unit.getWeapon().getSpread(), 10.0);
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unit.getWeapon().getParams().getExplosion(), unitCenter, adjustedDirection, unit.getId());
            bullets.add(dummyBullet);
        }
        Dummy enemyDummy = new Dummy(enemy);
        while (bullets.size() > 0) {
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                bullet.moveOneUpdateHorizontally();
                bullet.moveOneUpdateVertically();
                if (bullet.isHittingTheDummy(enemyDummy)) {
                    iterator.remove();
                    hitCount++;
                } else if (bullet.isHittingAWall()) {
                    if (bullet.isHittingTheDummyWithExplosion(enemyDummy)) {
                        hitCount++;
                    }
                    iterator.remove();
                }
            }
            enemyDummy.moveOneUpdateHorizontally();
            enemyDummy.moveOneUpdateVerically();
        }
        return hitCount / (double) maxHitCount;
    }

    private Tile getTile(Vec2Double location) {
        return tiles[(int) location.getX()][(int) location.getY()];
    }

    private LootBox getNearestWeapon(model.WeaponType weaponType) {
        LootBox nearestWeapon = null;
        for (LootBox lootBox : game.getLootBoxes()) {
            if (lootBox.getItem() instanceof Item.Weapon) {
                if (weaponType == null || weaponType == ((Item.Weapon) lootBox.getItem()).getWeaponType()) {
                    if (nearestWeapon == null || distanceSqr(unit.getPosition(),
                            lootBox.getPosition()) < distanceSqr(unit.getPosition(), nearestWeapon.getPosition())) {
                        nearestWeapon = lootBox;
                    }
                }
            }
        }
        return nearestWeapon;
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


        public DummyBullet(Bullet bullet) {
            this.position = vecUtil.clone(bullet.getPosition());
            this.velocity = vecUtil.clone(bullet.getVelocity());
            this.size = bullet.getSize();
            if (bullet.getExplosionParams() != null) {
                this.explosionRadius = bullet.getExplosionParams().getRadius();
            }
            this.unitId = bullet.getUnitId();
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
            return hit;
        }

        public boolean isHittingTheDummy(Dummy dummy) {
            if (dummy.unit.getId() == unitId) {
                return false;
            }
            Vec2Double dummyCenter = vecUtil.getCenter(dummy.getPosition(), dummy.unit.getSize());
            boolean hit = Math.abs(dummyCenter.getX() - position.getX()) <= (dummy.unit.getSize().getX() + size) / 2.0
                    && Math.abs(dummyCenter.getY() - position.getY()) <= (dummy.unit.getSize().getY() + size) / 2.0;

            return hit;
        }

        public boolean isHittingTheDummyWithExplosion(Dummy dummy) {
            Vec2Double dummyCenter = vecUtil.getCenter(dummy.getPosition(), dummy.unit.getSize());
            boolean hit = Math.abs(dummyCenter.getX() - position.getX()) <= (dummy.unit.getSize().getX() / 2.0 + explosionRadius)
                    && Math.abs(dummyCenter.getY() - position.getY()) <= (dummy.unit.getSize().getY() / 2.0 + explosionRadius);

            return hit;
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

        public void moveOneUpdateVerically() {
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
            if (isFalling && (!isStandingPosition(position) || jumpingDown)) {
                Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, -game.getProperties().getUnitFallSpeed() / updatesPerSecond));
                if (isPositionPossible(newPosition)) {
                    position = newPosition;
                }
            }
            if (isStandingPosition(position) && (canCancelJump || canJumpForSeconds < EPSILON)) {
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
            Tile tile00 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, 0.0)));
            Tile tile10 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, 0.0)));
            Tile tile01 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, unit.getSize().getY() / 2.0)));
            Tile tile11 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, unit.getSize().getY() / 2.0)));
            Tile tile02 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, unit.getSize().getY())));
            Tile tile12 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, unit.getSize().getY())));
            return tile00 != Tile.WALL && tile10 != Tile.WALL && tile01 != Tile.WALL && tile11 != Tile.WALL && tile02 != Tile.WALL && tile12 != Tile.WALL;
        }

        public boolean isPositionAffectedByJumpPad(Vec2Double position) {
            Tile tile00 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, 0.0)));
            Tile tile10 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, 0.0)));
            Tile tile01 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, unit.getSize().getY() / 2.0)));
            Tile tile11 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, unit.getSize().getY() / 2.0)));
            Tile tile02 = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, unit.getSize().getY())));
            Tile tile12 = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, unit.getSize().getY())));
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