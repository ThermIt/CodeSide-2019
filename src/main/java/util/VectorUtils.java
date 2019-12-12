package util;

import model.Unit;
import model.Vec2Double;
import model.Vec2Float;

import static java.lang.StrictMath.PI;
import static java.lang.StrictMath.atan2;

public class VectorUtils {

    public Vec2Double clone(Vec2Double from) {
        return new Vec2Double(from.getX(), from.getY());
    }

    public Vec2Float toFloatVector(Vec2Double from) {
        return new Vec2Float((float) from.getX(), (float) from.getY());
    }

    public Vec2Float toFloatVector(Vec2Double from, Vec2Double plus, float scale) {
        return new Vec2Float((float) (from.getX() + plus.getX() * scale), (float) (from.getY() + plus.getY() * scale));
    }

    public Vec2Float toFloatVector(Vec2Double from, double plusX, double plusY) {
        return new Vec2Float((float) (from.getX() + plusX), (float) (from.getY() + plusY));
    }

    public Vec2Double scale(Vec2Double size, double scale) {
        return new Vec2Double(size.getX() * scale, size.getY() * scale);
    }

    public Vec2Double removeX(Vec2Double vector) {
        return new Vec2Double(0.0, vector.getY());
    }

    public Vec2Double removeY(Vec2Double vector) {
        return new Vec2Double(vector.getX(), 0.0);
    }

    public Vec2Double add(Vec2Double vector1, Vec2Double vector2) {
        return new Vec2Double(vector1.getX() + vector2.getX(), vector1.getY() + vector2.getY());
    }

    public Vec2Double substract(Vec2Double vector1, Vec2Double vector2) {
        return new Vec2Double(vector1.getX() - vector2.getX(), vector1.getY() - vector2.getY());
    }

    public Vec2Double normalize(Vec2Double vector, double newLength) {
        double length = length(vector);
        if (length == 0) {
            return vector;
        }
        return new Vec2Double(vector.getX() / length * newLength, vector.getY() / length * newLength);
    }

    public double length(Vec2Double vector) {
        return Math.sqrt(vector.getX() * vector.getX() + vector.getY() * vector.getY());
    }

    public Vec2Double getCenter(Unit unit) {
        return add(unit.getPosition(), removeX(scale(unit.getSize(), 0.5)));
    }

    public Vec2Double getCenter(Vec2Double position, Vec2Double size) {
        return add(position, removeX(scale(size, 0.5)));
    }

    public double getAngle(Vec2Double vector) {
        double relativeAngleTo = atan2(vector.getY(), vector.getX());

        while (relativeAngleTo > PI) {
            relativeAngleTo -= 2.0D * PI;
        }

        while (relativeAngleTo < -PI) {
            relativeAngleTo += 2.0D * PI;
        }

        return relativeAngleTo;
    }

    public Vec2Double fromAngle(double angle, double length) {
        double x = StrictMath.cos(angle) * length;
        double y = StrictMath.sin(angle) * length;
        return new Vec2Double(x, y);
    }

}
