package com.epicorp.joel.potholer;

/**
 * Created by Joel on 9/15/2015.
 */

public class AccelData {
    private Long timestamp;
    private double x;
    private double y;
    private double z;
    private double lat;
    private double lng;
    private long id;
    private double accel;
    private double severity;

    public AccelData(Long timestamp, double x, double y, double z, double lat,
                     double lng) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lat = lat;
        this.lng = lng;
        this.severity = Math.sqrt(Math.abs(x * y * z));
    }

    public AccelData(Long timestamp, double accel, double lat, double lng) {
        this.timestamp = timestamp;
        this.accel = accel;
        this.lat = lat;
        this.lng = lng;

        this.x = -1;
        this.y = -1;
        this.z = -1;
        this.severity = -1;
    }

    public AccelData(double severity, double lat, double lng, Long id) {
        this.timestamp = 0L;
        this.accel = 0;
        this.id = id;
        this.lat = lat;
        this.lng = lng;

        this.x = -1;
        this.y = -1;
        this.z = -1;
        this.severity = severity;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getAccel() {
        if (this.x == -1)
            return (Math.sqrt(x * x + y * y + z * z));
        else
            return this.accel;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
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

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String toString() {
        return "t=" + timestamp + ", x=" + x + ", y=" + y + ", z=" + z
                + ", lat=" + lat + ", lng=" + lng + "severity=" + severity;
    }

    public double getSeverity() {
        return severity;
    }
}