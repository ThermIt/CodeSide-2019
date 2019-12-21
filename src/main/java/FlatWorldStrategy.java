import model.*;
import util.Debug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class FlatWorldStrategy {
    private Game game;
    private Debug debug;
    private Tile[][] tiles;
    private Player me;
    private Player enemy;

    private Set<Unit> myUnits = new HashSet<>();
    private Set<Unit> enemyUnits = new HashSet<>();
    private HashMap<Integer, Unit> myUnitsById = new HashMap<>();
    private HashMap<Integer, Order> ordersById = new HashMap<>();


    public void UpdateTick(PlayerView playerView, Debug debug) {
        this.game = playerView.getGame();
        this.debug = debug;
        this.tiles = game.getLevel().getTiles();
        if (game.getPlayers()[0].getId() == playerView.getMyId()) {
            me = game.getPlayers()[0];
            enemy = game.getPlayers()[1];
        } else {
            me = game.getPlayers()[1];
            enemy = game.getPlayers()[0];
        }

        myUnits.clear();
        enemyUnits.clear();
        for (model.Unit unit : playerView.getGame().getUnits()) {
            if (unit.getPlayerId() == playerView.getMyId()) {
                myUnits.add(unit);
            } else {
                enemyUnits.add(unit);
            }
        }
    }


    public Set<Unit> getMyUnits() {
        return myUnits;
    }

    public Set<Unit> getEnemyUnits() {
        return enemyUnits;
    }

    public HashMap<Integer, Unit> getMyUnitsById() {
        return myUnitsById;
    }

    public HashMap<Integer, Order> getOrdersById() {
        return ordersById;
    }

    public Player getMe() {
        return me;
    }

    public Player getEnemy() {
        return enemy;
    }
}
