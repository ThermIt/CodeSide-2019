import model.Game;
import model.Player;
import model.PlayerView;
import model.Tile;
import util.Debug;

public class FlatWorldStrategy {
    private Game game;
    private Debug debug;
    private Tile[][] tiles;
    private Player me;
    private Player enemy;

    public FlatWorldStrategy() {
    }

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
    }


}
