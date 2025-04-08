import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.awt.*;
import java.util.ArrayList;
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
    private final boolean debug = true;
    // Directions to move once hit is made
    private final Point[] directions = {
            new Point(0, 1),
            new Point(0, -1),
            new Point(1, 0),
            new Point(-1, 0)
    };
    private int size;
    private BattleShip2 battleShip;
    private Random random;
    // Track points that have already been checked
    private HashSet<Point> shotsFired;
    private HashSet<Point> uselessLocations;
    private Queue<Point> targetQueue;  // Changed to Queue
    private ArrayList<Point> hitList;
    private cellState[][] boardState;
    private status hitOrientation;


    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        // Need Seed for same results to persist over runs. need to improve performance
        random = new Random(0xAAAAAAAA);
        shotsFired = new HashSet<>();
        uselessLocations = new HashSet<>();
        targetQueue = new LinkedList<>();  // Initialize as a LinkedList (Queue)
        hitList = new ArrayList<>();
        // Initialize the boardState 2D array to track the state of each cell
        boardState = new cellState[15][15];
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                boardState[i][j] = cellState.UNKNOWN;
            }
        }
        hitOrientation = status.UNKNOWN;

    }

    // Need to avoid firing on duplicates to optimize
    @Override
    public void fireShot() {

        if (debug) {
            System.out.println("Ships Sunk: " + battleShip.numberOfShipsSunk());
            System.out.println("Orientation: " + hitOrientation);
        }

        Point shot = null;

        // Check if there are targets in the queue
        if (!targetQueue.isEmpty()) {
            while (!targetQueue.isEmpty()) {
                Point potentialShot = targetQueue.poll();  // Use poll() to remove from the queue (FIFO)
                if (potentialShot != null && isValid(potentialShot) && !shotsFired.contains(potentialShot) && !uselessLocations.contains(potentialShot)) {
                    shot = potentialShot;
                    break;
                }
            }
        }

        // If not target in the queue just fire randomly in a checkered pattern
        if (shot == null) {
            do {
                int x = random.nextInt(size);
                int y = random.nextInt(size);
                if ((x + y) % 2 == 0) shot = new Point(x, y);

            } while (shot == null || shotsFired.contains(shot) || uselessLocations.contains(shot));
        }

        shotsFired.add(shot);
        // Returns true if a ship was hit
        boolean hit = battleShip.shoot(shot);

        if (hit) {
            hitList.add(shot);
            boardState[shot.x][shot.y] = cellState.HIT;
            for (Point direction : directions) {
                Point bestN = bestNeighbor(shot);
                if (bestN != null && isValid(bestN) && !shotsFired.contains(bestN)) {
                    targetQueue.add(bestN);  // Add to the Queue instead of Stack
                    if (hitOrientation == status.VERTICAL) {
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
                    } else if (hitOrientation == status.HORIZONTAL) {
                        Point top = new Point(shot.x - 1, shot.y);
                        Point bottom = new Point(shot.x + 1, shot.y);

                        if (isValid(top)) {
                            boardState[top.x][top.y] = cellState.USELESS;
                            uselessLocations.add(top);
                        }
                        if (isValid(bottom)) {
                            boardState[bottom.x][bottom.y] = cellState.USELESS;
                            uselessLocations.add(bottom);
                        }
                    }
                }
                Point neighbor = new Point(shot.x + direction.x, shot.y + direction.y);
                if (isValid(neighbor) && !shotsFired.contains(neighbor) && !uselessLocations.contains(neighbor)) {
                    targetQueue.add(neighbor);  // Add to the Queue instead of Stack
                }

            }
        } else {
            boardState[shot.x][shot.y] = cellState.MISS;
            hitOrientation = status.UNKNOWN;
        }
        if (debug) printBoardState();

    }

    public Point bestNeighbor(Point shot) {
        Point bestNeighbor = null;
        int maxPriority = -1;

        for (Point direction : directions) {
            Point neighbor = new Point(shot.x + direction.x, shot.y + direction.y);

            if (isValid(neighbor)) {
                int priority = 0;

                if (boardState[neighbor.x][neighbor.y] == cellState.UNKNOWN) {
                    priority = 1;
                }
                if (hitOrientation == status.HORIZONTAL && neighbor.x == shot.x) priority += 2;
                if (hitOrientation == status.VERTICAL && neighbor.y == shot.y) priority += 2;

                if (boardState[neighbor.x][neighbor.y] == cellState.HIT) {
                    if (isConsecutiveHit(shot, direction)) {
                        if (direction.equals(new Point(0, 1)) || direction.equals(new Point(0, -1))) {
                            hitOrientation = status.HORIZONTAL;
                        } else if (direction.equals(new Point(1, 0)) || direction.equals(new Point(-1, 0))) {
                            hitOrientation = status.VERTICAL;
                        }
                    }
                }

                // Update best neighbor based on priority
                if (priority > maxPriority) {
                    maxPriority = priority;
                    bestNeighbor = neighbor;
                }
            }
        }
        return bestNeighbor;
    }

    private boolean isConsecutiveHit(Point shot, Point direction) {
        Point neighbor1 = new Point(shot.x + direction.x, shot.y + direction.y);
        if (isValid(neighbor1) && boardState[neighbor1.x][neighbor1.y] == cellState.HIT) {
            Point neighbor2 = new Point(neighbor1.x + direction.x, neighbor1.y + direction.y);
            if (isValid(neighbor2) && boardState[neighbor2.x][neighbor2.y] == cellState.HIT) {
                return true;
            }
        }

        Point neighbor3 = new Point(shot.x - direction.x, shot.y - direction.y);
        if (isValid(neighbor3) && boardState[neighbor3.x][neighbor3.y] == cellState.HIT) {
            Point neighbor4 = new Point(neighbor3.x - direction.x, neighbor3.y - direction.y);
            if (isValid(neighbor4) && boardState[neighbor4.x][neighbor4.y] == cellState.HIT) {
                return true;
            }
        }

        return false;
    }

    public void printBoardState() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                switch (boardState[i][j]) {
                    case HIT:
                        System.out.print("* ");
                        break;
                    case MISS:
                        System.out.print("m ");
                        break;
                    case UNKNOWN:
                        System.out.print("• ");
                        break;
                    case USELESS:
                        System.out.print("X ");
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

    private enum cellState {
        HIT,
        MISS,
        UNKNOWN,
        USELESS
    }

    private enum status {
        VERTICAL,
        HORIZONTAL,
        UNKNOWN
    }
}
