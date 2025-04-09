
import battleship.BattleShip2;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;


public class ShipStatus {
    private boolean sunk;
    private ArrayList<Point> hitCoordinates;
    private int size;
    private orientation shipOrientation ;
    public ShipStatus(int size) {
        this.sunk = false;
//        this.hitCoordinates = hitCoordinates;
        this.size = size;
    }

    public boolean isSunk() {
        return sunk;
    }
    public int getSize() {return size;}

    public void setSunk(boolean sunk) {
        this.sunk = sunk;
    }

    public int getSunkLength() {
        if (sunk) return hitCoordinates.size();
        else return -1;
    }

    public ArrayList<Point> getHitCoordinates() {
        return hitCoordinates;
    }

    public void setHitCoordinates(ArrayList<Point> hitCoordinates) {
        this.hitCoordinates = hitCoordinates;
    }

    public void addCoordinate(Point p) {
        hitCoordinates.add(p);
    }

    public Point getCoordinate(int i) {
        return hitCoordinates.get(i);
    }

    public void setShipOrientation(orientation shipOrientation) {
        this.shipOrientation = shipOrientation;
    }

    public ArrayList<Point> getNeighbors() {
        ArrayList<Point> neighbors = new ArrayList<>();
        HashSet<Point> shipCells = new HashSet<>(hitCoordinates); // Avoid duplicates
        int[] dx = {0, 1, 0, -1}; // right, down, left, up
        int[] dy = {1, 0, -1, 0};

        for (Point cell : hitCoordinates) {
            for (int i = 0; i < 4; i++) {
                int newX = cell.x + dx[i];
                int newY = cell.y + dy[i];
                Point neighbor = new Point(newX, newY);

                if (isValid(neighbor) && !shipCells.contains(neighbor)) {
                    neighbors.add(neighbor);
                }
            }
        }

        return neighbors;
    }


    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < BattleShip2.BOARD_SIZE && point.y >= 0 && point.y < BattleShip2.BOARD_SIZE;
    }

}
