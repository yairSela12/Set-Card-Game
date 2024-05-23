package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Queue;
import bguspl.set.Env;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //fields we added
    private BlockingQueue<Integer> actionQueue;
    private Dealer dealer;
    public Object playerKey;
    protected BlockingQueue<Boolean> isSet;
    private volatile boolean penalized;


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actionQueue = new LinkedBlockingQueue<>();
        this.dealer = dealer;
        this.playerKey = new Object();
        this.isSet = new LinkedBlockingQueue<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        
        while (!terminate) {
            while (!actionQueue.isEmpty()) {
                int slot = actionQueue.poll();
                boolean found = false;
                for(int i = 0 ; i < 3 && !found ; i++){
                    if (this.table.playerTokens[this.id][i] == slot)
                    found = true; 
                }
                if(found)
                    table.removeToken(id, slot);
                else{
                    table.placeToken(id, slot);
                    if(table.numOfTokens[id] == 3){
                        synchronized(dealer){
                        dealer.playerSetCompleteQueue.offer(id);
                        synchronized (dealer.dealerWake) {
                        dealer.dealerWake.notify();
                        } }
                        boolean answerSet = false;
                        try{
                            answerSet = isSet.take();
                        
                            if(answerSet)
                                point();
                            else
                                penalty();
                              
                        }  
                        catch(InterruptedException e) {}
                                              
                    }
                } 
          }        
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");    
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                int randomSlot = (int) (Math.random() * env.config.tableSize);
                keyPressed(randomSlot);
                try {
                    synchronized (this) { wait(20); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        Thread.currentThread().interrupt();
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {  
        if (actionQueue.size() < 3 && table.slotToCard[slot] != null && !penalized)
            actionQueue.offer(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        long freezeTime = env.config.pointFreezeMillis;
        penalized = true;
        env.ui.setFreeze(this.id, freezeTime);
        try {
            Thread.sleep(freezeTime);
        } 
        catch (InterruptedException e) {}
        env.ui.setFreeze(this.id, 0);
        penalized = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long remainingTime = env.config.penaltyFreezeMillis;
        long decrementInterval = 1000;
        env.ui.setFreeze(this.id, remainingTime);
        penalized = true;
        while (remainingTime > 0) {
            try {
                Thread.sleep(decrementInterval);
            } catch (InterruptedException ignored) {}
            remainingTime = remainingTime - decrementInterval;
            env.ui.setFreeze(this.id, remainingTime);
        }
        penalized = false;
    }

    public int score() {
        return score;
    }

    public int getId() {
        return id;}

    public void interrupt(){
        playerThread.interrupt();
    }    
}
