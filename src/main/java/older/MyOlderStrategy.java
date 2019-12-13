package older;

import model.*;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MyOlderStrategy implements Strategy {

    public static final double HEALTH_TO_LOOK_FOR_HEAL = 0.90;
    private static final double EPSILON = 0.000000000001;
    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;
    private double updatesPerSecond;
    private double halfUnitSizeX;

    static double distanceSqr(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    @Override
    public UnitAction getAction(Unit unit, Game game, Debug debug) {
        this.unit = unit;
        this.game = game;
        this.updatesPerSecond = game.getProperties().getTicksPerSecond() * (double) game.getProperties().getUpdatesPerTick();
        this.halfUnitSizeX = game.getProperties().getUnitSize().getX() / 2.0;

        Unit nearestEnemy = getNearestEnemy(unit, game);
        LootBox nearestWeapon = getNearestWeapon(unit, game);
        Vec2Double targetPos = unit.getPosition();
        Vec2Double runningPos = unit.getPosition();
        if (unit.getWeapon() == null && nearestWeapon != null) {
            targetPos = nearestWeapon.getPosition();
            runningPos = targetPos;
        } else if (nearestEnemy != null) {
            targetPos = nearestEnemy.getPosition();
            runningPos = targetPos;
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

        // hack jumppad
        Vec2Double left10 = vecUtil.add(runningPos, new Vec2Double(-1.0, 0.0));
        Vec2Double left05 = vecUtil.add(runningPos, new Vec2Double(-0.5, 0.0));
        Vec2Double right10 = vecUtil.add(runningPos, new Vec2Double(1.0, 0.0));
        Vec2Double right05 = vecUtil.add(runningPos, new Vec2Double(0.5, 0.0));
        if (getTile(left10) == Tile.JUMP_PAD) {
            runningPos = right05;
        } else if (getTile(right10) == Tile.JUMP_PAD) {
            runningPos = left05;
        }

        UnitAction action = new UnitAction();
        action.setVelocity(Math.signum(runningPos.getX() - unit.getPosition().getX()) * game.getProperties().getUnitMaxHorizontalSpeed());
        action.setJump(jump);
        action.setJumpDown(!jump);
        double hitPNew = hitProbability(nearestEnemy, aim);
        double hitPOld = 0.0;
        if (unit.getWeapon() != null) {
            hitPOld = hitProbability(nearestEnemy, vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0));
        }
        if (hitPNew >= hitPOld || unit.getWeapon() == null) {
            action.setAim(vecUtil.normalize(aim, 1.0));
        } else {
            hitPNew = hitPOld;
            action.setAim(vecUtil.fromAngle(unit.getWeapon().getLastAngle(), 10.0));
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
        action.setSwapWeapon(false);

        action.setPlantMine(nearestEnemy != null && vecUtil.length(vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition())) < game.getProperties().getMineExplosionParams().getRadius());

        return action;
    }

    private Vec2Double findMeanAim(Unit enemy) {
        if (unit.getWeapon() == null) {
            return new Vec2Double(0.0, 0.0);
        }
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 1.0) || unit.getWeapon().getMagazine() == 0) {
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
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unitCenter, adjustedDirection);
            bullets.add(dummyBullet);
        }
        Dummy enemyDummy = new Dummy(enemy);
        Vec2Double hitVector = new Vec2Double(0, 0);
        while (bullets.size() > 0) {
            enemyDummy.moveOneUpdate();
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                bullet.moveOneUpdate();
                if (bullet.isHittingTheDummy(enemyDummy)) {
                    iterator.remove();
                    hitCount++;
                    hitVector = vecUtil.add(bullet.position, hitVector);
                } else if (bullet.isHittingAWall()) {
                    iterator.remove();
                }
            }
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
        if ((unit.getWeapon().getFireTimer() != null && unit.getWeapon().getFireTimer() > 1.0) || unit.getWeapon().getMagazine() == 0) {
            return 0.0;
        }
        Vec2Double unitCenter = vecUtil.getCenter(unit);
        int hitCount = 0;
        int maxHitCount = 51;
        List<DummyBullet> bullets = new ArrayList<>();
        Vec2Double direction = aim;
        double baseAngle = vecUtil.getAngle(direction);
        int maxSpread = (maxHitCount - 1) / 2;
        for (int i = -maxSpread; i <= maxSpread; i++) {
            Vec2Double adjustedDirection = vecUtil.fromAngle(baseAngle + i / (double) maxSpread * unit.getWeapon().getSpread(), 10.0);
            DummyBullet dummyBullet = new DummyBullet(unit.getWeapon().getParams().getBullet(), unitCenter, adjustedDirection);
            bullets.add(dummyBullet);
        }
        Dummy enemyDummy = new Dummy(enemy);
        while (bullets.size() > 0) {
            enemyDummy.moveOneUpdate();
            for (Iterator<DummyBullet> iterator = bullets.iterator(); iterator.hasNext(); ) {
                DummyBullet bullet = iterator.next();
                bullet.moveOneUpdate();
                if (bullet.isHittingTheDummy(enemyDummy)) {
                    iterator.remove();
                    hitCount++;
                } else if (bullet.isHittingAWall()) {
                    iterator.remove();
                }
            }
        }
        return hitCount / (double) maxHitCount;
    }

    private Tile getTile(Vec2Double location) {
        return game.getLevel().getTiles()[(int) Math.floor(location.getX())][(int) Math.floor(location.getY())];
    }

    private LootBox getNearestWeapon(Unit unit, Game game) {
        LootBox nearestWeapon = null;
        for (LootBox lootBox : game.getLootBoxes()) {
            if (lootBox.getItem() instanceof Item.Weapon) {
                if (nearestWeapon == null || distanceSqr(unit.getPosition(),
                        lootBox.getPosition()) < distanceSqr(unit.getPosition(), nearestWeapon.getPosition())) {
                    nearestWeapon = lootBox;
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

    private Unit getNearestEnemy(Unit unit, Game game) {
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

    class DummyBullet {
        private Vec2Double velocity;
        private Vec2Double position;
        private double size;

        public DummyBullet(BulletParams bulletParams, Vec2Double position, Vec2Double direction) {
            this.position = vecUtil.clone(position);
            this.velocity = vecUtil.normalize(direction, bulletParams.getSpeed());
            this.size = bulletParams.getSize();
        }

        public void moveOneUpdate() {
            position.setX(position.getX() + velocity.getX() / updatesPerSecond);
            position.setY(position.getY() + velocity.getY() / updatesPerSecond);
        }

        public boolean isHittingAWall() {
            int xMinusSize = (int) Math.floor(position.getX() - size / 2.0);
            int xPlusSize = (int) Math.floor(position.getX() + size / 2.0);
            int yMinusSize = (int) Math.floor(position.getY() - size / 2.0);
            int yPlusSize = (int) Math.floor(position.getY() + size / 2.0);
            Tile tile1 = game.getLevel().getTiles()[xMinusSize][yMinusSize];
            Tile tile2 = game.getLevel().getTiles()[xPlusSize][yMinusSize];
            Tile tile3 = game.getLevel().getTiles()[xMinusSize][yPlusSize];
            Tile tile4 = game.getLevel().getTiles()[xPlusSize][yPlusSize];
            return tile1 == Tile.WALL || tile2 == Tile.WALL || tile3 == Tile.WALL || tile4 == Tile.WALL;
        }

        public boolean isHittingTheDummy(Dummy dummy) {
            Vec2Double dummyCenter = vecUtil.getCenter(dummy.getPosition(), dummy.unit.getSize());
            boolean hit = Math.abs(dummyCenter.getX() - position.getX()) <= (dummy.unit.getSize().getX() + size) / 2.0
                    && Math.abs(dummyCenter.getY() - position.getY()) <= (dummy.unit.getSize().getY() + size) / 2.0;

            return hit;
        }
    }

    class Dummy {
        private Vec2Double position;
        private Unit unit;
        private double canJumpForSeconds;
        private double jumpSpeed;
        private boolean canCancelJump;
        private long microTick = 0;

        public Dummy(Unit unit) {
            this.position = vecUtil.clone(unit.getPosition());
            this.unit = unit;
            JumpState jumpState = this.unit.getJumpState();
            this.canJumpForSeconds = jumpState.getMaxTime();
            this.jumpSpeed = jumpState.getSpeed();
            this.canCancelJump = jumpState.isCanCancel();
        }

        public Vec2Double getPosition() {
            return position;
        }

        public void moveOneUpdate() {
            microTick++;
            boolean canChangeOrder = microTick == game.getProperties().getUpdatesPerTick();
            if (canChangeOrder) {
                microTick = 0;
            }
            // horizontal
            // vertical
            boolean isJumping = false;
            if (canJumpForSeconds > 0.0) {
                if (!canCancelJump) {
                    Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, jumpSpeed / updatesPerSecond));
                    if (isPositionPossible(newPosition)) {
                        position = newPosition;
                        isJumping = true;
                    } else {
                        canCancelJump = true;
                        canJumpForSeconds = 0.0;
                        jumpSpeed = 0.0;
                    }
                }
                canJumpForSeconds -= 1.0 / updatesPerSecond;
            }
            if (!isJumping && !isStandingPosition(position)) {
                Vec2Double newPosition = vecUtil.add(position, new Vec2Double(0, -game.getProperties().getUnitFallSpeed() / updatesPerSecond));
                if (isPositionPossible(newPosition)) {
                    position = newPosition;
                }
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

        public boolean isPositionAffectedByLadder(Vec2Double position) {
            Tile centalTile = getTile(vecUtil.getCenter(position, unit.getSize()));
            Tile tileUnderCenter = getTile(vecUtil.add(position, new Vec2Double(0.0, -EPSILON)));
            return centalTile == Tile.LADDER || tileUnderCenter == Tile.LADDER;
        }

        public boolean isStandingPosition(Vec2Double position) {
            if (isPositionAffectedByLadder(position)) {
                return true;
            }

            boolean sameVerticalTile = (0 == ((int) Math.floor(position.getY()) - (int) Math.floor(position.getY() - game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            Tile tile0u = getTile(vecUtil.add(position, new Vec2Double(-halfUnitSizeX, -game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            Tile tile1u = getTile(vecUtil.add(position, new Vec2Double(+halfUnitSizeX, -game.getProperties().getUnitFallSpeed() / updatesPerSecond)));
            return !sameVerticalTile && (tile0u == Tile.WALL || tile1u == Tile.WALL || tile0u == Tile.PLATFORM || tile1u == Tile.PLATFORM);
        }
    }
}