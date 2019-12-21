package util;

import model.PlayerView;
import model.UnitAction;

import java.util.Map;

public interface Strategy {
    Map<Integer, UnitAction> getAllActions(PlayerView playerView, Debug debug);
}
