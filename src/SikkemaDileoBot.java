import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;


/**
 Adapted from code by Mark Yendt (ExampleBot.java), Mohawk College, December 2021
 'A Sample random shooter - Takes no precaution on double shooting and has no strategy once
 a ship is hit - This is not a good solution to the problem!'

 @author Nathan Sikkema
 @author Brendan Dileo */

public class SikkemaDileoBot implements BattleShipBot {
    public static boolean debug = false;

    // Directions to move once hit is made
    private final Point[] directions = {
            new Point(0, 1),  // right
            new Point(0, -1), // left
            new Point(1, 0),  // down
            new Point(-1, 0)  // up
    };

    private int size;
    private BattleShip2 battleShip;
    private Random random;
    private HashSet<Point> shotsFired;
    private HashSet<Point> uselessLocations;
    private Queue<Point> targetQueue;   // Changed to Queue
    private ArrayList<Point> hitList;
    private cellState[][] boardState;
    private status hitOrientation;
    private int[][] probabilityMap;
    private int[] remainingShips = {5, 4, 3, 3, 2};
    private Point lastHit;
    private int consecutiveHits;

    // Init
    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        // Need Seed for same results to persist over runs. need to improve performance
        random = new Random(0xAAAAAAAA);
        shotsFired = new HashSet<>();
        uselessLocations = new HashSet<>();
        targetQueue = new LinkedList<>();       // Initialize as a LinkedList (Queue)
        hitList = new ArrayList<>();
        // Initialize the boardState 2D array to track the state of each cell
        boardState = new cellState[size][size];
        probabilityMap = new int[size][size];
        hitOrientation = status.UNKNOWN;
        lastHit = null;
        consecutiveHits = 0;
        updateProbabilityMap();
        debugPrint("Bot initialized with board size: " + size);
    }

    /**
     * Updates the probability map that guides our shot selection.
     * The map assigns higher probabilities to cells where ships are more likely to be.
     * In target mode, we prioritize cells around hits.
     * In hunt mode, we calculate probabilities based on where remaining ships could fit.
     */
    private void updateProbabilityMap() {
        // Reset probability map - zero out all cells to start fresh
        for (int i = 0; i < size; i++) {
            Arrays.fill(probabilityMap[i], 0);
        }

        // Skip advanced logic if in target mode - when we have hits to pursue
        if (!hitList.isEmpty()) {
            debugPrint("Updating probability map in target mode");
            // For each hit we've made, check adjacent cells
            for (Point hit : hitList) {
                // Check all 4 directions (up, down, left, right)
                for (Point dir : directions) {
                    Point neighbor = new Point(hit.x + dir.x, hit.y + dir.y);
                    // If the cell is valid and hasn't been shot yet
                    if (isValid(neighbor) && boardState[neighbor.x][neighbor.y] == cellState.UNKNOWN) {
                        // Add high probability to cells around hits
                        probabilityMap[neighbor.x][neighbor.y] += 1000;
                    }
                }
            }
            return; // Skip hunt mode calculations if we're in target mode
        }

        debugPrint("Updating probability map in hunt mode");
        // In hunt mode, consider every unknown cell and count how many ships could fit
        // For each remaining ship size (5,4,3,3,2)
        for (int shipSize : remainingShips) {
            if (shipSize == 0) continue;  // Skip if ship is already sunk

            // Calculate horizontal and vertical probabilities separately
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    // Check if ship can be placed horizontally starting at (i,j)
                    if (canPlaceShip(i, j, shipSize, true)) {
                        // Add probability for each cell the ship would cover
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i][j + k] += shipSize; // Weight by ship size
                        }
                    }

                    // Check if ship can be placed vertically starting at (i,j)
                    if (canPlaceShip(i, j, shipSize, false)) {
                        // Add probability for each cell the ship would cover
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i + k][j] += shipSize; // Weight by ship size
                        }
                    }
                }
            }
        }

        // Apply checkerboard pattern in hunt mode - helps find ships faster
        if (lastHit == null) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if ((i + j) % 2 != 0) {  // Odd parity squares
                        probabilityMap[i][j] = 0;  // Zero out odd parity squares in hunt mode
                    }
                }
            }
        }

        // Debug print the probability map if debug is enabled
        if (debug) {
            debugPrint("Current Probability Map:");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    System.out.printf("%4d", probabilityMap[i][j]);
                }
                System.out.println();
            }
            System.out.println();
        }
    }

    /**
     * Checks if a ship of given length can be placed at position (x,y)
     * @param x Starting x coordinate
     * @param y Starting y coordinate
     * @param length Length of the ship
     * @param horizontal True if ship is horizontal, false if vertical
     * @return True if ship can be placed, false otherwise
     */
    private boolean canPlaceShip(int x, int y, int length, boolean horizontal) {
        if (horizontal) {
            // Check if ship would go off the board
            if (y + length > size) return false;
            // Check if all cells are unknown
            for (int i = 0; i < length; i++) {
                if (boardState[x][y + i] != cellState.UNKNOWN) return false;
            }
        } else {
            // Check if ship would go off the board
            if (x + length > size) return false;
            // Check if all cells are unknown
            for (int i = 0; i < length; i++) {
                if (boardState[x + i][y] != cellState.UNKNOWN) return false;
            }
        }
        return true;
    }

    /**
     * Selects the best cell to shoot at based on the probability map
     * @return The Point representing the best cell to shoot at
     */
    private Point getBestProbabilityShot() {
        Point bestShot = null;
        int maxProbability = -1;

        // In hunting mode, only consider even parity squares
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // Skip odd parity squares in hunt mode (checkerboard pattern)
                if (lastHit == null && (i + j) % 2 != 0) continue;

                Point p = new Point(i, j);
                // Only consider cells we haven't shot yet
                if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                    // Keep track of highest probability cell
                    if (probabilityMap[i][j] > maxProbability) {
                        maxProbability = probabilityMap[i][j];
                        bestShot = p;
                    }
                }
            }
        }
        return bestShot;
    }

    /**
     * Fires a shot at the best possible location
     * Uses a combination of target queue and probability map to determine where to shoot
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();

        // 1. Check target queue first - highest priority
        if (!targetQueue.isEmpty()) {
            debugPrint("Taking shot from target queue");
            shot = targetQueue.poll();
            // Skip any cells we've already shot or marked as useless
            while (shot != null && (shotsFired.contains(shot) || uselessLocations.contains(shot))) {
                shot = targetQueue.poll();
            }
        }

        // 2. Use probability map if no target in queue
        if (shot == null) {
            debugPrint("Using probability map to determine shot");
            shot = getBestProbabilityShot();
        }

        // 3. Fallback to any valid position
        if (shot == null) {
            debugPrint("Falling back to any valid position");
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    Point p = new Point(i, j);
                    if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                        shot = p;
                    }
                }
            }
        }

        // 4. Absolute last resort
        if (shot == null) {
            debugPrint("Using absolute last resort shot");
            shot = new Point(0, 0);
        }

        // Fire the shot and update our state
        shotsFired.add(shot);
        boolean hit = battleShip.shoot(shot);
        boardState[shot.x][shot.y] = hit ? cellState.HIT : cellState.MISS;
        debugPrint("Shot at (" + shot.x + "," + shot.y + "): " + (hit ? "HIT" : "MISS"));

        if (hit) {
            hitList.add(shot);
            consecutiveHits++;
            debugPrint("Consecutive hits: " + consecutiveHits);

            // Check if a ship was sunk
            int currentSunkShips = battleShip.numberOfShipsSunk();
            if (currentSunkShips > previousSunkShips) {
                debugPrint("Ship sunk! Resetting target queue and state");
                // A ship was sunk, clear target queue and reset state
                targetQueue.clear();
                lastHit = null;
                hitOrientation = status.UNKNOWN;
                consecutiveHits = 0;
                // Mark all cells around the sunk ship as useless
                markSunkShipCells();
            } else if (lastHit == null) {
                debugPrint("First hit, adding adjacent points to queue");
                lastHit = shot;
                // Add all adjacent points to queue
                for (Point dir : directions) {
                    Point neighbor = new Point(shot.x + dir.x, shot.y + dir.y);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor) && !uselessLocations.contains(neighbor)) {
                        targetQueue.add(neighbor);
                    }
                }
            } else {
                // Determine orientation if not known
                if (hitOrientation == status.UNKNOWN) {
                    if (shot.x == lastHit.x) {
                        hitOrientation = status.HORIZONTAL;
                        debugPrint("Ship orientation determined: HORIZONTAL");
                        markPerpendicularCellsUseless(shot, true);
                    } else if (shot.y == lastHit.y) {
                        hitOrientation = status.VERTICAL;
                        debugPrint("Ship orientation determined: VERTICAL");
                        markPerpendicularCellsUseless(shot, false);
                    }
                }

                // Add next cell in the same direction
                if (hitOrientation != status.UNKNOWN) {
                    Point nextCell = getNextCellInDirection(shot);
                    if (nextCell != null) {
                        targetQueue.add(nextCell);
                        debugPrint("Added next cell in direction to queue: (" + nextCell.x + "," + nextCell.y + ")");
                    }
                }

                lastHit = shot;
            }
        } else {
            if (consecutiveHits > 0 && hitOrientation != status.UNKNOWN) {
                debugPrint("Miss after hits, trying opposite direction");
                // If we miss after hits, try the opposite direction
                targetQueue.clear();
                Point firstHit = hitList.get(hitList.size() - consecutiveHits);
                Point oppositeDir = getOppositeDirection(firstHit);
                if (oppositeDir != null) {
                    targetQueue.add(oppositeDir);
                }
            }
            consecutiveHits = 0;
            if (targetQueue.isEmpty()) {
                lastHit = null;
                hitOrientation = status.UNKNOWN;
            }
        }

        updateProbabilityMap();
    }

    private Point getNextCellInDirection(Point current) {
        // Get next cell in current ship's direction
        if (hitOrientation == status.HORIZONTAL) {
            Point right = new Point(current.x, current.y + 1);
            Point left = new Point(current.x, current.y - 1);
            if (isValid(right) && !shotsFired.contains(right) && !uselessLocations.contains(right)) {
                return right;
            }
            if (isValid(left) && !shotsFired.contains(left) && !uselessLocations.contains(left)) {
                return left;
            }
        } else if (hitOrientation == status.VERTICAL) {
            Point down = new Point(current.x + 1, current.y);
            Point up = new Point(current.x - 1, current.y);
            if (isValid(down) && !shotsFired.contains(down) && !uselessLocations.contains(down)) {
                return down;
            }
            if (isValid(up) && !shotsFired.contains(up) && !uselessLocations.contains(up)) {
                return up;
            }
        }
        return null;
    }

    private void markSunkShipCells() {
        // Get the last consecutive hits
        ArrayList<Point> sunkShipPoints = new ArrayList<>();
        for (int i = hitList.size() - consecutiveHits; i < hitList.size(); i++) {
            sunkShipPoints.add(hitList.get(i));
        }

        // Mark all cells around the sunk ship as useless
        for (Point p : sunkShipPoints) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Point neighbor = new Point(p.x + dx, p.y + dy);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor)) {
                        boardState[neighbor.x][neighbor.y] = cellState.USELESS;
                        uselessLocations.add(neighbor);
                    }
                }
            }
        }
    }

    private Point getOppositeDirection(Point firstHit) {
        // Get the opposite direction from first hit
        if (lastHit == null || firstHit == null) return null;

        if (hitOrientation == status.HORIZONTAL) {
            int direction = lastHit.y > firstHit.y ? -1 : 1;
            Point opposite = new Point(firstHit.x, firstHit.y + direction);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        } else if (hitOrientation == status.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1 : 1;
            Point opposite = new Point(firstHit.x + direction, firstHit.y);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        }
        return null;
    }

    private void markPerpendicularCellsUseless(Point shot, boolean horizontal) {
        // Mark cells perpendicular to ship as useless
        if (horizontal) {
            Point up = new Point(shot.x - 1, shot.y);
            Point down = new Point(shot.x + 1, shot.y);
            if (isValid(up)) {
                boardState[up.x][up.y] = cellState.USELESS;
                uselessLocations.add(up);
            }
            if (isValid(down)) {
                boardState[down.x][down.y] = cellState.USELESS;
                uselessLocations.add(down);
            }
        } else {
            Point left = new Point(shot.x, shot.y - 1);
            Point right = new Point(shot.x, shot.y + 1);
            if (isValid(left)) {
                boardState[left.x][left.y] = cellState.USELESS;
                uselessLocations.add(left);
            }
            if (isValid(right)) {
                boardState[right.x][right.y] = cellState.USELESS;
                uselessLocations.add(right);
            }
        }
    }

    private boolean isValid(Point point) {
        // Check if point is within board bounds
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }

    @Override
    public String getAuthors() {
        return "Nathan Sikkema and Brendan Dileo";
    }

    private enum cellState {
        HIT, MISS, UNKNOWN, USELESS
    }

    private enum status {
        VERTICAL, HORIZONTAL, UNKNOWN
    }

    private void debugPrint(String message) {
        if (debug) {
            System.out.println("[DEBUG] " + message);
        }
    }
}

