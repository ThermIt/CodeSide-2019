package older;

import model.*;
import util.Debug;
import util.Strategy;
import util.VectorUtils;

public class MyOlderStrategy implements Strategy {

    VectorUtils vecUtil = new VectorUtils();
    private Unit unit;
    private Game game;
    private Debug debug;

    static double distanceSqr(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    @Override
    public UnitAction getAction(Unit unit, Game game, Debug debug) {
        this.unit = unit;
        this.game = game;
        this.debug = debug;
/*        Tile[][] tiles = game.getLevel().getTiles();
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                ColoredVertex[] vertices = new ColoredVertex[4];
                vertices[0] = new ColoredVertex(new Vec2Float(i, j), new ColorFloat(1, 0, 0, 1));
                vertices[1] = new ColoredVertex(new Vec2Float(i + 0.5f, j), new ColorFloat(0, 1, 0, 1));
                vertices[2] = new ColoredVertex(new Vec2Float(i + 0.5f, j + 0.5f), new ColorFloat(1, 0, 0, 1));
                vertices[3] = new ColoredVertex(new Vec2Float(i, j + 0.5f), new ColorFloat(0, 0, 1, 1));
                debug.draw(new CustomData.Polygon(vertices));
                debug.draw(new CustomData.PlacedText(tiles[i][j].toString() + " " + i + " " + j, new Vec2Float(i, j), TextAlignment.CENTER, 10, new ColorFloat(1, 1, 1, 1)));
            }
        }
        ColoredVertex[] vertices = new ColoredVertex[4];
        vertices[0] = new ColoredVertex(vecUtil.fromVec2Double(unit.getPosition()), new ColorFloat(1, 0, 0, 1));
        vertices[1] = new ColoredVertex(vecUtil.fromVec2Double(unit.getPosition(), unit.getSize().getX() / 2.0, 0.0), new ColorFloat(0, 1, 0, 1));
        vertices[2] = new ColoredVertex(vecUtil.fromVec2Double(unit.getPosition(), unit.getSize().getX() / 2.0, unit.getSize().getY() / 2.0), new ColorFloat(1, 0, 0, 1));
        vertices[3] = new ColoredVertex(vecUtil.fromVec2Double(unit.getPosition(), 0.0, unit.getSize().getY() / 2.0), new ColorFloat(0, 0, 1, 1));
        debug.draw(new CustomData.Polygon(vertices));*/


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
                debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(location),
                        0.05f, new ColorFloat(1, 0, 0, 1)));
                return false;
            }
        }

        debug.draw(new CustomData.Line(vecUtil.toFloatVector(unitCenter), vecUtil.toFloatVector(enemyCenter),
                0.05f, new ColorFloat(0, 1, 0, 1)));
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
}