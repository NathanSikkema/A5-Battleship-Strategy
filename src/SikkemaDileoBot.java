import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/** Adapted from code by Mark Yendt (ExampleBot.java), Mohawk College, December 2021
 * @author Nathan Sikkema
 * @author Brendan Dileo */


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
    private Point[] hitList;
    private int hitListSize;
    private cellState[][] boardState;
    private orientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;
    private ShipStatus[] shipStatuses;
    // Track remaining ship sizes for faster probability calculation
    private ArrayList<Integer> remainingShipSizes;


    // Point objects to be resued - garbage collection ?? Check this: https://stackoverflow.com/questions/40498096/is-everything-null-in-java-eligible-for-garbage-collection
    private Point tempPoint = new Point();
    private Point reusablePoint = new Point();
    private Point[] neighborPoints = new Point[4];

    // Pre allocating arrays for better time performance
    private int[][] probabilityMap;
    private boolean[][] shipCells;

    // Cache size as local var
    private int boardSize;

    /**
     * Initializes the bot with a new game instance.
     * Sets up all tracking variables and initializes the board state.
     */
    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        boardSize = size; // Cache size
        // Initialize boolean arrays instead of HashSets
        shotsFired = new boolean[size][size];
        uselessLocations = new boolean[size][size];
        targetQueue = new LinkedList<>();
        // Preallocate hitList array with total cells
        hitList = new Point[size * size];
        hitListSize = 0;
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
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
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
     *
     * shipSizes unused?
     */
    private int[][] buildProbabilityMap(int[] shipSizes, Point[] hitList, cellState[][] boardState) {
        // Clear the probability map more efficiently using Arrays.fill
        for (int[] row : probabilityMap) {
            Arrays.fill(row, 0);
        }
        
        // Store hitList size to avoid recalculation
        boolean isEarlyGame = hitListSize < 20;
        
        // Convert ArrayList to array for faster access
        int[] remainingShipsArray = remainingShipSizes.stream().mapToInt(Integer::intValue).toArray();
        
        // Pre-calculate ship size multipliers to avoid repeated calculations
        int[] baseScores = new int[remainingShipsArray.length];
        for (int i = 0; i < remainingShipsArray.length; i++) {
            int shipSize = remainingShipsArray[i];
            baseScores[i] = shipSize * 10;
            if (isEarlyGame) {
                baseScores[i] += shipSize * 5;
            }
        }
        
        // Use local reference to avoid field access overhead
        cellState[][] state = boardState;
        
        // Calculate base probabilities with higher weights for larger ships
        for (int i = 0; i < remainingShipsArray.length; i++) {
            int shipSize = remainingShipsArray[i];
            int baseScore = baseScores[i];
            
            // Horizontal placements
            for (int row = 0; row < boardSize; row++) {
                for (int col = 0; col <= boardSize - shipSize; col++) {
                    boolean canPlace = true;
                    int hitCount = 0;
                    
                    // Unroll loop for small ship sizes
                    if (shipSize <= 5) {
                        // Check first cell
                        cellState cell0 = state[row][col];
                        switch (cell0) {
                            case MISS:
                            case USELESS:
                                canPlace = false;
                                break;
                            case HIT:
                                hitCount++;
                                break;
                        }
                        
                        // Check remaining cells if needed
                        if (canPlace && shipSize > 1) {
                            cellState cell1 = state[row][col + 1];
                            switch (cell1) {
                                case MISS:
                                case USELESS:
                                    canPlace = false;
                                    break;
                                case HIT:
                                    hitCount++;
                                    break;
                            }
                            
                            if (canPlace && shipSize > 2) {
                                cellState cell2 = state[row][col + 2];
                                switch (cell2) {
                                    case MISS:
                                    case USELESS:
                                        canPlace = false;
                                        break;
                                    case HIT:
                                        hitCount++;
                                        break;
                                }
                                
                                if (canPlace && shipSize > 3) {
                                    cellState cell3 = state[row][col + 3];
                                    switch (cell3) {
                                        case MISS:
                                        case USELESS:
                                            canPlace = false;
                                            break;
                                        case HIT:
                                            hitCount++;
                                            break;
                                    }
                                    
                                    if (canPlace && shipSize > 4) {
                                        cellState cell4 = state[row][col + 4];
                                        switch (cell4) {
                                            case MISS:
                                            case USELESS:
                                                canPlace = false;
                                                break;
                                            case HIT:
                                                hitCount++;
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback for larger ships
                        for (int k = 0; k < shipSize; k++) {
                            cellState cell = state[row][col + k];
                            switch (cell) {
                                case MISS:
                                case USELESS:
                                    canPlace = false;
                                    break;
                                case HIT:
                                    hitCount++;
                                    break;
                            }
                            if (!canPlace) break;
                        }
                    }
                    
                    // Adds probability scores
                    if (canPlace) {
                        int score = baseScore + hitCount * 20;
                        
                        // Unroll the loop for smaller ship sizes
                        if (shipSize <= 5) {
                            probabilityMap[row][col] += score;
                            if (shipSize > 1) probabilityMap[row][col + 1] += score;
                            if (shipSize > 2) probabilityMap[row][col + 2] += score;
                            if (shipSize > 3) probabilityMap[row][col + 3] += score;
                            if (shipSize > 4) probabilityMap[row][col + 4] += score;
                        } else {
                            for (int k = 0; k < shipSize; k++) {
                                probabilityMap[row][col + k] += score;
                            }
                        }
                    }
                }
            }
            
            // Vertical placements
            for (int row = 0; row <= boardSize - shipSize; row++) {
                for (int col = 0; col < boardSize; col++) {
                    boolean canPlace = true;
                    int hitCount = 0;
                    
                    // Unroll loop for small ship sizes
                    if (shipSize <= 5) {
                        // Check first cell
                        cellState cell0 = state[row][col];
                        switch (cell0) {
                            case MISS:
                            case USELESS:
                                canPlace = false;
                                break;
                            case HIT:
                                hitCount++;
                                break;
                        }
                        
                        // Check remaining cells if needed
                        if (canPlace && shipSize > 1) {
                            cellState cell1 = state[row + 1][col];
                            switch (cell1) {
                                case MISS:
                                case USELESS:
                                    canPlace = false;
                                    break;
                                case HIT:
                                    hitCount++;
                                    break;
                            }
                            
                            if (canPlace && shipSize > 2) {
                                cellState cell2 = state[row + 2][col];
                                switch (cell2) {
                                    case MISS:
                                    case USELESS:
                                        canPlace = false;
                                        break;
                                    case HIT:
                                        hitCount++;
                                        break;
                                }
                                
                                if (canPlace && shipSize > 3) {
                                    cellState cell3 = state[row + 3][col];
                                    switch (cell3) {
                                        case MISS:
                                        case USELESS:
                                            canPlace = false;
                                            break;
                                        case HIT:
                                            hitCount++;
                                            break;
                                    }
                                    
                                    if (canPlace && shipSize > 4) {
                                        cellState cell4 = state[row + 4][col];
                                        switch (cell4) {
                                            case MISS:
                                            case USELESS:
                                                canPlace = false;
                                                break;
                                            case HIT:
                                                hitCount++;
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback for larger ships
                        for (int k = 0; k < shipSize; k++) {
                            cellState cell = state[row + k][col];
                            switch (cell) {
                                case MISS:
                                case USELESS:
                                    canPlace = false;
                                    break;
                                case HIT:
                                    hitCount++;
                                    break;
                            }
                            if (!canPlace) break;
                        }
                    }
                    
                    // Add probability scores
                    if (canPlace) {
                        int score = baseScore + hitCount * 20;
                        
                        // Unroll loop for small ship sizes
                        if (shipSize <= 5) {
                            probabilityMap[row][col] += score;
                            if (shipSize > 1) probabilityMap[row + 1][col] += score;
                            if (shipSize > 2) probabilityMap[row + 2][col] += score;
                            if (shipSize > 3) probabilityMap[row + 3][col] += score;
                            if (shipSize > 4) probabilityMap[row + 4][col] += score;
                        } else {
                            for (int k = 0; k < shipSize; k++) {
                                probabilityMap[row + k][col] += score;
                            }
                        }
                    }
                }
            }
        }
        
        // Optimize adjacent cell bonus calculation by avoiding diagonal checks
        for (int i = 0; i < hitListSize; i++) {
            Point hit = hitList[i];
            int hitX = hit.x;
            int hitY = hit.y;
            
            // Check horizontal neighbors
            for (int dy = -1; dy <= 1; dy += 2) {
                int newY = hitY + dy;
                if (newY >= 0 && newY < boardSize && state[hitX][newY] == cellState.UNKNOWN) {
                    probabilityMap[hitX][newY] += 30;
                    if (lastHit != null && hitOrientation == orientation.HORIZONTAL) {
                        probabilityMap[hitX][newY] += 50;
                    }
                }
            }
            
            // Check vertical neighbors
            for (int dx = -1; dx <= 1; dx += 2) {
                int newX = hitX + dx;
                if (newX >= 0 && newX < boardSize && state[newX][hitY] == cellState.UNKNOWN) {
                    probabilityMap[newX][hitY] += 30;
                    if (lastHit != null && hitOrientation == orientation.VERTICAL) {
                        probabilityMap[newX][hitY] += 50;
                    }
                }
            }
        }
        
        // Optimize miss penalty calculation by avoiding diagonal checks
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (state[i][j] == cellState.MISS) {
                    // Check horizontal neighbors
                    for (int dy = -1; dy <= 1; dy += 2) {
                        int newY = j + dy;
                        if (newY >= 0 && newY < boardSize && state[i][newY] == cellState.UNKNOWN) {
                            // Use ternary instead of Math.max
                            probabilityMap[i][newY] = probabilityMap[i][newY] > 10 ? 
                                probabilityMap[i][newY] - 10 : 0;
                        }
                    }
                    
                    // Check vertical neighbors
                    for (int dx = -1; dx <= 1; dx += 2) {
                        int newX = i + dx;
                        if (newX >= 0 && newX < boardSize && state[newX][j] == cellState.UNKNOWN) {
                            // Use ternary instead of Math.max
                            probabilityMap[newX][j] = probabilityMap[newX][j] > 10 ? 
                                probabilityMap[newX][j] - 10 : 0;
                        }
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
        int bestX = -1;
        int bestY = -1;

        // Use local references to avoid repeated field access
        boolean[][] localShotsFired = this.shotsFired;
        boolean[][] localUselessLocations = this.uselessLocations;

        // Process in blocks for better cache utilization
        final int BLOCK_SIZE = 4;
        for (int i = 0; i < size; i += BLOCK_SIZE) {
            for (int j = 0; j < size; j += BLOCK_SIZE) {
                for (int bi = i; bi < Math.min(i + BLOCK_SIZE, size); bi++) {
                    for (int bj = j; bj < Math.min(j + BLOCK_SIZE, size); bj++) {
                        if (!localShotsFired[bi][bj] && !localUselessLocations[bi][bj]) {
                            int prob = map[bi][bj];
                            if (prob > highestProbability) {
                                highestProbability = prob;
                                bestX = bi;
                                bestY = bj;
                            }
                        }
                    }
                }
            }
        }

        return highestProbability > 0 ? new Point(bestX, bestY) : null;
    }

    /**
     * Determines the coordinates of a ship based on a hit.
     * Tracks the ship's orientation and all cells that are part of it.
     */
    private void findShipCoordinates(Point p) {
        ArrayList<Point> shipCoordinates = new ArrayList<>();
        shipCoordinates.add(p);
        orientation targetBoatOrientation = orientation.UNKNOWN;

        // Use local reference to avoid repeated field access
        cellState[][] localBoardState = this.boardState;

        // Try to find a direction with another HIT adjacent
        for (Point dir : directions) {
            int newX = p.x + dir.x;
            int newY = p.y + dir.y;
            if (isValid(newX, newY) && localBoardState[newX][newY] == cellState.HIT) {
                // Set orientation based on direction
                targetBoatOrientation = (dir.x != 0) ? orientation.VERTICAL : orientation.HORIZONTAL;

                // Traverse in both directions along the identified direction
                for (int mult = -1; mult <= 1; mult += 2) {
                    int dx = dir.x * mult;
                    int dy = dir.y * mult;
                    int currX = p.x + dx;
                    int currY = p.y + dy;

                    while (isValid(currX, currY) && localBoardState[currX][currY] == cellState.HIT) {
                        shipCoordinates.add(new Point(currX, currY));
                        currX += dx;
                        currY += dy;
                    }
                }

                // Check if we have enough hits to match a ship size
                int shipLength = shipCoordinates.size();

                // First check if this matches any unsunk ship size
                for (ShipStatus s : shipStatuses) {
                    if (!s.isSunk() && s.getSize() == shipLength) {
                        updateShipStatus(shipCoordinates, targetBoatOrientation);
                        return;
                    }
                }

                // If we don't have a match yet, check if we need to look further
                for (int mult = -1; mult <= 1; mult += 2) {
                    int dx = dir.x * mult;
                    int dy = dir.y * mult;
                    int currX = p.x + dx;
                    int currY = p.y + dy;

                    // Look ahead one more cell if we haven't found a match
                    if (isValid(currX, currY) && localBoardState[currX][currY] == cellState.UNKNOWN) {
                        // Check if adding this cell would match a ship size
                        for (ShipStatus s : shipStatuses) {
                            if (!s.isSunk() && s.getSize() == shipLength + 1) {
                                // Add this cell to our target queue with high priority
                                targetQueue.add(new Point(currX, currY));
                                break;
                            }
                        }
                    }
                }

                updateShipStatus(shipCoordinates, targetBoatOrientation);
                return;
            }
        }

        // If no direction found, it's a single-point ship (unlikely with size ≥ 2)
        updateShipStatus(shipCoordinates, targetBoatOrientation);
    }

    /**
     * Updates the status of a ship when it's sunk.
     * Marks adjacent cells as useless and updates ship tracking.
     */
    private void updateShipStatus(ArrayList<Point> shipCoordinates, orientation o) {
        ArrayList<Point> sunkShipNeighbors = new ArrayList<>();

        // Use local references to avoid repeated field access
        cellState[][] localBoardState = this.boardState;
        boolean[][] localUselessLocations = this.uselessLocations;

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
            if (isValid(n) && localBoardState[n.x][n.y] != cellState.HIT) {
                localBoardState[n.x][n.y] = cellState.USELESS;
                localUselessLocations[n.x][n.y] = true;
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
            addToHitList(shot);
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
                Point firstHit = getHitAt(hitListSize - consecutiveHits);
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
        for (int i = hitListSize - consecutiveHits; i < hitListSize; i++) {
            sunkShipPoints.add(hitList[i]);
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

    // Update other methods to use hitList array instead of ArrayList
    private void addToHitList(Point p) {
        hitList[hitListSize++] = p;
    }

    private Point getLastHit() {
        return hitListSize > 0 ? hitList[hitListSize - 1] : null;
    }

    private Point getHitAt(int index) {
        return index < hitListSize ? hitList[index] : null;
    }
}