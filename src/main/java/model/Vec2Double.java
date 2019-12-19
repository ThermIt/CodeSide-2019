package model;

import util.StreamUtil;

import java.util.Objects;

public class Vec2Double {
    private double x;
    private double y;

    public Vec2Double() {
    }

    public Vec2Double(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Vec2Double readFrom(java.io.InputStream stream) throws java.io.IOException {
        Vec2Double result = new Vec2Double();
        result.x = StreamUtil.readDouble(stream);
        result.y = StreamUtil.readDouble(stream);
        return result;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public Vec2Float toFloatVector() {
        return new Vec2Float((float) x, (float) y);
    }

    public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
        StreamUtil.writeDouble(stream, x);
        StreamUtil.writeDouble(stream, y);
    }

    @Override
    public String toString() {
        return "Vec2Double{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Vec2Double that = (Vec2Double) o;
        return Double.compare(that.x, x) == 0 &&
                Double.compare(that.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
