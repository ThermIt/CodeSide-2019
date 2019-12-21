import model.Game;
import model.Tile;
import model.Unit;
import model.Vec2Double;

import java.util.ArrayList;
import java.util.List;

public class DistanceMap {

    private int[][] negativeWeight;
    private int[][] distanceMap;
    private int[][] distanceMapFromTarget;
    private Game game;
    private Unit unit;
    private int sizeX;
    private int sizeY;
    private Tile[][] tiles;
    private FlatWorldStrategy strat;

    public DistanceMap(Tile[][] tiles, Game game, Unit unit, FlatWorldStrategy strat) {
        sizeX = tiles.length;
        sizeY = tiles[0].length;
        this.tiles = tiles;
        this.strat = strat;
        distanceMap = new int[sizeX][sizeY];
        negativeWeight = new int[sizeX][sizeY];
        this.game = game;
        this.unit = unit;
        for (Unit enemyUnit : strat.getEnemyUnits()) {
            int x = (int) enemyUnit.getPosition().getX();
            int y = (int) enemyUnit.getPosition().getY() + 1;
            for (int i = -3; i <= 3; i++) {
                for (int j = 3; j <= 3; j++) {
                    int ix = i + x;
                    int iy = i + y;
                    if (ix >= 0 && ix < sizeX && iy >= 0 && iy < sizeY) {
                        negativeWeight[ix][iy] += 20 - Math.abs(i)*2 - Math.abs(j)*2;
                    }
                }
            }
        }
        fillDistances(distanceMap, unit.getPosition());
    }

    public void updateTarget(Vec2Double target) {
        distanceMapFromTarget = new int[sizeX][sizeY];
        if (target != null) {
            fillDistances(distanceMapFromTarget, target);
        }
    }

    private void fillDistances(int[][] distanceMap, Vec2Double position) {
        List<Coordinate> coordinateList = new ArrayList<>();
        coordinateList.add(new Coordinate((int) position.getX(), (int) position.getY()));
        for (int i = 1; !coordinateList.isEmpty(); i++) {
            List<Coordinate> coordinateListNext = new ArrayList<>();
            for (Coordinate coordinate : coordinateList) {
                if (coordinate.getX() >= 0 && coordinate.getX() < sizeX
                        && coordinate.getY() >= 0 && coordinate.getY() < sizeY
                        && distanceMap[coordinate.getX()][coordinate.getY()] == 0
                        && tiles[coordinate.getX()][coordinate.getY()] != Tile.WALL) {
                    distanceMap[coordinate.getX()][coordinate.getY()] = i;
//                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() - 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() + 0));
//                    coordinateListNext.add(new Coordinate(coordinate.getX() - 1, coordinate.getY() + 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 0, coordinate.getY() + 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 0, coordinate.getY() - 1));
//                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() - 1));
                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() + 0));
//                    coordinateListNext.add(new Coordinate(coordinate.getX() + 1, coordinate.getY() + 1));
                }
            }
            coordinateList = coordinateListNext;
        }
    }

    public double getDistance(Vec2Double to) {
        return distanceMap[(int) to.getX()][(int) to.getY()];
    }

    public double getDistanceFromTarget(Vec2Double to) {
        int x = (int) to.getX();
        int y = (int) to.getY();
        return distanceMapFromTarget[x][y];
    }

    private class Coordinate {
        int x;
        int y;

        public Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
