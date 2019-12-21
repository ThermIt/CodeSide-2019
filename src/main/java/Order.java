import model.Unit;
import model.Vec2Double;

public class Order {
    Unit targetEnemy;
    Vec2Double runTo;

    public Unit getTargetEnemy() {
        return targetEnemy;
    }

    public void setTargetEnemy(Unit targetEnemy) {
        this.targetEnemy = targetEnemy;
    }

    public Vec2Double getRunTo() {
        return runTo;
    }

    public void setRunTo(Vec2Double runTo) {
        this.runTo = runTo;
    }
}
