
import battleship.BattleShip2;
import battleship.BattleShipBot;

import java.util.HashSet;
import java.util.Random;
import java.awt.*;

/*
 * Use a data structure to track where you have placed your shots so that you don't
 * fire on a cell more than once. This needs to be accessed quickly in order to
 * determine if a cell has been fired upon yet. After you have hit a ship, you may
 * want to change how you select your next shot.
 * ---> HashSet? LinkedList?
 */

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


    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;
        // Need Seed for same results to persist over runs. need to improve performance
        random = new Random(0xAAAAAAAA);
        shotsFired = new HashSet<>();
    }

    // Need to avoid firing on duplicates to optimize
    // Check surrounding points to check for another hit instead of randomly firing?
    @Override
    public void fireShot() {

        Point shot;

        // Attempts to make a shot at random point not already fired on
        do {
            // Random coords on grid for shot
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            shot = new Point(x, y);
        } while (shotsFired.contains(shot));
        shotsFired.add(shot);

        // Returns true is a ship was hit
        boolean hit = battleShip.shoot(shot);
    }

    @Override
    public String getAuthors() {
        return "Nathan Sikkema\nBrendan Dileo";
    }



}
