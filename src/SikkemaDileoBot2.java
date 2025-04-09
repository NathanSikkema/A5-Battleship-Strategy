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

public class SikkemaDileoBot2 implements BattleShipBot {
    public static boolean debug = false;
    private final Point[] directions = {
            new Point(0, 1),  // right
            new Point(0, -1), // left
            new Point(1, 0),  // down
            new Point(-1, 0)  // up
    };
    private int size;
    private BattleShip2 battleShip;
    private HashSet<Point> shotsFired;
    private HashSet<Point> uselessLocations;
    private Queue<Point> targetQueue;
    private ArrayList<Point> hitList;
    private cellState[][] boardState;
    private targetOrientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;

    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        shotsFired = new HashSet<>();
        uselessLocations = new HashSet<>();
        targetQueue = new LinkedList<>();
        hitList = new ArrayList<>();
        boardState = new cellState[size][size];
        hitOrientation = targetOrientation.UNKNOWN;
        lastHit = null;
        consecutiveHits = 0;
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) boardState[i][j] = cellState.UNKNOWN;
    }

    private int[][] buildProbabilityMap(int[] shipSizes, ArrayList<Point> hitList, cellState[][] boardState) {
        int[][] probabilityMap = new int[BattleShip2.BOARD_SIZE][BattleShip2.BOARD_SIZE];

        // Only rebuild if we have new information
        if (hitList.size() > 0 && lastHit != null) {
            // Clear only affected areas
            for (int i = Math.max(0, lastHit.x - 1); i <= Math.min(BattleShip2.BOARD_SIZE - 1, lastHit.x + 1); i++) {
                for (int j = Math.max(0, lastHit.y - 1); j <= Math.min(BattleShip2.BOARD_SIZE - 1, lastHit.y + 1); j++) {
                    probabilityMap[i][j] = 0;
                }
            }
        }

        // Simplified probability calculation
        for (int shipSize : shipSizes) {
            // Horizontal placement checks
            for (int i = 0; i < BattleShip2.BOARD_SIZE; i++) {
                for (int j = 0; j <= BattleShip2.BOARD_SIZE - shipSize; j++) {
                    boolean canPlace = true;
                    for (int k = 0; k < shipSize; k++) {
                        Point p = new Point(i, j + k);
                        if (boardState[p.x][p.y] == cellState.MISS || boardState[p.x][p.y] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                    }
                    if (canPlace) {
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i][j + k]++;
                        }
                    }
                }
            }

            // Vertical placement checks
            for (int i = 0; i <= BattleShip2.BOARD_SIZE - shipSize; i++) {
                for (int j = 0; j < BattleShip2.BOARD_SIZE; j++) {
                    boolean canPlace = true;
                    for (int k = 0; k < shipSize; k++) {
                        Point p = new Point(i + k, j);
                        if (boardState[p.x][p.y] == cellState.MISS || boardState[p.x][p.y] == cellState.USELESS) {
                            canPlace = false;
                            break;
                        }
                    }
                    if (canPlace) {
                        for (int k = 0; k < shipSize; k++) {
                            probabilityMap[i + k][j]++;
                        }
                    }
                }
            }
        }

        // Add bonus for cells adjacent to hits
        for (Point hit : hitList) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx != 0 && dy != 0) continue;
                    int newX = hit.x + dx;
                    int newY = hit.y + dy;
                    if (isValid(new Point(newX, newY)) && boardState[newX][newY] == cellState.UNKNOWN) {
                        probabilityMap[newX][newY] += 2;
                    }
                }
            }
        }

        return probabilityMap;
    }

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
     Fires a shot at the best possible location
     Uses a combination of target queue and probability map to determine where to shoot
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();

        // Try to get a shot from the target queue first
        while (!targetQueue.isEmpty() && shot == null) {
            Point potentialShot = targetQueue.poll();
            if (potentialShot != null && !shotsFired.contains(potentialShot) && !uselessLocations.contains(potentialShot)) {
                shot = potentialShot;
            }
        }

        // If no shot from queue, try probability map
        if (shot == null) {
            int[][] probabilityMap = buildProbabilityMap(battleShip.getShipSizes(), hitList, boardState);
            Point p = findHighestProbability(probabilityMap);
            if (p != null && !shotsFired.contains(p) && !uselessLocations.contains(p)) {
                shot = p;
            }
        }

        // If still no shot, use checkerboard pattern
        if (shot == null) {
            // First try checkerboard pattern
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    if ((i + j) % 2 == 0) {
                        Point p = new Point(i, j);
                        if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                            shot = p;
                            break;
                        }
                    }
                }
            }

            // If still no shot, try any remaining position
            if (shot == null) {
                for (int i = 0; i < size && shot == null; i++) {
                    for (int j = 0; j < size && shot == null; j++) {
                        Point p = new Point(i, j);
                        if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                            shot = p;
                            break;
                        }
                    }
                }
            }
        }

        // If we still don't have a shot, clear useless locations and try again
        if (shot == null) {
            debugPrint("No valid shots found, clearing useless locations");
            uselessLocations.clear();
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (boardState[i][j] == cellState.USELESS) {
                        boardState[i][j] = cellState.UNKNOWN;
                    }
                }
            }

            // Try finding a shot again
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    Point p = new Point(i, j);
                    if (!shotsFired.contains(p)) {
                        shot = p;
                        break;
                    }
                }
            }
        }

        // If we still don't have a shot, something is wrong
        if (shot == null) {
            throw new IllegalStateException("No valid shots available - all cells have been shot");
        }

        // Ensure the shot is valid
        if (!isValid(shot)) {
            throw new IllegalStateException("Invalid shot coordinates: (" + shot.x + "," + shot.y + ")");
        }

        shotsFired.add(shot);
        boolean hit = battleShip.shoot(shot);
        boardState[shot.x][shot.y] = hit ? cellState.HIT : cellState.MISS;
        debugPrint("Shot at (" + shot.x + "," + shot.y + "): " + (hit ? "HIT" : "MISS"));

        if (hit) {
            hitList.add(shot);
            consecutiveHits++;
            debugPrint("Consecutive hits: " + consecutiveHits);

            if (battleShip.numberOfShipsSunk() > previousSunkShips) {
                debugPrint("Ship sunk! Resetting target queue and state");
                targetQueue.clear();
                lastHit = null;
                hitOrientation = targetOrientation.UNKNOWN;
                consecutiveHits = 0;
                markSunkShipCells();
            } else if (lastHit == null) {
                debugPrint("First hit, adding adjacent points to queue");
                lastHit = shot;
                for (Point dir : directions) {
                    Point neighbor = new Point(shot.x + dir.x, shot.y + dir.y);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor) && !uselessLocations.contains(neighbor))
                        targetQueue.add(neighbor);
                }
            } else {
                if (shot.x == lastHit.x) {
                    hitOrientation = targetOrientation.HORIZONTAL;
                    debugPrint("Ship orientation determined: HORIZONTAL");
                    markPerpendicularCellsUseless(shot, true);
                } else if (shot.y == lastHit.y) {
                    hitOrientation = targetOrientation.VERTICAL;
                    debugPrint("Ship orientation determined: VERTICAL");
                    markPerpendicularCellsUseless(shot, false);
                }
                if (hitOrientation != targetOrientation.UNKNOWN) {
                    Point nextCell = getNextCellInDirection(shot);
                    if (nextCell != null) {
                        targetQueue.add(nextCell);
                        debugPrint("Added next cell in direction to queue: (" + nextCell.x + "," + nextCell.y + ")");
                    }
                }
                lastHit = shot;
            }
        } else {
            if (consecutiveHits > 0 && hitOrientation != targetOrientation.UNKNOWN) {
                debugPrint("Miss after hits, trying opposite direction");
                targetQueue.clear();
                Point firstHit = hitList.get(hitList.size() - consecutiveHits);
                Point oppositeDir = getOppositeDirection(firstHit);
                if (oppositeDir != null) targetQueue.add(oppositeDir);
            }
            consecutiveHits = 0;
            if (targetQueue.isEmpty()) {
                lastHit = null;
                hitOrientation = targetOrientation.UNKNOWN;
            }
        }
        if (debug) printBoardState();
    }

    private Point getNextCellInDirection(Point current) {
        if (hitOrientation == targetOrientation.HORIZONTAL) {
            Point right = new Point(current.x, current.y + 1);
            Point left = new Point(current.x, current.y - 1);
            if (isValid(right) && !shotsFired.contains(right) && !uselessLocations.contains(right)) return right;
            if (isValid(left) && !shotsFired.contains(left) && !uselessLocations.contains(left)) return left;
        } else if (hitOrientation == targetOrientation.VERTICAL) {
            Point down = new Point(current.x + 1, current.y);
            Point up = new Point(current.x - 1, current.y);
            if (isValid(down) && !shotsFired.contains(down) && !uselessLocations.contains(down)) return down;
            if (isValid(up) && !shotsFired.contains(up) && !uselessLocations.contains(up)) return up;
        }
        return null;
    }

    private void markSunkShipCells() {
        ArrayList<Point> sunkShipPoints = new ArrayList<>();
        for (int i = hitList.size() - consecutiveHits; i < hitList.size(); i++) {
            sunkShipPoints.add(hitList.get(i));
        }

        // Only mark immediate neighbors as useless when a ship is sunk
        for (Point p : sunkShipPoints) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Point neighbor = new Point(p.x + dx, p.y + dy);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor) && !hitList.contains(neighbor)) {
                        markUseless(neighbor);
                    }
                }
            }
        }
    }

    private void markUseless(Point p) {
        boardState[p.x][p.y] = cellState.USELESS;
        uselessLocations.add(p);
    }

    private Point getOppositeDirection(Point firstHit) {
        if (lastHit == null || firstHit == null) return null;

        if (hitOrientation == targetOrientation.HORIZONTAL) {
            int direction = lastHit.y > firstHit.y ? -1:1;
            Point opposite = new Point(firstHit.x, firstHit.y + direction);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        } else if (hitOrientation == targetOrientation.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1:1;
            Point opposite = new Point(firstHit.x + direction, firstHit.y);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        }
        return null;
    }

    private void markPerpendicularCellsUseless(Point shot, boolean horizontal) {
        // Only mark perpendicular cells when we have multiple consecutive hits
        if (consecutiveHits < 2) {
            return;
        }

        if (horizontal) {
            // Only mark immediate perpendicular cells
            Point up = new Point(shot.x - 1, shot.y);
            Point down = new Point(shot.x + 1, shot.y);
            if (isValid(up) && !hitList.contains(up) && !shotsFired.contains(up)) markUseless(up);
            if (isValid(down) && !hitList.contains(down) && !shotsFired.contains(down)) markUseless(down);
        } else {
            // Only mark immediate perpendicular cells
            Point left = new Point(shot.x, shot.y - 1);
            Point right = new Point(shot.x, shot.y + 1);
            if (isValid(left) && !hitList.contains(left) && !shotsFired.contains(left)) markUseless(left);
            if (isValid(right) && !hitList.contains(right) && !shotsFired.contains(right)) markUseless(right);
        }

        // Only mark diagonal cells when we're certain about the ship's position
        if (consecutiveHits > 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    Point diagonal = new Point(shot.x + dx, shot.y + dy);
                    if (isValid(diagonal) && !hitList.contains(diagonal) && !shotsFired.contains(diagonal)) {
                        markUseless(diagonal);
                    }
                }
            }
        }
    }

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

    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }

    @Override
    public String getAuthors() {
        return "Nathan Sikkema and Brendan Dileo";
    }

    private void debugPrint(String message) {
        if (debug) System.out.println("[DEBUG] " + message);
    }

    private enum cellState {
        HIT, MISS, UNKNOWN, USELESS
    }

    private enum targetOrientation {
        VERTICAL, HORIZONTAL, UNKNOWN
    }
}

