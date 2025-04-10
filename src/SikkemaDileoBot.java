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
    // Changed HashSet to use boolean arrays for faster lookups
    private boolean[][] shotsFired;
    private boolean[][] uselessLocations;
    private Queue<Point> targetQueue;
    // Array of points to track list of hits
    private Point[] hitList;
    private int hitListSize;
    private cellState[][] boardState;
    private orientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;
    private ShipStatus[] shipStatuses;
    // Track the remaining ship sizes for faster probability calculation
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
     *
     * @param battleShip2 The Battleship2 object from the Battleship API needed to run the game.
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
     *
     * @param shipSizes Array containijg the remaining ships.
     * @param hitList Array of points containing cells that have been hit.
     * @param boardState An array representing the current state of the board.
     * @return Array containing probability map.
     */
    private int[][] buildProbabilityMap(int[] shipSizes, Point[] hitList, cellState[][] boardState) {
        // Clear the probability map using Arrays.fill
        for (int[] row : probabilityMap) {
            Arrays.fill(row, 0);
        }

        // Pre-calculate ship size multipliers and cache board size
        final int boardSize = this.boardSize;
        final int[] baseScores = new int[remainingShipSizes.size()];
        final int hitListSize = this.hitListSize;

        // Precompute base scores
        for (int i = 0; i < remainingShipSizes.size(); i++) {
            int shipSize = remainingShipSizes.get(i);
            baseScores[i] = shipSize * 10 + (hitListSize < 20 ? shipSize * 5 : 0);
        }

        // Calculate base probabilities
        for (int i = 0; i < remainingShipSizes.size(); i++) {
            int shipSize = remainingShipSizes.get(i);
            int baseScore = baseScores[i];

            // Horizontal placements
            for (int row = 0; row < boardSize; row++) {
                cellState[] rowState = boardState[row];
                for (int col = 0; col <= boardSize - shipSize; col++) {
                    boolean canPlace = true;
                    int hitCount = 0;

                    for (int k = 0; k < shipSize; k++) {
                        cellState cell = rowState[col + k];
                        if (cell == cellState.MISS || cell == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        if (cell == cellState.HIT) hitCount++;
                    }

                    if (canPlace) {
                        int score = baseScore + hitCount * 20;
                        int[] probRow = probabilityMap[row];
                        for (int k = 0; k < shipSize; k++) {
                            probRow[col + k] += score;
                        }
                    }
                }
            }

            // Vertical placements
            for (int col = 0; col < boardSize; col++) {
                for (int row = 0; row <= boardSize - shipSize; row++) {
                    boolean canPlace = true;
                    int hitCount = 0;

                    for (int k = 0; k < shipSize; k++) {
                        cellState cell = boardState[row + k][col];
                        if (cell == cellState.MISS || cell == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                        if (cell == cellState.HIT) hitCount++;
                    }

                    if (canPlace) {
                        int score = baseScore + hitCount * 20;
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[row + k][col] += score;
                        }
                    }
                }
            }
        }

        // Optimize adjacent cell bonus calculation
        final Point lastHit = this.lastHit;
        final orientation hitOrientation = this.hitOrientation;

        for (int i = 0; i < hitListSize; i++) {
            Point hit = hitList[i];
            int hitX = hit.x;
            int hitY = hit.y;

            // Check horizontal neighbors
            for (int dy = -1; dy <= 1; dy += 2) {
                int newY = hitY + dy;
                if (newY >= 0 && newY < boardSize && boardState[hitX][newY] == cellState.UNKNOWN) {
                    probabilityMap[hitX][newY] += 30;
                    if (lastHit != null && hitOrientation == orientation.HORIZONTAL) {
                        probabilityMap[hitX][newY] += 50;
                    }
                }
            }

            // Check vertical neighbors
            for (int dx = -1; dx <= 1; dx += 2) {
                int newX = hitX + dx;
                if (newX >= 0 && newX < boardSize && boardState[newX][hitY] == cellState.UNKNOWN) {
                    probabilityMap[newX][hitY] += 30;
                    if (lastHit != null && hitOrientation == orientation.VERTICAL) {
                        probabilityMap[newX][hitY] += 50;
                    }
                }
            }
        }

        return probabilityMap;
    }

    /**
     * Finds the cell with the highest probability score that hasn't been shot at.
     *
     * @param map
     * @return
     */
    private Point findHighestProbability(int[][] map) {
        int highestProbability = -1;
        int bestX = -1;
        int bestY = -1;
        final int size = this.size;
        final boolean[][] shotsFired = this.shotsFired;
        final boolean[][] uselessLocations = this.uselessLocations;

        // Process in blocks for better cache utilization
        final int BLOCK_SIZE = 8; // Increased block size for better cache utilization
        for (int i = 0; i < size; i += BLOCK_SIZE) {
            for (int j = 0; j < size; j += BLOCK_SIZE) {
                int endI = Math.min(i + BLOCK_SIZE, size);
                int endJ = Math.min(j + BLOCK_SIZE, size);

                for (int bi = i; bi < endI; bi++) {
                    boolean[] shotsRow = shotsFired[bi];
                    boolean[] uselessRow = uselessLocations[bi];
                    int[] mapRow = map[bi];

                    for (int bj = j; bj < endJ; bj++) {
                        if (!shotsRow[bj] && !uselessRow[bj]) {
                            int prob = mapRow[bj];
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
     *
     * @param p
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
     *
     * @param shipCoordinates
     * @param o
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

        // Build probability map first      -        TODO: Should we only build it if needed?
        int[][] probabilityMap = buildProbabilityMap(battleShip.getShipSizes(), hitList, boardState);

        // If we have a hit and know the orientation, prioritize completing the ship
        if (lastHit != null && hitOrientation != orientation.UNKNOWN) {
            Point nextCell = getNextCellInDirection(lastHit);
            if (nextCell != null && !shotsFired[nextCell.x][nextCell.y] && !uselessLocations[nextCell.x][nextCell.y]) {
                shot = nextCell;
            } else {
                Point oppositeCell = getOppositeDirection(lastHit);
                if (oppositeCell != null && !shotsFired[oppositeCell.x][oppositeCell.y] && !uselessLocations[oppositeCell.x][oppositeCell.y]) {
                    shot = oppositeCell;
                }
            }
        }

        // If no shot from completing a ship, check target queue against probability map
        if (shot == null && !targetQueue.isEmpty()) {
            Point[] sortedQueue = new Point[Math.min(targetQueue.size(), 10)];
            int index = 0;
            for (Point p : targetQueue) {
                if (!shotsFired[p.x][p.y] && !uselessLocations[p.x][p.y]) {
                    sortedQueue[index++] = p;
                    if (index >= 10) break;
                }
            }

            // Sort by probability
            Arrays.sort(sortedQueue, 0, index, (a, b) -> Integer.compare(probabilityMap[b.x][b.y], probabilityMap[a.x][a.y]));

            // Take the highest-probability shot
            if (index > 0) {
                shot = sortedQueue[0];
                targetQueue.remove(shot);
            }
        }

        // If still no shot, use highest probability from map
        if (shot == null) {
            shot = findHighestProbability(probabilityMap);
        }

        // If still no shot, use a more efficient search pattern
        if (shot == null) {
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    if (!shotsFired[i][j] && !uselessLocations[i][j]) {
                        shot = new Point(i, j);
                    }
                }
            }
        }

        // Fire the shot
        shotsFired[shot.x][shot.y] = true;
        boolean hit = battleShip.shoot(shot);
        boardState[shot.x][shot.y] = hit ? cellState.HIT : cellState.MISS;

        // Update game state
        if (hit) {
            addToHitList(shot);
            consecutiveHits++;

            if (battleShip.numberOfShipsSunk() > previousSunkShips) {
                findShipCoordinates(shot);
                targetQueue.clear();
                lastHit = null;
                hitOrientation = orientation.UNKNOWN;
                consecutiveHits = 0;
                markSunkShipCells();
            } else if (lastHit == null) {
                lastHit = shot;
                for (Point dir : directions) {
                    int newX = shot.x + dir.x;
                    int newY = shot.y + dir.y;
                    if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY]) {
                        if (probabilityMap[newX][newY] > 0) {
                            targetQueue.add(new Point(newX, newY));
                        }
                    }
                }
            } else {
                if (shot.x == lastHit.x) {
                    hitOrientation = orientation.HORIZONTAL;
                    markPerpendicularCellsUseless(shot, true);
                } else if (shot.y == lastHit.y) {
                    hitOrientation = orientation.VERTICAL;
                    markPerpendicularCellsUseless(shot, false);
                }
                if (hitOrientation != orientation.UNKNOWN) {
                    Point nextCell = getNextCellInDirection(shot);
                    if (nextCell != null) {
                        targetQueue.add(nextCell);
                    }
                }
                lastHit = shot;
            }
        } else {
            if (consecutiveHits > 0 && hitOrientation != orientation.UNKNOWN) {
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
            Arrays.fill(shipCells[i], false);
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
     *
     * @param x
     * @param y
     */
    private void markUseless(int x, int y) {
        boardState[x][y] = cellState.USELESS;
        uselessLocations[x][y] = true;
    }

    /**
     * Marks cells perpendicular to the current ship orientation as useless.
     * Used to narrow down posible ship locations.
     *
     * @param shot
     * @param horizontal
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

    /**
     * Update other methods to use hitList array instead of ArrayList
     * Optimizatiuon?
     * @param p
     */
    private void addToHitList(Point p) {
        hitList[hitListSize++] = p;
    }

    /**
     *
     * TODO: UNUSED BUT KEEP ---> GARBAGE*
     * @return
     */
    private Point getLastHit() {
        return hitListSize > 0 ? hitList[hitListSize - 1] : null;
    }

    /**
     *
     * @param index
     * @return
     */
    private Point getHitAt(int index) {
        return index < hitListSize ? hitList[index] : null;
    }
}