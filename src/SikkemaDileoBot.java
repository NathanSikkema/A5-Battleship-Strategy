
import battleship.BattleShip2;
import battleship.BattleShipBot;
import java.util.Queue;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Random;
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
    private int size;
    private BattleShip2 battleShip;
    private Random random;

    // Track points that have already been checked
    private HashSet<Point> shotsFired;
    private Queue<Point> targetQueue;


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
        targetQueue = new LinkedList<>();
    }

    // Need to avoid firing on duplicates to optimize
    // Check surrounding points to check for another hit instead of randomly firing?
    @Override
    public void fireShot() {

        Point shot = null;

        // Check if there are targets in the queue
        if (!targetQueue.isEmpty()) {
            while (!targetQueue.isEmpty()) {
                Point potentialShot = targetQueue.poll();
                if (potentialShot != null && isValid(potentialShot) && !shotsFired.contains(potentialShot)) {
                    shot = potentialShot;
                    break;
                }
            }
        }

        // If not target in the queue just fire randomly
        if (shot == null) {
            do {
                // Random coords on grid for shot
                int x = random.nextInt(size);
                int y = random.nextInt(size);
                shot = new Point(x, y);
            } while (shotsFired.contains(shot));
        }

        shotsFired.add(shot);
        // Returns true is a ship was hit
        boolean hit = battleShip.shoot(shot);

       if (hit) {
           for (Point direction : directions) {
               Point neighbor = new Point(shot.x + direction.x, shot.y + direction.y);
               if (isValid(neighbor) && !shotsFired.contains(neighbor)) {
                   targetQueue.add(neighbor);
               }
           }
       }
    }

    // Checks if a point is valid
    private boolean isValid(Point point) {
        return point.x >= 0 && point.x < size && point.y >= 0 && point.y < size;
    }

    @Override
    public String getAuthors() {
        return "Nathan Sikkema\nBrendan Dileo";
    }



}
