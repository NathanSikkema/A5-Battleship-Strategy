import battleship.BattleShip2;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;


public class ShipStatus {
    private boolean sunk;
    private ArrayList<Point> hitCoordinates;
    private int size;
    private orientation shipOrientation;
    
    // Pre-allocated arrays for better performance
    private int[][] hitCoordsArray;
    private int hitCoordsCount = 0;
    
    public ShipStatus(int size) {
        this.sunk = false;
        this.size = size;
        this.hitCoordinates = new ArrayList<>();
        this.hitCoordsArray = new int[size][2];
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
        // Also update the array representation
        hitCoordsCount = hitCoordinates.size();
        for (int i = 0; i < hitCoordsCount; i++) {
            hitCoordsArray[i][0] = hitCoordinates.get(i).x;
            hitCoordsArray[i][1] = hitCoordinates.get(i).y;
        }
    }

    public void addCoordinate(Point p) {
        hitCoordinates.add(p);
        // Also update the array representation
        hitCoordsArray[hitCoordsCount][0] = p.x;
        hitCoordsArray[hitCoordsCount][1] = p.y;
        hitCoordsCount++;
    }

    public Point getCoordinate(int i) {
        return hitCoordinates.get(i);
    }

    public void setShipOrientation(orientation shipOrientation) {
        this.shipOrientation = shipOrientation;
    }

    public ArrayList<Point> getNeighbors() {
        ArrayList<Point> neighbors = new ArrayList<>();
        // Use a boolean array instead of HashSet for better performance
        boolean[][] shipCells = new boolean[BattleShip2.BOARD_SIZE][BattleShip2.BOARD_SIZE];
        
        // Mark ship cells
        for (int i = 0; i < hitCoordsCount; i++) {
            shipCells[hitCoordsArray[i][0]][hitCoordsArray[i][1]] = true;
        }
        
        int[] dx = {0, 1, 0, -1}; // right, down, left, up
        int[] dy = {1, 0, -1, 0};

        for (int i = 0; i < hitCoordsCount; i++) {
            int x = hitCoordsArray[i][0];
            int y = hitCoordsArray[i][1];
            
            for (int j = 0; j < 4; j++) {
                int newX = x + dx[j];
                int newY = y + dy[j];
                
                if (isValid(newX, newY) && !shipCells[newX][newY]) {
                    neighbors.add(new Point(newX, newY));
                }
            }
        }

        return neighbors;
    }


    private boolean isValid(int x, int y) {
        return x >= 0 && x < BattleShip2.BOARD_SIZE && y >= 0 && y < BattleShip2.BOARD_SIZE;
    }
}
