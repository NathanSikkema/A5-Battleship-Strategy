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
    private orientation hitOrientation;
    private Point lastHit;
    private int consecutiveHits;
    private ShipStatus[] shipStatuses;

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
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                boardState[i][j] = cellState.UNKNOWN;
        shipStatuses = new ShipStatus[battleShip.getShipSizes().length];
        for (int i = 0; i < battleShip.getShipSizes().length; i++)
            shipStatuses[i] = new ShipStatus(battleShip.getShipSizes()[i]);
    }

    private int[][] buildProbabilityMap(int[] shipSizes, ArrayList<Point> hitList, cellState[][] boardState) {
        int[][] probabilityMap = new int[BattleShip2.BOARD_SIZE][BattleShip2.BOARD_SIZE];

        // Only rebuild if we have new information
        if (!hitList.isEmpty() && lastHit != null) {
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
     Fires a shot at the best possible location
     Uses a combination of target queue and probability map to determine where to shoot
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();

        int[][] probabilityMap = buildProbabilityMap(battleShip.getShipSizes(), hitList, boardState);

        if (!targetQueue.isEmpty())
            do shot = targetQueue.poll();
            while (shot != null && (shotsFired.contains(shot) || uselessLocations.contains(shot)));

        if (shot == null) {
            Point p = findHighestProbability(probabilityMap);
            if (!shotsFired.contains(p) && !uselessLocations.contains(p))
                shot = p;
        }

        if (shot == null) {
            debugPrint("Falling back to any valid position");
            for (int i = 0; i < size && shot == null; i++) {
                for (int j = 0; j < size && shot == null; j++) {
                    if (lastHit == null && (i + j) % 2 != 0) continue;
                    Point p = new Point(i, j);
                    if (!shotsFired.contains(p) && !uselessLocations.contains(p)) {
                        shot = p;
                    }
                }
            }
        }

        shotsFired.add(shot);
        boolean hit = battleShip.shoot(shot);
        assert shot != null;
        boardState[shot.x][shot.y] = hit ? cellState.HIT:cellState.MISS;
        debugPrint("Shot at (" + shot.x + "," + shot.y + "): " + (hit ? "HIT":"MISS"));

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
            }
            else if (lastHit == null) {
                debugPrint("First hit, adding adjacent points to queue");
                lastHit = shot;
                for (Point dir : directions) {
                    Point neighbor = new Point(shot.x + dir.x, shot.y + dir.y);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor) && !uselessLocations.contains(neighbor))
                        targetQueue.add(neighbor);
                }
            } else {
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

    private void markSunkShipCells() {
        ArrayList<Point> sunkShipPoints = new ArrayList<>();
        for (int i = hitList.size() - consecutiveHits; i < hitList.size(); i++) sunkShipPoints.add(hitList.get(i));

        for (Point p : sunkShipPoints) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Point neighbor = new Point(p.x + dx, p.y + dy);
                    if (isValid(neighbor) && !shotsFired.contains(neighbor)) markUseless(neighbor);
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

        if (hitOrientation == orientation.HORIZONTAL) {
            int direction = lastHit.y > firstHit.y ? -1:1;
            Point opposite = new Point(firstHit.x, firstHit.y + direction);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        } else if (hitOrientation == orientation.VERTICAL) {
            int direction = lastHit.x > firstHit.x ? -1:1;
            Point opposite = new Point(firstHit.x + direction, firstHit.y);
            if (isValid(opposite) && !shotsFired.contains(opposite) && !uselessLocations.contains(opposite)) {
                return opposite;
            }
        }
        return null;
    }

    private void markPerpendicularCellsUseless(Point shot, boolean horizontal) {
        if (horizontal) {
            Point up = new Point(shot.x - 1, shot.y);
            Point down = new Point(shot.x + 1, shot.y);
            if (isValid(up)) markUseless(up);
            if (isValid(down)) markUseless(down);
        } else {
            Point left = new Point(shot.x, shot.y - 1);
            Point right = new Point(shot.x, shot.y + 1);
            if (isValid(left)) markUseless(left);
            if (isValid(right)) markUseless(right);
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

    
}