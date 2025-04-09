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

    /**
     Fires a shot at the best possible location
     Uses a combination of target queue and probability map to determine where to shoot
     */
    @Override
    public void fireShot() {
        Point shot = null;
        int previousSunkShips = battleShip.numberOfShipsSunk();
        if (!targetQueue.isEmpty())
            do shot = targetQueue.poll();
            while (shot != null && (shotsFired.contains(shot) || uselessLocations.contains(shot)));

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

            int currentSunkShips = battleShip.numberOfShipsSunk();
            if (currentSunkShips > previousSunkShips) {
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
                if (hitOrientation == targetOrientation.UNKNOWN) {
                    if (shot.x == lastHit.x) {
                        hitOrientation = targetOrientation.HORIZONTAL;
                        debugPrint("Ship orientation determined: HORIZONTAL");
                        markPerpendicularCellsUseless(shot, true);
                    } else if (shot.y == lastHit.y) {
                        hitOrientation = targetOrientation.VERTICAL;
                        debugPrint("Ship orientation determined: VERTICAL");
                        markPerpendicularCellsUseless(shot, false);
                    }
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
            if (isValid(right) && !shotsFired.contains(right) && !uselessLocations.contains(right)) {
                return right;
            }
            if (isValid(left) && !shotsFired.contains(left) && !uselessLocations.contains(left)) {
                return left;
            }
        } else if (hitOrientation == targetOrientation.VERTICAL) {
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

    private enum targetOrientation {
        VERTICAL, HORIZONTAL, UNKNOWN
    }
}