import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

/**
 @author Nathan Sikkema
 @author Brendan Dileo */


public class SikkemaDileoBot implements BattleShipBot {

    // Debug
    public static boolean debug = false;

    // Directions for ship orientation
    private final Point[] directions = {
            new Point(0, 1),  // right
            new Point(0, -1), // left
            new Point(1, 0),  // down
            new Point(-1, 0)  // up
    };

    // Game state tracking variables
    private int size;
    private BattleShip2 battleShip;
    private HashSet<Point> shotsFired;
    private HashSet<Point> uselessLocations;
    private Queue<Point> targetQueue;
    private ArrayList<Point> hitList;
    private cellState[][] boardState;
    private orientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;
    private ShipStatus[] shipStatuses;

    /**
     * Initializes the bot with a new game instance.
     * Sets up all tracking variables and initializes the board state.
     */
    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        shotsFired = new HashSet<>();
        uselessLocations = new HashSet<>();
        targetQueue = new LinkedList<>();
        hitList = new ArrayList<>();
        boardState = new cellState[size][size];
        hitOrientation = orientation.UNKNOWN;
        lastHit = null;
        consecutiveHits = 0;

        // Initialize board state to UNKNOWN
        // All cells unknown to sdtart
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                boardState[i][j] = cellState.UNKNOWN;

        // Initialize ship status tracking
        shipStatuses = new ShipStatus[battleShip.getShipSizes().length];
        for (int i = 0; i < battleShip.getShipSizes().length; i++)
            shipStatuses[i] = new ShipStatus(battleShip.getShipSizes()[i]);
    }

    /**
     * Builds a probability map for potential ship locations.
     * Changes from previous version:
     * - More efficient initial search pattern
     * - Better handling of early game targeting
     * - Smarter ship completion logic
     */
    private int[][] buildProbabilityMap(int[] shipSizes, ArrayList<Point> hitList, cellState[][] boardState) {
        int[][] probabilityMap = new int[BattleShip2.BOARD_SIZE][BattleShip2.BOARD_SIZE];

        // Only consider unsunk ships for probability calculation
        ArrayList<Integer> remainingSizes = new ArrayList<>();
        for (ShipStatus s : shipStatuses)
            if (!s.isSunk()) remainingSizes.add(s.getSize());

        // Calculate base probabilities with higher weights for larger ships
        for (int shipSize : remainingSizes) {
            // Horizontal placements with improved probability calculation
            for (int i = 0; i < BattleShip2.BOARD_SIZE; i++) {
                for (int j = 0; j <= BattleShip2.BOARD_SIZE - shipSize; j++) {
                    boolean canPlace = true;
                    int hitCount = 0;
                    int consecutiveHits = 0;
                    int maxConsecutive = 0;
                    boolean hasGap = false;
                    int emptySpaces = 0;

                    // Check if ship can be placed and count hits
                    for (int k = 0; k < shipSize; k++) {
                        Point p = new Point(i, j + k);
                        if (boardState[p.x][p.y] == cellState.MISS || boardState[p.x][p.y] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        if (boardState[p.x][p.y] == cellState.HIT) {
                            hitCount++;
                            consecutiveHits++;
                            maxConsecutive = Math.max(maxConsecutive, consecutiveHits);
                        } else {
                            if (consecutiveHits > 0) hasGap = true;
                            consecutiveHits = 0;
                            emptySpaces++;
                        }
                    }

                    // Add probability scores with improved weights
                    if (canPlace) {
                        // More aggressive weights focusing on ship completion
                        int baseScore = shipSize * 12 + hitCount * 18 + maxConsecutive * 25;
                        if (hasGap) baseScore += 30; // Higher bonus for potential ship completion
                        if (emptySpaces == 1) baseScore += 40; // Very high bonus for single empty space
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i][j + k] += baseScore;
                        }
                    }
                }
            }

            // Vertical placements with same improved probability calculation
            for (int i = 0; i <= BattleShip2.BOARD_SIZE - shipSize; i++) {
                for (int j = 0; j < BattleShip2.BOARD_SIZE; j++) {
                    boolean canPlace = true;
                    int hitCount = 0;
                    int consecutiveHits = 0;
                    int maxConsecutive = 0;
                    boolean hasGap = false;
                    int emptySpaces = 0;

                    // Check if ship can be placed and count hits
                    for (int k = 0; k < shipSize; k++) {
                        Point p = new Point(i + k, j);
                        if (boardState[p.x][p.y] == cellState.MISS || boardState[p.x][p.y] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        if (boardState[p.x][p.y] == cellState.HIT) {
                            hitCount++;
                            consecutiveHits++;
                            maxConsecutive = Math.max(maxConsecutive, consecutiveHits);
                        } else {
                            if (consecutiveHits > 0) hasGap = true;
                            consecutiveHits = 0;
                            emptySpaces++;
                        }
                    }

                    // Add probability scores with improved weights
                    if (canPlace) {
                        // More aggressive weights focusing on ship completion
                        int baseScore = shipSize * 12 + hitCount * 18 + maxConsecutive * 25;
                        if (hasGap) baseScore += 30; // Higher bonus for potential ship completion
                        if (emptySpaces == 1) baseScore += 40; // Very high bonus for single empty space
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i + k][j] += baseScore;
                        }
                    }
                }
            }
        }

        // Add high bonus for cells adjacent to hits
        // More balanced bonuses for better targeting
        for (Point hit : hitList) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx != 0 && dy != 0) continue; // Skip diagonals
                    int newX = hit.x + dx;
                    int newY = hit.y + dy;
                    if (isValid(new Point(newX, newY)) && boardState[newX][newY] == cellState.UNKNOWN) {
                        probabilityMap[newX][newY] += 20; // Increased base bonus for adjacent cells

                        // Additional bonus if this cell could complete a ship
                        if (lastHit != null && hitOrientation != orientation.UNKNOWN) {
                            if ((hitOrientation == orientation.HORIZONTAL && dx == 0) ||
                                (hitOrientation == orientation.VERTICAL && dy == 0)) {
                                probabilityMap[newX][newY] += 30; // Higher bonus for potential ship completion
                            }
                        }
                    }
                }
            }
        }

        // Decrease probability around misses more moderately
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (boardState[i][j] == cellState.MISS) {
                    for (Point dir : directions) {
                        int x = i + dir.x;
                        int y = j + dir.y;
                        if (isValid(new Point(x, y)) && boardState[x][y] == cellState.UNKNOWN)
                            probabilityMap[x][y] = Math.max(0, probabilityMap[x][y] - 9); // More aggressive penalty
                    }
                }
            }
        }

        return probabilityMap;
    }

    /**
     * Finds the cell with the highest probability score that hasn't been shot at.
     */
    private Point findHighestProbability(int[][] map) {
        Point bestShot = null;
        int highestProbability = -1;

        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                Point currentPoint = new Point(i, j);
                if (shotsFired.contains(currentPoint) || uselessLocations.contains(currentPoint)) continue;

                if (map[i][j] > highestProbability) {
                    highestProbability = map[i][j];
                    bestShot = currentPoint;
                }
            }
        }
        return bestShot;
    }

    /**
     * Determines the coordinates of a ship based on a hit.
     * Tracks the ship's orientation and all cells that are part of it.
     */
    private void findShipCoordinates(Point p) {
        ArrayList<Point> shipCoordinates = new ArrayList<>();
        shipCoordinates.add(p);
        orientation targetBoatOrientation = orientation.UNKNOWN;

        Point shipDirection = null;

        // Try to find a direction with another HIT adjacent
        for (Point dir : directions) {
            Point neighbor = new Point(p.x + dir.x, p.y + dir.y);
            if (isValid(neighbor) && boardState[neighbor.x][neighbor.y] == cellState.HIT) {
                shipDirection = dir;

                // Set orientation based on direction
                if (dir.x != 0) {
                    targetBoatOrientation = orientation.VERTICAL;
                } else if (dir.y != 0) {
                    targetBoatOrientation = orientation.HORIZONTAL;
                }
                break;
            }
        }

        // If no direction found, it's a single-point ship (unlikely with size ≥ 2)
        if (shipDirection == null) {
            updateShipStatus(shipCoordinates, targetBoatOrientation);
            return;
        }

        // Traverse in both directions along the identified direction
        for (int mult = -1; mult <= 1; mult += 2) {
            int dx = shipDirection.x * mult;
            int dy = shipDirection.y * mult;
            Point curr = new Point(p.x + dx, p.y + dy);

            while (isValid(curr) && boardState[curr.x][curr.y] == cellState.HIT) {
                shipCoordinates.add(curr);
                curr = new Point(curr.x + dx, curr.y + dy);
            }
        }

        updateShipStatus(shipCoordinates, targetBoatOrientation);
    }

    /**
     * Updates the status of a ship when it's sunk.
     * Marks adjacent cells as useless and updates ship tracking.
     */
    private void updateShipStatus(ArrayList<Point> shipCoordinates, orientation o) {
        ArrayList<Point> sunkShipNeighbors = new ArrayList<>();
        for (ShipStatus s : shipStatuses) {
            if (!s.isSunk() && s.getSize() == shipCoordinates.size()) {
                s.setHitCoordinates(shipCoordinates);
                s.setSunk(true);
                s.setShipOrientation(o);
                sunkShipNeighbors = s.getNeighbors();
                break;
            }
        }
        if (debug) System.out.print("Marking cells useless next to sunk boat: ");
        for (Point n : sunkShipNeighbors) {
            if (isValid(n) && boardState[n.x][n.y] != cellState.HIT) {
                boardState[n.x][n.y] = cellState.USELESS;
                if (debug) System.out.print("X: " + n.x + " Y: " + n.y);
            }
        }
    }

    /**
     * Fires a shot at the best possible location.
     * Uses a combination of target queue and probability map to determine where to shoot.
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();

        // Build probability map first
        int[][] probabilityMap = buildProbabilityMap(battleShip.getShipSizes(), hitList, boardState);

        // If we have a hit and know the orientation, prioritize completing the ship
        if (lastHit != null && hitOrientation != orientation.UNKNOWN) {
            // Try to find the next cell in the ship's direction
            Point nextCell = getNextCellInDirection(lastHit);
            if (nextCell != null && !shotsFired.contains(nextCell) && !uselessLocations.contains(nextCell)) {
                shot = nextCell;
            } else {
                // If we can't continue in that direction, try the opposite direction
                Point oppositeCell = getOppositeDirection(lastHit);
                if (oppositeCell != null && !shotsFired.contains(oppositeCell) && !uselessLocations.contains(oppositeCell)) {
                    shot = oppositeCell;
                }
            }
        }

        // If no shot from completing a ship, check target queue against probability map
        if (shot == null && !targetQueue.isEmpty()) {
            Point bestQueueShot = null;
            int highestQueueProbability = -1;

            // Sort the queue by probability
            ArrayList<Point> sortedQueue = new ArrayList<>(targetQueue);
            sortedQueue.sort((a, b) -> Integer.compare(
                    probabilityMap[b.x][b.y], probabilityMap[a.x][a.y]
            ));

            // Take the highest-probability shot
            for (Point queueShot : sortedQueue) {
                if (!shotsFired.contains(queueShot) && !uselessLocations.contains(queueShot)) {
                    bestQueueShot = queueShot;
                    break;
                }
            }

            if (bestQueueShot != null) {
                shot = bestQueueShot;
                targetQueue.remove(bestQueueShot);
            }
        }

        // If still no shot, use highest probability from map
        if (shot == null) {
            shot = findHighestProbability(probabilityMap);
        }

        // If still no shot, use a more efficient search pattern
        if (shot == null) {
            int minShipSize = Integer.MAX_VALUE;
            for (ShipStatus s : shipStatuses) {
                if (!s.isSunk() && s.getSize() < minShipSize) {
                    minShipSize = s.getSize();
                }
            }

            // Use a dynamic checkerboard pattern with better coverage
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    // Adjust the pattern to ensure even coverage
                    if ((i % minShipSize == 0 || j % minShipSize == 0) && (i + j) % 2 == 0) {
                        Point p = new Point(i, j);
                        if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                            shot = p;
                        }
                    }
                }
            }
        }

        // Safety check - if still no shot, find any unshot cell
        if (shot == null) {
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    Point p = new Point(i, j);
                    if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                        shot = p;
                    }
                }
            }
        }

        // Fire the shot
        shotsFired.add(shot);
        boolean hit = battleShip.shoot(shot);
        boardState[shot.x][shot.y] = hit ? cellState.HIT : cellState.MISS;
        debugPrint("Shot at (" + shot.x + "," + shot.y + "): " + (hit ? "HIT" : "MISS"));

        // Update game state
        if (hit) {
            hitList.add(shot);
            consecutiveHits++;
            debugPrint("Consecutive hits: " + consecutiveHits);

            if (battleShip.numberOfShipsSunk() > previousSunkShips) {
                debugPrint("Ship sunk! Resetting target queue and state");
                findShipCoordinates(shot);
                targetQueue.clear();
                lastHit = null;
                hitOrientation = orientation.UNKNOWN;
                consecutiveHits = 0;
                markSunkShipCells();
            } else if (lastHit == null) {
                debugPrint("First hit, adding adjacent points to queue");
                lastHit = shot;
                // Add adjacent cells to queue in order of probability
                Point[] orderedDirs = {
                    new Point(0, 1),  // right
                    new Point(0, -1), // left
                    new Point(1, 0),  // down
                    new Point(-1, 0)  // up
                };

                // Build a new probability map for the current state
                int[][] newProbabilityMap = buildProbabilityMap(battleShip.getShipSizes(), hitList, boardState);

                // Add neighbors in order of probability
                for (Point dir : orderedDirs) {
                    Point neighbor = new Point(shot.x + dir.x, shot.y + dir.y);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor) && !uselessLocations.contains(neighbor)) {
                        // Only add if it has a reasonable probability
                        if (newProbabilityMap[neighbor.x][neighbor.y] > 0) {
                            targetQueue.add(neighbor);
                        }
                    }
                }
            } else {
                // Update ship orientation
                if (shot.x == lastHit.x) {
                    hitOrientation = orientation.HORIZONTAL;
                    debugPrint("Ship orientation determined: HORIZONTAL");
                    markPerpendicularCellsUseless(shot, true);
                } else if (shot.y == lastHit.y) {
                    hitOrientation = orientation.VERTICAL;
                    debugPrint("Ship orientation determined: VERTICAL");
                    markPerpendicularCellsUseless(shot, false);
                }
                if (hitOrientation != orientation.UNKNOWN) {
                    Point nextCell = getNextCellInDirection(shot);
                    if (nextCell != null) {
                        targetQueue.add(nextCell);
                        debugPrint("Added next cell in direction to queue: (" + nextCell.x + "," + nextCell.y + ")");
                    }
                }
                lastHit = shot;
            }
        } else {
            if (consecutiveHits > 0 && hitOrientation != orientation.UNKNOWN) {
                debugPrint("Miss after hits, trying opposite direction");
                targetQueue.clear();
                Point firstHit = hitList.get(hitList.size() - consecutiveHits);
                Point oppositeDir = getOppositeDirection(firstHit);
                if (oppositeDir != null) targetQueue.add(oppositeDir);
            }
            consecutiveHits = 0;
            if (targetQueue.isEmpty()) {
                lastHit = null;
                hitOrientation = orientation.UNKNOWN;
            }
        }
        if (debug) printBoardState();
    }

    /**
     * Get next cell in direction
     * @param current The current point
     * @return
     */
    private Point getNextCellInDirection(Point current) {
        if (hitOrientation == orientation.HORIZONTAL) {
            Point right = new Point(current.x, current.y + 1);
            Point left = new Point(current.x, current.y - 1);
            if (isValid(right) && !shotsFired.contains(right) && !uselessLocations.contains(right)) return right;
            if (isValid(left) && !shotsFired.contains(left) && !uselessLocations.contains(left)) return left;
        } else if (hitOrientation == orientation.VERTICAL) {
            Point down = new Point(current.x + 1, current.y);
            Point up = new Point(current.x - 1, current.y);
            if (isValid(down) && !shotsFired.contains(down) && !uselessLocations.contains(down)) return down;
            if (isValid(up) && !shotsFired.contains(up) && !uselessLocations.contains(up)) return up;
        }
        return null;
    }

    /**
     *
     * @param firstHit
     * @return
     */
    private Point getOppositeDirection(Point firstHit) {
        if (lastHit == null || firstHit == null) return null;

        if (hitOrientation == orientation.HORIZONTAL) {
            int direction = lastHit.y > firstHit.y ? -1 : 1;
            Point opposite = new Point(firstHit.x, firstHit.y + direction);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        } else if (hitOrientation == orientation.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1 : 1;
            Point opposite = new Point(firstHit.x + direction, firstHit.y);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        }
        return null;
    }

    /**
     * Marks cells around a sunk ship as useless.
     * Prevents targeting cells that can't contain other ships.
     */
    private void markSunkShipCells() {
        ArrayList<Point> sunkShipPoints = new ArrayList<>();
        for (int i = hitList.size() - consecutiveHits; i < hitList.size(); i++) {
            sunkShipPoints.add(hitList.get(i));
        }

        for (Point p : sunkShipPoints) {
            // Expand the range to 2 cells for more aggressive marking
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    // Skip diagonals to avoid marking too many cells
                    if (dx != 0 && dy != 0) continue;
                    Point neighbor = new Point(p.x + dx, p.y + dy);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor)) {
                        markUseless(neighbor);
                    }
                }
            }
        }
    }

    /**
     * Marks a cell as useless and adds it to the useless locations set.
     */
    private void markUseless(Point p) {
        boardState[p.x][p.y] = cellState.USELESS;
        uselessLocations.add(p);
    }

    /**
     * Marks cells perpendicular to the current ship orientation as useless.
     * Used to narrow down posible ship locations.
     */
    private void markPerpendicularCellsUseless(Point shot, boolean horizontal) {
        if (horizontal) {
            // Mark cells above and below the ship
            for (int i = -1; i <= 1; i++) {
                if (i == 0) continue; // Skip the ship itself
                Point cell = new Point(shot.x + i, shot.y);
                if (isValid(cell)) markUseless(cell);
            }
        } else {
            // Mark cells to the left and right of the ship
            for (int i = -1; i <= 1; i++) {
                if (i == 0) continue; // Skip the ship itself
                Point cell = new Point(shot.x, shot.y + i);
                if (isValid(cell)) markUseless(cell);
            }
        }
    }

    /**
     * Prints the current state of the board to debug.
     * Hits - (*), Misses - (m), Useless - (X).
     */
    public void printBoardState() {
        System.out.println("Current Board State:");
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                cellState cell = boardState[i][j];
                if (cell == null || cell == cellState.UNKNOWN) System.out.print("• ");
                else {
                    switch (cell) {
                        case HIT:
                            System.out.print("* ");
                            break;
                        case MISS:
                            System.out.print("m ");
                            break;
                        case USELESS:
                            System.out.print("X ");
                            break;
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Checks if a given point is within the board boundaries.
     */
    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }

    /**
     * Returns authors for assignment.
     */
    @Override
    public String getAuthors() {
        return "Nathan Sikkema and Brendan Dileo";
    }

    /**
     * Prints debug messages if debug mode is enabled.
     */
    private void debugPrint(String message) {
        if (debug) System.out.println("[DEBUG] " + message);
    }

    /**
     * Possible states of a cell on the board
     */
    private enum cellState {
        HIT, MISS, UNKNOWN, USELESS
    }
}
