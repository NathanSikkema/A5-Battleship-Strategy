import battleship.BattleShip2;
import java.awt.*;
import java.util.ArrayList;

/**
 * Represents and manages the status of a ship in the Battleship game.
 * Tracks hit coordinates, sink status, and calculates neighboring cells.
 * Uses a cache system for efficient neighbor calculations.
 * @author Nathan Sikkema
 * @author Brendan Dileo
 */
public class ShipStatus {
    private static final int MAX_NEIGHBORS = 16;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {1, 0, -1, 0};
    private static final int BOARD_SIZE = BattleShip2.BOARD_SIZE;
    private final int size;
    private final int[][] hitCoordinatesArray;
    private final Point[] neighborCache;
    private boolean sunk;
    private int hitCoordinatesCount = 0;

    /**
     * Constructs a new ShipStatus instance for a ship of specified size.
     * Initializes the hit coordinate tracking array and neighbor cache.
     *
     * @param size The size of the ship (number of cells it occupies)
     */
    public ShipStatus(int size) {
        this.sunk = false;
        this.size = size;
        this.hitCoordinatesArray = new int[size][2];
        this.neighborCache = new Point[MAX_NEIGHBORS];
        for (int i = 0; i < MAX_NEIGHBORS; i++) {
            neighborCache[i] = new Point();
        }
    }

    /**
     * Checks if the ship has been sunk.
     *
     * @return boolean True if the ship is not sunk, false if it is sunk
     */
    public boolean isSunk() {
        return !sunk;
    }

    /**
     * Sets the sunk status of the ship.
     *
     * @param sunk The new sunk status to set
     */
    public void setSunk(boolean sunk) {
        this.sunk = sunk;
    }

    /**
     * Gets the size of the ship.
     *
     * @return int The number of cells the ship occupies
     */
    public int getSize() {
        return size;
    }

    /**
     * Updates the hit coordinates for this ship.
     * Stores the coordinates in an efficient array format.
     *
     * @param hitCoordinates ArrayList of Points containing the hit coordinates
     */
    public void setHitCoordinates(ArrayList<Point> hitCoordinates) {
        hitCoordinatesCount = hitCoordinates.size();
        for (int i = 0; i < hitCoordinatesCount; i++) {
            Point p = hitCoordinates.get(i);
            hitCoordinatesArray[i][0] = p.x;
            hitCoordinatesArray[i][1] = p.y;
        }
    }

    /**
     * Calculates and returns all valid neighboring cells around the ship.
     * Uses a caching system for efficient Point object reuse during calculations.
     * Only returns cells that are:
     * - Within the board boundaries
     * - Not occupied by the ship itself
     * - Adjacent to any part of the ship
     *
     * @return ArrayList<Point> List of valid neighboring cell coordinates
     */
    public ArrayList<Point> getNeighbors() {
        boolean[][] shipCells = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int i = 0; i < hitCoordinatesCount; i++) {
            shipCells[hitCoordinatesArray[i][0]][hitCoordinatesArray[i][1]] = true;
        }

        int neighborCount = 0;

        for (int i = 0; i < hitCoordinatesCount; i++) {
            int x = hitCoordinatesArray[i][0];
            int y = hitCoordinatesArray[i][1];

            for (int j = 0; j < 4; j++) {
                int newX = x + DX[j];
                int newY = y + DY[j];

                if (newX >= 0 && newX < BOARD_SIZE && newY >= 0 && newY < BOARD_SIZE && !shipCells[newX][newY]) {
                    Point p = neighborCache[neighborCount++];
                    p.x = newX;
                    p.y = newY;
                }
            }
        }

        ArrayList<Point> result = new ArrayList<>(neighborCount);
        for (int i = 0; i < neighborCount; i++) {
            result.add(new Point(neighborCache[i].x, neighborCache[i].y));
        }

        return result;
    }
}
