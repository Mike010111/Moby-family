package by.moby.family.members;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/** Последняя известная точка ребёнка на радаре (x/y — доли диаметра, центр — дом). */
@Embeddable
public class Geo {

    @Column(name = "geo_place")
    private String place;
    @Column(name = "geo_x")
    private double x;
    @Column(name = "geo_y")
    private double y;
    @Column(name = "geo_distance")
    private double distance;
    @Column(name = "geo_updated_at")
    private Instant updatedAt;

    protected Geo() {
    }

    public Geo(String place, double x, double y, double distance, Instant updatedAt) {
        this.place = place;
        this.x = x;
        this.y = y;
        this.distance = distance;
        this.updatedAt = updatedAt;
    }

    public String getPlace() {
        return place;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getDistance() {
        return distance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
