import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 Adapted from code by Mark Yendt (ExampleBot.java), Mohawk College, December 2021

 @author Nathan Sikkema
 @author Brendan Dileo */

public class SikkemaDileoBot implements BattleShipBot {
    private final Point[] directions = {new Point(0, 1), new Point(0, -1), new Point(1, 0), new Point(-1, 0)};
    private int size;
    private BattleShip2 battleShip;
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
    private ArrayList<Integer> remainingShipSizes;
    private int[][] probabilityMap;
    private boolean[][] shipCells;
    private int boardSize;

    /**
     * Initializes the bot with a new game instance and resets all tracking variables.
     * Sets up the game board, initializes tracking arrays, and prepares the targeting system.
     * This method is called once at the start of each new game.
     *
     * @param battleShip2 The BattleShip game instance that this bot will interact with
     */
    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        boardSize = size;
        shotsFired = new boolean[size][size];
        uselessLocations = new boolean[size][size];
        targetQueue = new LinkedList<>();
        hitList = new Point[size * size];
        hitListSize = 0;
        boardState = new cellState[size][size];
        hitOrientation = orientation.UNKNOWN;
        lastHit = null;
        consecutiveHits = 0;
        remainingShipSizes = new ArrayList<>();

        probabilityMap = new int[size][size];
        shipCells = new boolean[size][size];

        for (int i = 0; i < boardSize; i++) for (int j = 0; j < boardSize; j++) boardState[i][j] = cellState.UNKNOWN;

        shipStatuses = new ShipStatus[battleShip.getShipSizes().length];
        for (int i = 0; i < battleShip.getShipSizes().length; i++) {
            shipStatuses[i] = new ShipStatus(battleShip.getShipSizes()[i]);
            remainingShipSizes.add(battleShip.getShipSizes()[i]);
        }
    }

    /**
     * Calculates probability scores for each cell on the board based on possible ship placements.
     * The probability calculation considers:
     * - Remaining ship sizes
     * - Previous hits and misses
     * - Ship orientation patterns
     * - Adjacent cells to confirmed hits
     * Higher scores indicate cells more likely to contain ships, with bonuses applied for:
     * - Cells that could fit larger ships
     * - Cells adjacent to confirmed hits
     * - Cells aligned with suspected ship orientations
     *
     * @param hitList Array of points where successful hits have been recorded
     * @param boardState Current state of each cell on the game board
     * @return Updated probability map with scores for each cell
     */
    private int[][] buildProbabilityMap(Point[] hitList, cellState[][] boardState) {
        for (int[] row : probabilityMap) Arrays.fill(row, 0);
        final int boardSize = this.boardSize;
        final int[] baseScores = new int[remainingShipSizes.size()];
        final int hitListSize = this.hitListSize;
        for (int i = 0; i < remainingShipSizes.size(); i++) {
            int shipSize = remainingShipSizes.get(i);
            baseScores[i] = shipSize * 10 + (hitListSize < 20 ? shipSize * 5:0);
        }
        for (int i = 0; i < remainingShipSizes.size(); i++) {
            int shipSize = remainingShipSizes.get(i);
            int baseScore = baseScores[i];
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
                    if (lastHit != null && hitOrientation == orientation.HORIZONTAL) probabilityMap[hitX][newY] += 50;
                }
            }

            // Check vertical neighbors
            for (int dx = -1; dx <= 1; dx += 2) {
                int newX = hitX + dx;
                if (newX >= 0 && newX < boardSize && boardState[newX][hitY] == cellState.UNKNOWN) {
                    probabilityMap[newX][hitY] += 30;
                    if (lastHit != null && hitOrientation == orientation.VERTICAL) probabilityMap[newX][hitY] += 50;
                }
            }
        }
        return probabilityMap;
    }

    /**
     * Searches the probability map to find the cell with the highest probability score.
     * Uses block-based scanning for improved performance, checking 8x8 blocks at a time.
     * Only considers cells that haven't been shot at and aren't marked as useless.
     *
     * @param map The probability map containing scores for each cell
     * @return Point representing the coordinates of the highest probability cell, 
     *         or null if no valid cells are found
     */
    private Point findHighestProbability(int[][] map) {
        int highestProbability = -1;
        int bestX = -1;
        int bestY = -1;
        final int size = this.size;
        final boolean[][] shotsFired = this.shotsFired;
        final boolean[][] uselessLocations = this.uselessLocations;
        final int BLOCK_SIZE = 8;
        for (int i = 0; i < size; i += BLOCK_SIZE)
            for (int j = 0; j < size; j += BLOCK_SIZE) {
                int endI = Math.min(i + BLOCK_SIZE, size);
                int endJ = Math.min(j + BLOCK_SIZE, size);
                for (int bi = i; bi < endI; bi++) {
                    boolean[] shotsRow = shotsFired[bi];
                    boolean[] uselessRow = uselessLocations[bi];
                    int[] mapRow = map[bi];
                    for (int bj = j; bj < endJ; bj++)
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
        return highestProbability > 0 ? new Point(bestX, bestY):null;
    }

    /**
     * Identifies all coordinates belonging to a ship after it has been sunk.
     * Starting from a hit point, searches in all cardinal directions to find connected hits
     * that form the complete ship. Once found, updates the ship status and marks adjacent
     * cells as useless for future targeting.
     * If a partial ship is found that matches a known ship size, adds potential remaining
     * coordinates to the target queue for future shots.
     *
     * @param p The point from which to start searching for the ship's coordinates
     */
    private void findShipCoordinates(Point p) {
        ArrayList<Point> shipCoordinates = new ArrayList<>();
        shipCoordinates.add(p);
        cellState[][] localBoardState = this.boardState;
        for (Point dir : directions) {
            int newX = p.x + dir.x;
            int newY = p.y + dir.y;
            if (isValid(newX, newY) && localBoardState[newX][newY] == cellState.HIT) {
                for (int i = -1; i <= 1; i += 2) {
                    int dx = dir.x * i;
                    int dy = dir.y * i;
                    int currX = p.x + dx;
                    int currY = p.y + dy;
                    while (isValid(currX, currY) && localBoardState[currX][currY] == cellState.HIT) {
                        shipCoordinates.add(new Point(currX, currY));
                        currX += dx;
                        currY += dy;
                    }
                }
                int shipLength = shipCoordinates.size();
                for (ShipStatus s : shipStatuses)
                    if (s.isSunk() && s.getSize() == shipLength) {
                        updateShipStatus(shipCoordinates);
                        return;
                    }
                for (int i = -1; i <= 1; i += 2) {
                    int dx = dir.x * i;
                    int dy = dir.y * i;
                    int currX = p.x + dx;
                    int currY = p.y + dy;
                    if (isValid(currX, currY) && localBoardState[currX][currY] == cellState.UNKNOWN)
                        for (ShipStatus s : shipStatuses)
                            if (s.isSunk() && s.getSize() == shipLength + 1) {
                                targetQueue.add(new Point(currX, currY));
                                break;
                            }
                }
                updateShipStatus(shipCoordinates);
                return;
            }
        }
        updateShipStatus(shipCoordinates);
    }

    /**
     * Updates the status of a ship after it has been identified as sunk.
     * This method will:
     * - Updates the ship's hit coordinates
     * - Marks the ship as sunk
     * - Removes the ship's size from remaining sizes
     * - Marks all adjacent cells as useless for targeting
     * - Updates the board state for neighboring cells
     *
     * @param shipCoordinates List of coordinates that make up the sunk ship
     */
    private void updateShipStatus(ArrayList<Point> shipCoordinates) {
        ArrayList<Point> sunkShipNeighbors = new ArrayList<>();
        cellState[][] localBoardState = this.boardState;
        boolean[][] localUselessLocations = this.uselessLocations;

        for (ShipStatus s : shipStatuses) {
            if (s.isSunk() && s.getSize() == shipCoordinates.size()) {
                s.setHitCoordinates(shipCoordinates);
                s.setSunk(true);
                sunkShipNeighbors = s.getNeighbors();
                remainingShipSizes.remove(Integer.valueOf(s.getSize()));
                break;
            }
        }

        for (Point n : sunkShipNeighbors)
            if (isValid(n) && localBoardState[n.x][n.y] != cellState.HIT) {
                localBoardState[n.x][n.y] = cellState.USELESS;
                localUselessLocations[n.x][n.y] = true;
            }

    }

    /**
     * Executes the bot's shooting strategy to select and fire at the next target.
     * The targeting priority is as follows:
     * 1. Continue targeting a partially hit ship based on orientation
     * 2. Process targets from the priority queue based on probability scores
     * 3. Select highest probability cell from probability map
     * 4. Fall back to systematic grid search if no better options exist
     * After each shot, updates the game state by:
     * - Recording the shot result (hit/miss)
     * - Updating ship tracking data
     * - Adjusting targeting strategy based on hit results
     * - Managing the target queue and hit orientation
     * - Updating consecutive hits counter
     * 
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();
        if (targetQueue.isEmpty()) probabilityMap = buildProbabilityMap(hitList, boardState);
        if (lastHit != null && hitOrientation != orientation.UNKNOWN) {
            Point nextCell = getNextCellInDirection(lastHit);
            if (nextCell != null && !shotsFired[nextCell.x][nextCell.y] && !uselessLocations[nextCell.x][nextCell.y])
                shot = nextCell;
            else {
                Point oppositeCell = getOppositeDirection(lastHit);
                if (oppositeCell != null && !shotsFired[oppositeCell.x][oppositeCell.y] && !uselessLocations[oppositeCell.x][oppositeCell.y])
                    shot = oppositeCell;
            }
        }

        if (shot == null && !targetQueue.isEmpty()) {
            Point[] sortedQueue = new Point[Math.min(targetQueue.size(), 10)];
            int index = 0;
            for (Point p : targetQueue)
                if (!shotsFired[p.x][p.y] && !uselessLocations[p.x][p.y]) {
                    sortedQueue[index++] = p;
                    if (index >= 10) break;
                }

            Arrays.sort(sortedQueue, 0, index, (a, b) -> Integer.compare(probabilityMap[b.x][b.y], probabilityMap[a.x][a.y]));

            if (index > 0) {
                shot = sortedQueue[0];
                targetQueue.remove(shot);
            }
        }
        if (shot == null) shot = findHighestProbability(probabilityMap);

        if (shot == null) for (int i = 0; i < size && shot == null; i++)
            for (int j = 0; j < size && shot == null; j++)
                if (!shotsFired[i][j] && !uselessLocations[i][j]) shot = new Point(i, j);

        assert shot != null;
        shotsFired[shot.x][shot.y] = true;
        boolean hit = battleShip.shoot(shot);
        boardState[shot.x][shot.y] = hit ? cellState.HIT:cellState.MISS;

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
                    if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                        if (probabilityMap[newX][newY] > 0) targetQueue.add(new Point(newX, newY));
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
                    if (nextCell != null) targetQueue.add(nextCell);
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
     * Determines the next valid cell to target based on the current hit orientation.
     * For horizontal orientation, checks cells to the right and left.
     * For vertical orientation, checks cells above and below.
     * Only returns cells that are:
     * - Within board boundaries
     * - Not previously shot at
     * - Not marked as useless
     *
     * @param current The current hit position to search from
     * @return Point representing the next cell to target, or null if no valid cells found
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
     * Calculates the opposite direction from a first hit when continuing ship targeting.
     * Used when the current direction of targeting hits an obstruction or miss, to try
     * the opposite direction from the initial hit point.
     * For horizontal ships, checks the opposite side along the Y-axis.
     * For vertical ships, checks the opposite side along the X-axis.
     * Only returns cells that are:
     * - Within board boundaries
     * - Not previously shot at
     * - Not marked as useless
     *
     * @param firstHit The initial hit point to calculate opposite direction from
     * @return Point representing the cell in the opposite direction, or null if no valid cell exists
     */
    private Point getOppositeDirection(Point firstHit) {
        if (lastHit == null || firstHit == null) return null;
        if (hitOrientation == orientation.HORIZONTAL) {
            int direction = lastHit.y > firstHit.y ? -1:1;
            int newX = firstHit.x;
            int newY = firstHit.y + direction;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);

        } else if (hitOrientation == orientation.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1:1;
            int newX = firstHit.x + direction;
            int newY = firstHit.y;
            if (isValid(newX, newY) && !shotsFired[newX][newY] && !uselessLocations[newX][newY])
                return new Point(newX, newY);
        }
        return null;
    }

    /**
     * Updates the board state after a ship has been sunk by marking adjacent cells.
     * Performs two main operations:
     * 1. Marks all orthogonally adjacent cells (up, down, left, right) as useless
     * 2. For large ships (size >= 5), marks additional cells at distance 3 as useless
     *    based on the ship's orientation
     * The method:
     * - Creates a list of points from recent consecutive hits
     * - Updates the shipCells array to track confirmed ship locations
     * - Marks cells adjacent to the sunk ship as useless
     * - For large ships, applies additional marking logic to optimize targeting
     */
    private void markSunkShipCells() {
        ArrayList<Point> sunkShipPoints = new ArrayList<>(Arrays.asList(hitList).subList(hitListSize - consecutiveHits, hitListSize));
        for (int i = 0; i < size; i++) Arrays.fill(shipCells[i], false);
        for (Point p : sunkShipPoints) shipCells[p.x][p.y] = true;
        for (Point p : sunkShipPoints)
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx != 0 && dy != 0) continue;
                    int newX = p.x + dx;
                    int newY = p.y + dy;
                    if (isValid(newX, newY) && !shotsFired[newX][newY]) markUseless(newX, newY);
                }

        int sunkShipSize = consecutiveHits;
        if (sunkShipSize >= 5) {
            for (Point p : sunkShipPoints) {
                boolean isHorizontal = false;
                if (sunkShipPoints.size() > 1) {
                    Point first = sunkShipPoints.get(0);
                    Point second = sunkShipPoints.get(1);
                    isHorizontal = first.x == second.x;
                }
                if (isHorizontal) for (int dy = -3; dy <= 3; dy += 6) {
                    int newX = p.x;
                    int newY = p.y + dy;
                    if (isValid(newX, newY) && !shotsFired[newX][newY]) markUseless(newX, newY);
                }
                else for (int dx = -3; dx <= 3; dx += 6) {
                    int newX = p.x + dx;
                    int newY = p.y;
                    if (isValid(newX, newY) && !shotsFired[newX][newY]) markUseless(newX, newY);
                }

            }
        }
    }

    /**
     * Marks a specific cell as useless for targeting.
     * Updates both the board state and useless locations tracking arrays
     * to indicate that this cell should not be considered for future shots.
     *
     * @param x The x-coordinate of the cell to mark
     * @param y The y-coordinate of the cell to mark
     */
    private void markUseless(int x, int y) {
        boardState[x][y] = cellState.USELESS;
        uselessLocations[x][y] = true;
    }

    /**
     * Marks cells perpendicular to a hit ship as useless for targeting.
     * When a ship's orientation is known, cells perpendicular to the ship's axis
     * cannot contain parts of the same ship and can be marked as useless.
     * For horizontal ships: marks cells above and below
     * For vertical ships: marks cells to the left and right
     *
     * @param shot The point where the hit occurred
     * @param horizontal True if the ship is horizontal, false if vertical
     */
    private void markPerpendicularCellsUseless(Point shot, boolean horizontal) {
        if (horizontal) for (int i = -1; i <= 1; i++) {
            if (i == 0) continue;
            int newX = shot.x + i;
            int newY = shot.y;
            if (isValid(newX, newY)) markUseless(newX, newY);
        }
        else for (int i = -1; i <= 1; i++) {
            if (i == 0) continue;
            int newX = shot.x;
            int newY = shot.y + i;
            if (isValid(newX, newY)) markUseless(newX, newY);
        }

    }

    /**
     * Validates if a point's coordinates are within the game board boundaries.
     * Checks if both x and y coordinates are between 0 and the board size.
     *
     * @param point The Point object containing x,y coordinates to validate
     * @return boolean True if the point is within bounds, false otherwise
     */
    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }

    /**
     * Validates if given coordinates are within the game board boundaries.
     * Checks if both x and y coordinates are between 0 and the board size.
     *
     * @param x The x-coordinate to validate
     * @param y The y-coordinate to validate
     * @return boolean True if the coordinates are within bounds, false otherwise
     */
    private boolean isValid(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    /**
     * Returns the names of the bot's authors.
     * Required override from the base class.
     *
     * @return String The names of the bot's authors
     */
    @Override
    public String getAuthors() {
        return "Nathan Sikkema and Brendan Dileo";
    }
    
    /**
     * Adds a successful hit to the hit tracking list.
     * Maintains a record of all successful shots for ship tracking
     * and targeting strategy optimization.
     *
     * @param hit The Point coordinates of the successful hit to add
     */
    private void addToHitList(Point hit) {
        hitList[hitListSize++] = hit;
    }

    /**
     * Retrieves a hit point from the hit tracking list at a specific index.
     * Used to access historical hit data for ship tracking and targeting.
     *
     * @param index The index in the hit list to retrieve
     * @return Point The coordinates of the hit at the specified index
     */
    private Point getHitAt(int index) {
        return index < hitListSize ? hitList[index]:null;
    }

    private enum cellState {
        HIT, MISS, UNKNOWN, USELESS
    }
}
