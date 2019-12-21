package older;

import model.*;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

import java.util.HashMap;
import java.util.Map;

public class MySmartGuy implements Strategy {

    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;

    static double distanceSqr(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    public UnitAction getAction(Unit unit, Game game, Debug debug) {
        this.unit = unit;
        this.game = game;

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
            if (unit.getHealth() < game.getProperties().getUnitMaxHealth()) {
                LootBox nearestHealthPack = getNearestHealthPack();
                if (nearestHealthPack != null) {
                    runningPos = nearestHealthPack.getPosition();
                }
            }
        }
        Vec2Double aim = new Vec2Double(0, 0);
        if (nearestEnemy != null) {
            aim = vecUtil.substract(nearestEnemy.getPosition(), unit.getPosition());
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
        UnitAction action = new UnitAction();
        action.setVelocity(Math.signum(runningPos.getX() - unit.getPosition().getX()) * game.getProperties().getUnitMaxHorizontalSpeed());
        action.setJump(jump);
        action.setJumpDown(!jump);
        action.setAim(aim);
        if (isInSight(unit, game, aim)) {
            action.setShoot(true);
        }
        if (unit.getWeapon() != null && unit.getWeapon().getMagazine() == 0) {
            action.setReload(true);
        }
        action.setSwapWeapon(false);
        action.setPlantMine(false);
        return action;
    }


    private boolean isInSight(Unit unit, Game game, Vec2Double aim) {
        Vec2Double vector = vecUtil.normalize(aim, 0.05);
        Vec2Double unitCenter = vecUtil.getCenter(unit);
        Vec2Double enemyCenter = vecUtil.add(unitCenter, aim);
        for (Vec2Double location = unitCenter; distanceSqr(location, enemyCenter) > 0.1; location = vecUtil.add(location, vector)) {

            if (getTile(location, game) == Tile.WALL) {
                return false;
            }
        }

        return true;
    }

    private Tile getTile(Vec2Double location, Game game) {
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

    @Override
    public Map<Integer, UnitAction> getAllActions(PlayerView playerView, Debug debug) {
        Map<Integer, UnitAction> actions = new HashMap<>();
        for (model.Unit unit : playerView.getGame().getUnits()) {
            if (unit.getPlayerId() == playerView.getMyId()) {
                actions.put(unit.getId(), getAction(unit, playerView.getGame(), debug));
            }
        }
        return actions;
    }
}