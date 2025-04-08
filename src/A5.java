import battleship.BattleShip2;

/**
 * Starting code for COMP10205 - Assignment#6 - Version 2 of BattleShip
 * @author mark.yendt@mohawkcollege.ca (Dec 2021)
 */

public class A5 {
    public static void main(String[] args) {

        // DO NOT add any logic to this code
        // All logic must be added to your Bot implementation
        // see fireShot in the ExampleBot class

        final int NUMBEROFGAMES = 10000;
        System.out.println(BattleShip2.getVersion());
        BattleShip2 battleShip1 = new BattleShip2(NUMBEROFGAMES, new SikkemaDileoBot());
        int [] gameResults1 = battleShip1.run();


        // You may add some analysis code to look at all the game scores that are returned in gameResults
        // This can be useful for debugging purposes.
        battleShip1.reportResults();
    }
}
