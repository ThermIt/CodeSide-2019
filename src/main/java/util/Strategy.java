package util;

import model.Game;
import model.Unit;
import model.UnitAction;

public interface Strategy {
    UnitAction getAction(Unit unit, Game game, Debug debug);
}
