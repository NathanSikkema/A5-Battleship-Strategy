
import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
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
    // Changed HashSet with boolean arrays for faster lookups
    private boolean[][] shotsFired;
    private boolean[][] uselessLocations;
    private Queue<Point> targetQueue;
    private ArrayList<Point> hitList;
    private cellState[][] boardState;
    private orientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;
    private ShipStatus[] shipStatuses;
    // Track remaining ship sizes for faster probability calculation
    private ArrayList<Integer> remainingShipSizes;

    // Reusable Point objects to reduce garbage collection
    private Point tempPoint = new Point();
    private Point reusablePoint = new Point();
    private Point[] neighborPoints = new Point[4];

    // Pre-allocated arrays for better performance
    private int[][] probabilityMap;
    private boolean[][] shipCells;

    /**
     * Initializes the bot with a new game instance.
     * Sets up all tracking variables and initializes the board state.
     */
    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        // Initialize boolean arrays instead of HashSets
        shotsFired = new boolean[size][size];
        uselessLocations = new boolean[size][size];
        targetQueue = new LinkedList<>();
        hitList = new ArrayList<>();
        boardState = new cellState[size][size];
        hitOrientation = orientation.UNKNOWN;
        lastHit = null;
        consecutiveHits = 0;
        remainingShipSizes = new ArrayList<>();

        // Initialize reusable arrays
        probabilityMap = new int[size][size];
        shipCells = new boolean[size][size];

        // Initialize neighbor points
        for (int i = 0; i < 4; i++) {
            neighborPoints[i] = new Point();
        }

        // Initialize board state to UNKNOWN
        // All cells unknown to start
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                boardState[i][j] = cellState.UNKNOWN;

        // Initialize ship status tracking
        shipStatuses = new ShipStatus[battleShip.getShipSizes().length];
        for (int i = 0; i < battleShip.getShipSizes().length; i++) {
            shipStatuses[i] = new ShipStatus(battleShip.getShipSizes()[i]);
            remainingShipSizes.add(battleShip.getShipSizes()[i]);
        }
    }

    /**
     * Builds a probability map for potential ship locations.
     * Optimized version with reduced object creation.
     */
    private int[][] buildProbabilityMap(int[] shipSizes, ArrayList<Point> hitList, cellState[][] boardState) {
        // Clear the probability map
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                probabilityMap[i][j] = 0;
            }
        }

        // Calculate base probabilities with higher weights for larger ships
        for (int shipSize : remainingShipSizes) {
            // Horizontal placements
            for (int i = 0; i < size; i++) {
                for (int j = 0; j <= size - shipSize; j++) {
                    boolean canPlace = true;
                    int hitCount = 0;

                    // Quick check for invalid placements
                    for (int k = 0; k < shipSize; k++) {
                        if (boardState[i][j + k] == cellState.MISS || boardState[i][j + k] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        if (boardState[i][j + k] == cellState.HIT) {
                            hitCount++;
                        }
                    }

                    // Add probability scores with simplified weights
                    if (canPlace) {
                        // Simplified scoring - focus on ship size and hits
                        int baseScore = shipSize * 10 + hitCount * 20;

                        // Add early game bonus for larger ships
                        if (hitList.size() < 20) {
                            baseScore += shipSize * 5;
                        }

                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i][j + k] += baseScore;
                        }
                    }
                }
            }

            // Vertical placements
            for (int i = 0; i <= size - shipSize; i++) {
                for (int j = 0; j < size; j++) {
                    // Track if we can place and num of hits
                    boolean canPlace = true;
                    int hitCount = 0;

                    // Quick check for invalid placements
                    for (int k = 0; k < shipSize; k++) {
                        if (boardState[i + k][j] == cellState.MISS || boardState[i + k][j] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        // Check for hit, increment count if hit
                        if (boardState[i + k][j] == cellState.HIT) {
                            hitCount++;
                        }
                    }

                    // Add probability scores with simplified weights
                    if (canPlace) {
                        // Simplified scoring - focus on ship size and hits
                        int baseScore = shipSize * 10 + hitCount * 20;

                        // Add early game bonus for larger ships
                        if (hitList.size() < 20) {
                            baseScore += shipSize * 5;
                        }

                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i + k][j] += baseScore;
                        }
                    }
                }
            }
        }

        // Adds a high bonus for cells adjacent to hits
        for (Point hit : hitList) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx != 0 && dy != 0) continue; // Skip diagonals
                    int newX = hit.x + dx;
                    int newY = hit.y + dy;
                    if (isValid(newX, newY) && boardState[newX][newY] == cellState.UNKNOWN) {
                        probabilityMap[newX][newY] += 30; // Base bonus for adjacent cells

                        // Additional bonus if this cell could complete a ship
                        if (lastHit != null && hitOrientation != orientation.UNKNOWN) {
                            if ((hitOrientation == orientation.HORIZONTAL && dx == 0) ||
                                    (hitOrientation == orientation.VERTICAL && dy == 0)) {
                                probabilityMap[newX][newY] += 50; // Higher bonus for potential ship completion
                            }
                        }
                    }
                }
            }
        }

        // Decrease probability around misses
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (boardState[i][j] == cellState.MISS) {
                    for (Point dir : directions) {
                        int x = i + dir.x;
                        int y = j + dir.y;
                        if (isValid(x, y) && boardState[x][y] == cellState.UNKNOWN)
                            probabilityMap[x][y] = Math.max(0, probabilityMap[x][y] - 10);
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
        int highestProbability = -1;
        reusablePoint.setLocation(-1, -1);

        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                if (shotsFired[i][j] || uselessLocations[i][j]) continue;

                if (map[i][j] > highestProbability) {
                    highestProbability = map[i][j];
                    reusablePoint.setLocation(i, j);
                }
            }
        }
        
        return highestProbability > 0 ? new Point(reusablePoint) : null;
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
            int newX = p.x + dir.x;
            int newY = p.y + dir.y;
            if (isValid(newX, newY) && boardState[newX][newY] == cellState.HIT) {
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
            int newX = p.x + dx;
            int newY = p.y + dy;

            while (isValid(newX, newY) && boardState[newX][newY] == cellState.HIT) {
                shipCoordinates.add(new Point(newX, newY));
                newX += dx;
                newY += dy;
            }
        }

        // Check if we have enough hits to match a ship size
        int shipLength = shipCoordinates.size();
        boolean foundMatchingShip = false;

        // First check if this matches any unsunk ship size
        for (ShipStatus s : shipStatuses) {
            if (!s.isSunk() && s.getSize() == shipLength) {
                foundMatchingShip = true;
                break;
            }
        }

        // If we don't have a match yet, check if we need to look further
        if (!foundMatchingShip) {
            // Check if we need to look further in the direction
            // This helps with ships that are partially hit
            for (int mult = -1; mult <= 1; mult += 2) {
                int dx = shipDirection.x * mult;
                int dy = shipDirection.y * mult;
                int newX = p.x + dx;
                int newY = p.y + dy;

                // Look ahead one more cell if we haven't found a match
                if (isValid(newX, newY) && boardState[newX][newY] == cellState.UNKNOWN) {
                    // Check if adding this cell would match a ship size
                    for (ShipStatus s : shipStatuses) {
                        if (!s.isSunk() && s.getSize() == shipLength + 1) {
                            // Add this cell to our target queue with high priority
                            targetQueue.add(new Point(newX, newY));
                            break;
                        }
                    }
                }
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
                
                // Update remaining ship sizes
                remainingShipSizes.remove(Integer.valueOf(s.getSize()));
                break;
            }
        }
        if (debug) System.out.print("Marking cells useless next to sunk boat: ");
        for (Point n : sunkShipNeighbors) {
            if (isValid(n) && boardState[n.x][n.y] != cellState.HIT) {
                boardState[n.x][n.y] = cellState.USELESS;
                uselessLocations[n.x][n.y] = true;
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
            if (nextCell != null && !shotsFired[nextCell.x][nextCell.y] && !uselessLocations[nextCell.x][nextCell.y]) {
                shot = nextCell;
            } else {
                // If we can't continue in that direction, try the opposite direction
                Point oppositeCell = getOppositeDirection(lastHit);
                if (oppositeCell != null && !shotsFired[oppositeCell.x][oppositeCell.y] && !uselessLocations[oppositeCell.x][oppositeCell.y]) {
                    shot = oppositeCell;
                }
            }
        }

        // If no shot from completing a ship, check target queue against probability map
        if (shot == null && !targetQueue.isEmpty()) {
            // Sort the queue by probability - limit to top 10 cells for better performance
            ArrayList<Point> sortedQueue = new ArrayList<>(Math.min(targetQueue.size(), 10));
            for (Point p : targetQueue) {
                if (!shotsFired[p.x][p.y] && !uselessLocations[p.x][p.y]) {
                    sortedQueue.add(p);
                    if (sortedQueue.size() >= 10) break;
                }
            }
            
            // Sort by probability
            sortedQueue.sort((a, b) -> Integer.compare(
                    probabilityMap[b.x][b.y], probabilityMap[a.x][a.y]
            ));
            
            // Take the highest-probability shot
            if (!sortedQueue.isEmpty()) {
                shot = sortedQueue.get(0);
                targetQueue.remove(shot);
            }
        }

        // If still no shot, use highest probability from map
        if (shot == null) {
            shot = findHighestProbability(probabilityMap);
        }

        // If still no shot, use a more efficient search pattern optimized for the specific ship sizes
        if (shot == null) {
            // Use a pattern optimized for the specific ship sizes (6,5,4,4,3,2)
            // Start with a pattern that's good for finding the largest ships first
            int[] shipSizes = battleShip.getShipSizes();
            int maxShipSize = 0;
            for (int size : shipSizes) {
                if (size > maxShipSize) maxShipSize = size;
            }

            // Improved pattern for 15x15 board with ships 6,5,4,4,3,2
            // Use a checkerboard pattern with spacing based on the largest ship
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    // Checkerboard pattern with spacing based on largest ship
                    // This ensures we don't miss any potential ship locations
                    if ((i % 3 == 0 || j % 3 == 0) && (i + j) % 2 == 0) {
                        if (!shotsFired[i][j] && !uselessLocations[i][j]) {
                            shot = new Point(i, j);
                        }
                    }
                }
            }

            // If still no shot, try a more aggressive pattern
            if (shot == null) {
                for (int i = 0; i < size && shot == null; i++) {
                    for (int j = 0; j < size && shot == null; j++) {
                        // Use a more aggressive pattern that covers more of the board
                        // Prioritize cells that are more likely to contain ships
                        if ((i + j) % 2 == 0) {
                            if (!shotsFired[i][j] && !uselessLocations[i][j]) {
                                shot = new Point(i, j);
                            }
                        }
                    }
                }
            }
        }

        // Safety check - if still no shot, find any unshot cell
        if (shot == null) {
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    if (!shotsFired[i][j] && !uselessLocations[i][j]) {
                        shot = new Point(i, j);
                    }
                }
            }
        }

        // Final safety check - if still no shot, use a default position
        if (shot == null) {
            // This should never happen, but just in case
            shot = new Point(0, 0);
        }

        // Fire the shot
        shotsFired[shot.x][shot.y] = true;
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

                // Add neighbors in order of probability
                for (Point dir : orderedDirs) {
                    int newX = shot.x + dir.x;
                    int newY = shot.y + dir.y;
                    if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY]) {
                        // Only add if it has a reasonable probability
                        if (probabilityMap[newX][newY] > 0) {
                            targetQueue.add(new Point(newX, newY));
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
            int newX = current.x;
            int newY = current.y + 1;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);

            newY = current.y - 1;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);
        } else if (hitOrientation == orientation.VERTICAL) {
            int newX = current.x + 1;
            int newY = current.y;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);

            newX = current.x - 1;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);
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
            int newX = firstHit.x;
            int newY = firstHit.y + direction;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY]) {
                return new Point(newX, newY);
            }
        } else if (hitOrientation == orientation.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1 : 1;
            int newX = firstHit.x + direction;
            int newY = firstHit.y;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY]) {
                return new Point(newX, newY);
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

        // Clear the ship cells array
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                shipCells[i][j] = false;
            }
        }

        // Mark ship cells
        for (Point p : sunkShipPoints) {
            shipCells[p.x][p.y] = true;
        }

        // Mark cells around the ship
        for (Point p : sunkShipPoints) {
            // Mark cells that are adjacent to the sunk ship
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    // Skip diagonals as ships can touch diagonally
                    if (dx != 0 && dy != 0) continue;
                    int newX = p.x + dx;
                    int newY = p.y + dy;
                    if (isValid(newX, newY) && !shotsFired[newX][newY]) {
                        markUseless(newX, newY);
                    }
                }
            }
        }

        // More aggressive marking for the largest ships
        // For ships of size 5 or 6, mark an additional cell in each direction
        int sunkShipSize = consecutiveHits;
        if (sunkShipSize >= 5) {
            for (Point p : sunkShipPoints) {
                // Determine the orientation of the sunk ship
                boolean isHorizontal = false;
                if (sunkShipPoints.size() > 1) {
                    Point first = sunkShipPoints.get(0);
                    Point second = sunkShipPoints.get(1);
                    isHorizontal = first.x == second.x;
                }

                // Mark additional cells based on orientation
                if (isHorizontal) {
                    // Mark cells to the left and right
                    for (int dy = -3; dy <= 3; dy += 6) {
                        int newX = p.x;
                        int newY = p.y + dy;
                        if (isValid(newX, newY) && !shotsFired[newX][newY]) {
                            markUseless(newX, newY);
                        }
                    }
                } else {
                    // Mark cells above and below
                    for (int dx = -3; dx <= 3; dx += 6) {
                        int newX = p.x + dx;
                        int newY = p.y;
                        if (isValid(newX, newY) && !shotsFired[newX][newY]) {
                            markUseless(newX, newY);
                        }
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
        uselessLocations[p.x][p.y] = true;
    }
    
    /**
     * Overloaded version that takes x,y coordinates directly
     */
    private void markUseless(int x, int y) {
        boardState[x][y] = cellState.USELESS;
        uselessLocations[x][y] = true;
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
                int newX = shot.x + i;
                int newY = shot.y;
                if (isValid(newX, newY)) markUseless(newX, newY);
            }
        } else {
            // Mark cells to the left and right of the ship
            for (int i = -1; i <= 1; i++) {
                if (i == 0) continue; // Skip the ship itself
                int newX = shot.x;
                int newY = shot.y + i;
                if (isValid(newX, newY)) markUseless(newX, newY);
            }
        }
    }

    /**
     * Checks if a given point is within the board boundaries.
     */
    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }
    
    /**
     * Overloaded version that takes x,y coordinates directly
     */
    private boolean isValid(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    /**
     * Returns authors for assignment.
     */
    @Override
    public String getAuthors() {
        return "Nathan Sikkema and Brendan Dileo";
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