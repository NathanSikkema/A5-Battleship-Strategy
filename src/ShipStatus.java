import battleship.BattleShip2;

import java.awt.*;
import java.util.ArrayList;

public class ShipStatus {
    private boolean sunk;
    private int size;
    private orientation shipOrientation;
    
    // Pre-allocated arrays for better performance
    private int[][] hitCoordsArray;
    private int hitCoordsCount = 0;
    
    // Pre-allocated arrays for neighbors
    private static final int MAX_NEIGHBORS = 16; // Increased from 8 to handle larger ships
    private Point[] neighborCache;
    private int neighborCount;
    
    // Pre-allocated direction arrays for better performance
    private static final int[] DX = {0, 1, 0, -1}; // right, down, left, up
    private static final int[] DY = {1, 0, -1, 0};
    
    public ShipStatus(int size) {
        this.sunk = false;
        this.size = size;
        this.hitCoordsArray = new int[size][2];
        this.neighborCache = new Point[MAX_NEIGHBORS];
        for (int i = 0; i < MAX_NEIGHBORS; i++) {
            neighborCache[i] = new Point();
        }
    }

    public boolean isSunk() {
        return sunk;
    }
    
    public int getSize() {
        return size;
    }

    public void setSunk(boolean sunk) {
        this.sunk = sunk;
    }

    public int getSunkLength() {
        return sunk ? hitCoordsCount : -1;
    }

    public void setHitCoordinates(ArrayList<Point> hitCoordinates) {
        hitCoordsCount = hitCoordinates.size();
        for (int i = 0; i < hitCoordsCount; i++) {
            hitCoordsArray[i][0] = hitCoordinates.get(i).x;
            hitCoordsArray[i][1] = hitCoordinates.get(i).y;
        }
    }

    public ArrayList<Point> getHitCoordinates() {
        ArrayList<Point> result = new ArrayList<>(hitCoordsCount);
        for (int i = 0; i < hitCoordsCount; i++) {
            result.add(new Point(hitCoordsArray[i][0], hitCoordsArray[i][1]));
        }
        return result;
    }

    public void addCoordinate(Point p) {
        hitCoordsArray[hitCoordsCount][0] = p.x;
        hitCoordsArray[hitCoordsCount][1] = p.y;
        hitCoordsCount++;
    }

    public Point getCoordinate(int i) {
        return new Point(hitCoordsArray[i][0], hitCoordsArray[i][1]);
    }

    public void setShipOrientation(orientation shipOrientation) {
        this.shipOrientation = shipOrientation;
    }

    public ArrayList<Point> getNeighbors() {
        // Use a boolean array instead of HashSet for better performance
        boolean[][] shipCells = new boolean[BattleShip2.BOARD_SIZE][BattleShip2.BOARD_SIZE];
        
        // Mark ship cells
        for (int i = 0; i < hitCoordsCount; i++) {
            shipCells[hitCoordsArray[i][0]][hitCoordsArray[i][1]] = true;
        }
        
        // Create ArrayList with initial capacity to avoid resizing
        ArrayList<Point> result = new ArrayList<>(hitCoordsCount * 4);
        
        for (int i = 0; i < hitCoordsCount; i++) {
            int x = hitCoordsArray[i][0];
            int y = hitCoordsArray[i][1];
            
            for (int j = 0; j < 4; j++) {
                int newX = x + DX[j];
                int newY = y + DY[j];
                
                if (isValid(newX, newY) && !shipCells[newX][newY]) {
                    // Create new Point directly
                    result.add(new Point(newX, newY));
                }
            }
        }

        return result;
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < BattleShip2.BOARD_SIZE && y >= 0 && y < BattleShip2.BOARD_SIZE;
    }
}
