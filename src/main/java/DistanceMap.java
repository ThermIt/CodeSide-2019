import model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DistanceMap {
    public static final int SCALE = 3;
    private int[][] distanceMap;
    private int[][] distanceMapFromTarget;
    private int[][] distanceMapFromHealth;
    private int healthCount = -1;
    private int[][] distanceMapFromMine;
    private int mineCount = -1;
    private Game game;
    private Unit unit;
    private int sizeX;
    private int sizeY;
    private Tile[][] tiles;
    private FlatWorldStrategy strat;

    public DistanceMap(Tile[][] tiles, FlatWorldStrategy strat) {
        sizeX = tiles.length * SCALE;
        sizeY = tiles[0].length * SCALE;
        this.tiles = tiles;
        this.strat = strat;
    }

    public void tickUpdate(Game game, Unit unit) {
        this.game = game;
        this.unit = unit;
        this.distanceMap = new int[sizeX][sizeY];
        fillDistances(distanceMap, unit.getPosition());
    }

    public void updateTarget(TargetType type, Vec2Double target, Game game) {
        switch (type) {
            case HEALTH:
                updateHealthMap(game);
                distanceMapFromTarget = distanceMapFromHealth;
                break;
            case MINE:
                updateMineMap(game);
                distanceMapFromTarget = distanceMapFromMine;
                break;
            case EMPTY:
            default:
                distanceMapFromTarget = new int[sizeX][sizeY];
                if (target != null) {
                    fillDistances(distanceMapFromTarget, target);
                }
        }
    }

    private void fillDistances(int[][] distanceMap, Vec2Double position) {
        List<Coordinate> coordinateList = new ArrayList<>();
        coordinateList.add(new Coordinate((int) (position.getX() * SCALE), (int) (position.getY() * SCALE)));
        fillDistances(distanceMap, coordinateList);
    }

    private void fillDistances(int[][] distanceMap, List<Coordinate> coordinateList) {
        for (int i = 1; !coordinateList.isEmpty(); i++) {
            List<Coordinate> coordinateListNext = new ArrayList<>();
            for (Coordinate coordinate : coordinateList) {
                if (coordinate.getX() >= 0 && coordinate.getX() < sizeX
                        && coordinate.getY() >= 0 && coordinate.getY() < sizeY
                        && distanceMap[coordinate.getX()][coordinate.getY()] == 0
                        && tiles[coordinate.getX() / SCALE][coordinate.getY() / SCALE] != Tile.WALL
                        && coordinate.getY() < sizeY - 1 && tiles[coordinate.getX() / SCALE][1 + coordinate.getY() / SCALE] != Tile.WALL) {
                    distanceMap[coordinate.getX()][coordinate.getY()] = i;
                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() - 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() + 0));
                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() + 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 0, coordinate.getY() + 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 0, coordinate.getY() - 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() - 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() + 0));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() + 1));
                }
            }
            coordinateList = coordinateListNext;
        }
    }

    public double getDistance(Vec2Double to) {
        return distanceMap[(int) (to.getX() * SCALE)][(int) (to.getY() * SCALE)];
    }

    public double getDistanceFromTarget(Vec2Double to) {
        return distanceMapFromTarget[(int) (to.getX() * SCALE)][(int) (to.getY() * SCALE)];
    }

    private void updateHealthMap(Game game) {
        List<LootBox> healthBoxes = Arrays.stream(game.getLootBoxes()).filter(box -> box.getItem() instanceof Item.HealthPack).collect(Collectors.toList());
        int count = healthBoxes.size();
        if (count != healthCount) {
            distanceMapFromHealth = new int[sizeX][sizeY];
            List<Coordinate> coordinates = healthBoxes.stream().map(box -> new Coordinate(box.getPosition())).collect(Collectors.toList());
            fillDistances(distanceMapFromHealth, coordinates);
            healthCount = count;
        }
    }

    private void updateMineMap(Game game) {
        List<LootBox> healthBoxes = Arrays.stream(game.getLootBoxes()).filter(box -> box.getItem() instanceof Item.Mine).collect(Collectors.toList());
        int count = healthBoxes.size();
        if (count != mineCount) {
            distanceMapFromMine = new int[sizeX][sizeY];
            List<Coordinate> coordinates = healthBoxes.stream().map(box -> new Coordinate(box.getPosition())).collect(Collectors.toList());
            fillDistances(distanceMapFromMine, coordinates);
            mineCount = count;
        }
    }

    public enum TargetType {
        EMPTY,
        MINE,
        HEALTH
    }

    private class Coordinate {
        int x;
        int y;

        public Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Coordinate(Vec2Double position) {
            this((int) (position.getX() * SCALE), (int) (position.getY() * SCALE));
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
