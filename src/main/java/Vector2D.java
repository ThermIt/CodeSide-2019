import model.Unit;

import static java.lang.StrictMath.PI;
import static java.lang.StrictMath.atan2;

public class Vector2D {
    private double x;
    private double y;

    public Vector2D(double x, double y) {
        set(x, y);
    }

    public Vector2D(Unit unit) {
        x = unit.getPosition().getX();
        y = unit.getPosition().getY();
    }

    public Vector2D(Vector2D location) {
        x = location.x;
        y = location.y;
    }

    public static Vector2D fromAngle(double angle, double length) {
        double x = StrictMath.cos(angle) * length;
        double y = StrictMath.sin(angle) * length;
        return new Vector2D(x, y);
    }

    public static double getDistanceTo(double x, double y, Unit unit) {
        return StrictMath.hypot(unit.getPosition().getX() - x, unit.getPosition().getY() - y);
    }

    public static Vector2D add(Vector2D vector1, Vector2D vector2) {
        return new Vector2D(vector1.getX() + vector2.getX(), vector1.getY() + vector2.getY());
    }

    public static double angleBetween(Vector2D vector1, Vector2D vector2) {
        double d = (vector1.getX() * vector2.getX() + vector1.getY() * vector2.getY()) / (vector1.getLength() * vector2.getLength());
        return StrictMath.acos(d) / Math.PI * 180.0;
    }

    public static double crossProduct(Vector2D vector1, Vector2D vector2) {
        return vector1.getX() * vector2.getY() - vector1.getY() * vector2.getX();
    }

    public static double determinant(Vector2D vector1, Vector2D vector2) {
        return vector1.getX() * vector2.getY() - vector1.getY() * vector2.getX();
    }

    public static Vector2D divide(Vector2D vector, double scalar) {
        return new Vector2D(vector.getX() / scalar, vector.getY() / scalar);
    }

    public static Vector2D multiply(double scalar, Vector2D vector) {
        return new Vector2D(scalar * vector.getX(), scalar * vector.getY());
    }

    public static Vector2D multiply(Vector2D vector, double scalar) {
        return new Vector2D(scalar * vector.getX(), scalar * vector.getY());
    }

    public static double multiply(Vector2D vector1, Vector2D vector2) {
        return vector1.getX() * vector2.getX() + vector1.getY() * vector2.getY();
    }

    public static Vector2D subtract(Vector2D vector1, Vector2D vector2) {
        return new Vector2D(vector1.getX() - vector2.getX(), vector1.getY() - vector2.getY());
    }

    public void add(Vector2D vector) {
        x += vector.x;
        y += vector.y;
    }

    public void multiply(double scalar) {
        x *= scalar;
        y *= scalar;
    }

    public void subtract(Vector2D vector) {
        this.x -= vector.x;
        this.y -= vector.y;
    }

    public void set(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ignored) {
            return null;
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getDistanceTo(double x, double y) {
        return StrictMath.hypot(this.x - x, this.y - y);
    }

    public double getDistanceTo(Vector2D point) {
        return getDistanceTo(point.x, point.y);
    }

    public double getDistanceTo(Unit unit) {
        return getDistanceTo(unit.getPosition().getX(), unit.getPosition().getY());
    }

    public double getAngle() {
        double relativeAngleTo = atan2(y, x);

        while (relativeAngleTo > PI) {
            relativeAngleTo -= 2.0D * PI;
        }

        while (relativeAngleTo < -PI) {
            relativeAngleTo += 2.0D * PI;
        }

        return relativeAngleTo;
    }

    public double getLength() {
        return Math.hypot(getX(), getY());
    }

    public double getLengthSquared() {
        return this.x * this.x + this.y * this.y;
    }

    public void negate() {
        this.x = -this.x;
        this.y = -this.y;
    }

    public void normalize() {
        double length = this.getLength();
        if (length == 1.0D) {
            return;
        }
        this.x /= length;
        this.y /= length;
    }

    @Override
    public String toString() {
        return '{' +
                "x=" + x +
                ", y=" + y +
                '}';
    }

    public boolean isCloserThan(Unit unit, double radius) {
        if (unit == null) {
            return false;
        }
        return Math.abs(unit.getPosition().getX() - x) < radius
                && Math.abs(unit.getPosition().getY() - y) < radius
                && getDistanceTo(unit) < radius;
    }

    public boolean isCloserThan(Vector2D unit, double radius) {
        if (unit == null) {
            return false;
        }
        return Math.abs(unit.getX() - x) < radius
                && Math.abs(unit.getY() - y) < radius
                && unit.getDistanceTo(x, y) < radius;
    }
}
