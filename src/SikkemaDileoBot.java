
import battleship.BattleShip2;
import battleship.BattleShipBot;
import java.util.*;
import java.awt.*;


/**
 * Adapted from code by Mark Yendt (ExampleBot.java), Mohawk College, December 2021
 * 'A Sample random shooter - Takes no precaution on double shooting and has no strategy once
 * a ship is hit - This is not a good solution to the problem!'
 *
 * @author Nathan Sikkema
 * @author Brendan Dileo
 */

public class SikkemaDileoBot implements BattleShipBot {
    private final boolean debug = false;
    private int size;
    private BattleShip2 battleShip;
    private Random random;

    // Track points that have already been checked
    private HashSet<Point> shotsFired;
    private Stack<Point> targetStack;
    private ArrayList<Point> hitList;
    private cellState[][] boardState;


    // Directions to move once hit is made
    private final Point[] directions = {
            new Point(0, 1),
            new Point(0, -1),
            new Point(1, 0),
            new Point(-1, 0)
    };


    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        // Need Seed for same results to persist over runs. need to improve performance
        random = new Random(0xAAAAAAAA);
        shotsFired = new HashSet<>();
        targetStack = new Stack<>();
        hitList = new ArrayList<>();
        // Initialize the boardState 2D array to track the state of each cell
        boardState = new cellState[15][15];
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                boardState[i][j] = cellState.UNKNOWN;
            }
        }

    }

    // Need to avoid firing on duplicates to optimize
    // Check surrounding points to check for another hit instead of randomly firing?
    @Override
    public void fireShot() {

        Point shot = null;

        // Check if there are targets in the queue
        if (!targetStack.isEmpty()) {
            while (!targetStack.isEmpty()) {
                Point potentialShot = targetStack.pop();
                if (potentialShot != null && isValid(potentialShot) && !shotsFired.contains(potentialShot)) {
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

            } while (shot == null || shotsFired.contains(shot));
        }

        shotsFired.add(shot);
        // Returns true is a ship was hit
        boolean hit = battleShip.shoot(shot);

       if (hit) {
           hitList.add(shot);
           boardState[shot.x][shot.y] = cellState.HIT;
           for (Point direction : directions) {
               Point neighbor = new Point(shot.x + direction.x, shot.y + direction.y);
               if (isValid(neighbor) && !shotsFired.contains(neighbor)) {
                   targetStack.add(neighbor);
               }
           }
       }
       else boardState[shot.x][shot.y] = cellState.MISS;
       if (debug) printBoardState();

    }
    public void printBoardState() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                switch (boardState[i][j]) {
                    case HIT:
                        System.out.print("H ");
                        break;
                    case MISS:
                        System.out.print("m ");
                        break;
                    case UNKNOWN:
                        System.out.print(". ");
                        break;
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    // Checks if a point is valid
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
        UNKNOWN
    }



}
