package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Mapping between a player and the slots that token putted there (-1 if none).
     */
    protected final Integer[][] playerTokens; // tokens per slot (if any)
    
    /**
     * Mapping between a player and the num of tokens he already put (0 if none)
     */
    protected final Integer[] numOfTokens; 

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;


        this.playerTokens = new Integer[env.config.players][3]; 
        for (int i = 0; i < playerTokens.length; i++) {
            for(int t = 0 ; t < 3 ; t++){
                playerTokens[i][t] = -1;
            }}

        this.numOfTokens = new Integer[env.config.players];
        for(int i = 0 ; i < env.config.players ; i++){
            numOfTokens[i] = 0;
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
       
        Integer card = slotToCard[slot];

        for (int i = 0; i < playerTokens.length;i++)
        {
            for (int j = 0; j < numOfTokens[i]; j++) 
            {
                if (playerTokens[i][j] == slot)
                    try {
                        Thread.sleep(1);
                    } 
                    catch (InterruptedException ignored) {}
                    removeToken(i,slot);
            }
        }
        
        if (card != null) {
        cardToSlot[card] = null;
        slotToCard[slot] = null;
        }
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(slotToCard[slot] != null ){
            if(numOfTokens[player] < 3){
            boolean placed = false;
            int i = 0;
            while (!placed){
                if(playerTokens[player][i] == -1){
                    playerTokens[player][i] = slot;
                    placed = true ;
                }
                else{
                    i++;
                }
                }

            numOfTokens[player]++;
            env.ui.placeToken( player, slot) ;  
        }}}
        // need to check if need to print something if there no card there.
    

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
         if(slotToCard[slot] != null ){
            boolean isThere = false ;
            int tokenIndex = -1;
            for(int i = 0 ; !isThere && i < 3 ; i++){
                if (playerTokens[player][i] == slot){
                    isThere = true;
                    tokenIndex = i;
                }
            }
            if(isThere){
                playerTokens[player][tokenIndex] = -1;
                numOfTokens[player]--;
                env.ui.removeToken(player, slot);
                return true;
            }
            else{
                 return false;
            }}
 
        else{
            return false;
         }
    }
}