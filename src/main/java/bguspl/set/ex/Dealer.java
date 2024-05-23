package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    //fields we added
    private final int[] cards;
    private long sleepTime = 10;
    protected Object dealerWake;
    protected BlockingQueue<Integer> playerSetCompleteQueue = new LinkedBlockingQueue<>();


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.cards = new int[3];
        this.dealerWake = new Object();
       
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (int i = 0; i < players.length; i++) {
            Thread player = new Thread(players[i], "player" + i);
            player.start();
        }

        while (!shouldFinish()) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        if(!terminate){
            terminate();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

        try{
            Thread.sleep(env.config.endGamePauseMillies);
        }
        catch(InterruptedException ignored){}

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable(); 
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for(int i = players.length - 1 ; i >= 0; i--){
            players[i].terminate();
            
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
    
            Integer idFirst;
            
            while(!playerSetCompleteQueue.isEmpty()){
                
                idFirst = playerSetCompleteQueue.remove();
                Player firstPlayer =  null ;
                    boolean found = false;

                for(int i = 0 ; i < players.length && !found; i++) {
                     if(players[i].getId() == idFirst){
                        firstPlayer = players[i];
                        found = true;
                    }
                }
                if(table.numOfTokens[idFirst] == 3){
                    
                    for(int i = 0 ; i < 3 ; i++){
                        int slot = table.playerTokens[idFirst][i];
                        cards[i] = table.slotToCard[slot];
                    }         
                    boolean isSet =  env.util.testSet(cards);
                    if(isSet){
                        int[] preNumOFtok  = new int[players.length];
                        for(int i = 0 ; i < players.length ; i++){
                            preNumOFtok[i] = table.numOfTokens[i];
                        }

                        table.removeCard(table.playerTokens[idFirst][0]);
                        table.removeCard(table.playerTokens[idFirst][1]);
                        table.removeCard(table.playerTokens[idFirst][2]);
                        
                        for(int i = 0 ; i < players.length ; i++){
                            if(table.numOfTokens[i] < preNumOFtok[i] ){
                                
                            }               
                        }
                        firstPlayer.isSet.add(true);
                        }
                    else{
                        for (int i = 0 ; i < 3 ; i++){
                            table.removeToken(idFirst , table.playerTokens[idFirst][i]);
                        }
                        firstPlayer.isSet.add(false);
                        
                        }

                    }    
                else{
                    firstPlayer.interrupt();
                }    
                }
            }    
               
             
                
            
    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        int cardsinDeck = deck.size();
        int cardsinTable = table.countCards();
        int tableSize = env.config.tableSize;

        if(cardsinTable == tableSize)
            return;
       
        else if(cardsinTable < tableSize){
            int numOfIter = Math.min(tableSize - cardsinTable , cardsinDeck);
            while(numOfIter > 0) {
                for(int i = 0 ; i < tableSize ; i++){
                    if(table.slotToCard[i] == null){
                        table.placeCard(deck.remove(0),i);
                        numOfIter--;
                    }
                }
            }
        }
        System.out.println("hints are:");
        table.hints();
        if(deck.size() == 0){
            List<Integer> tableCheck = new LinkedList<>();
            for(int i = 0 ; i < tableSize ; i++){
                if(table.slotToCard[i] != null){
                    tableCheck.add(table.slotToCard[i]);
                }
            }
            if(env.util.findSets(tableCheck, 1).size() == 0){
                terminate();

            }
        }
    }
    

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(dealerWake){
         
        try{   
            //System.out.println(Thread.currentThread().getName());
            dealerWake.wait(sleepTime);
            //System.out.println(123);
        }   
        catch (InterruptedException ignored) {}
    }}
        

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long currentTime = System.currentTimeMillis();
        long timeLeft = reshuffleTime - currentTime;
        if (reset && !shouldFinish()) {
            reshuffleTime = currentTime + env.config.turnTimeoutMillis;
            
        }
        boolean isRed = timeLeft <= env.config.turnTimeoutWarningMillis;
        env.ui.setCountdown(timeLeft, isRed);
    }
        

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]); 
                table.removeCard(i);
            }
        }
        
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
    List<Player> winnersList = new ArrayList<>();
    int maxScore = 0;
    for (Player p : players) {
        if (p.score() > maxScore)
            maxScore = p.score();
    }
    for (Player p : players) {
        if (p.score() == maxScore)
            winnersList.add(p);
    }
    int[] winners = new int[winnersList.size()];
    for (int i = 0; i < winnersList.size(); i++) {
        winners[i] = winnersList.get(i).id;
    }
    env.ui.announceWinner(winners);
    
    }

}
