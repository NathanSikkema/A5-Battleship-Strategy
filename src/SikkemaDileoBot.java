
import battleship.BattleShip2;
import battleship.BattleShipBot;
import java.util.Random;
import java.awt.*;



/**
 * Use a data structure to track where you have placed your shots so that you don't
 * fire on a cell more than once. This needs to be accessed quickly in order to
 * determine if a cell has been fired upon yet. After you have hit a ship, you may
 * want to change how you select your next shot.
 * ---> HashSet? LinkedList?
 */



public class SikkemaDileoBot implements BattleShipBot {
    private int size;
    private BattleShip2 battleShip;
    private Random random;


    @Override
    public void initialize(BattleShip2 battleShip2) {
        battleShip = battleShip2;
        size = BattleShip2.BOARD_SIZE;


        // Need to use a Seed if you want the same results to occur from run to run
        // This is needed if you are trying to improve the performance of your code

        // Needed for random shooter - not required for more systematic approaches
        random = new Random(0xAAAAAAAA);
    }

    @Override
    public void fireShot() {

        int x = random.nextInt(size);
        int y = random.nextInt(size);

        // Will return true if we hot a ship
        boolean hit = battleShip.shoot(new Point(x,y));
    }

    @Override
    public String getAuthors() {
        return "Nathan Sikkema\nBrendan Dileo";
    }



}
