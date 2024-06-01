package bguspl.set.ex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

import bguspl.set.Env;
import java.util.logging.Level;
/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
	
	// create blocking q for 3 slots
	private BlockingQu keyQueue= new BlockingQu(3);
	
	
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
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    
    private Dealer dealer;
    
    boolean penalty = false;
    boolean reward = false;
    public boolean play = false;
    private boolean shouldRemoveToken = false;
    private int tokenToBeRemoved = -1;
    private boolean shouldHandleKeyPress = false;
    private int key = -1;
    private long supposeToFinishPenaltyTime = -1;
    private boolean waitingForResponseFromDealer = false;
    private Vector<Integer> futureDemolition = new Vector<>();
    private boolean clearEraseCardsTokensFromQueueFlag = false;
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
        this.dealer=dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) 
        	createArtificialIntelligence();

        while (!terminate) {
	        synchronized (this) {
				try {
					wait();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			
				// TODO implement main player loop
	        	if(penalty) {// if the dealer punish the player for 3 tokens not a set
	            	waitingForResponseFromDealer = false;
	            	
	            	// create a thread that changed the time of the player in the ui 
	            	new Thread(() -> {
	            		long count = env.config.penaltyFreezeMillis;
	            		while(count>=0) {
		            		try {
		        				env.ui.setFreeze(id, count);
		        				Thread.sleep(1000);
		        				count = count - 1000;
		        			} catch (InterruptedException e) {
		        				// TODO Auto-generated catch block
		        				e.printStackTrace();
		        			}
	            		}
	            		
	            	}).start();
	            	
	    			try {
	            		// when do i need to wake up?
	            		supposeToFinishPenaltyTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
						
	            		// check if wake up time has passed
	            		while(System.currentTimeMillis() < supposeToFinishPenaltyTime)
						{
	            			// if the wake up time is still in the future go to sleep again
	            			shouldHandleKeyPress = false;
	            			wait(supposeToFinishPenaltyTime - System.currentTimeMillis());
						}
	            		
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	
	        		// clean the key queue 
	        		
	        		
	        		if(!human) {
	        			
	        			clearQueue();
	        			
	        			for (int i=0; i<12; i++) {
	        				table.removeToken(id, i);
	        			}
	        		}
	        		
	        		penalty = false;
	        	}
	        	if(reward) {
	            	waitingForResponseFromDealer = false;

	            	// thread that create the counter decrease
	            	new Thread(() -> {
	            		long count = env.config.pointFreezeMillis;
	            		while(count>=0) {
		            		try {
		        				env.ui.setFreeze(id, count);
		        				Thread.sleep(1000);
		        				count = count - 1000;
		        			} catch (InterruptedException e) {
		        				// TODO Auto-generated catch block
		        				e.printStackTrace();
		        			}
	            		}
	            	}).start();
	            	
	        		try {
	        			
	        			long supposeToFinishPointTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
						
	            		// check if wake up time has passed
	            		while(System.currentTimeMillis() < supposeToFinishPointTime)
						{
	            			// if the wake up time is still in the future go to sleep again
	            			shouldHandleKeyPress = false;
	            			wait(supposeToFinishPointTime - System.currentTimeMillis());
						}
	            		
	        		} catch (InterruptedException e) {
	        			// TODO Auto-generated catch block
	        			e.printStackTrace();
	        		}
	        		
	        		clearQueue();
	
	        		reward = false;
	        	}
	        	if(shouldRemoveToken) {
	        		keyQueue.remove(tokenToBeRemoved);
	        		shouldRemoveToken = false;
	        	}
	        	if(shouldHandleKeyPress) {
	        		keyPressHandleByPlayer(key);
	        		shouldHandleKeyPress = false;
	        	}
	        	if(clearEraseCardsTokensFromQueueFlag) {
	        		clearEraseCardsTokensFromQueueFlag = false;
	        		clearEraseCardsTokensFromQueue();
	        	}
	        }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    
     }
    
    // this method will be called if some of the cards that hols my tokens were erased
    private void clearEraseCardsTokensFromQueue() {
    	for(int i = 0 ;i < keyQueue.size() ;i++) {
    		table.removeToken(id, keyQueue.get(i));
    	}
    	keyQueue.clear();

	}

	/**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
    	
    	// if the player has 3 cards that are waiting for evaluation from the dealer dont do anything
    	if(waitingForResponseFromDealer ) {

    		return;
    	}
    	
    	shouldHandleKeyPress = true;
    	key = slot;
    	synchronized (this) {
    		notify();
		}
    }
    
    private void keyPressHandleByPlayer(int slot) {
    	
    	// check if the token already exist on the table in this slot
    	if(keyQueue.checkIfTokenExist(slot)) {
    		// remove the slot from the local blocked queue
    		keyQueue.remove(slot);
    		
    		// remove the slot from the table
    		table.removeToken(id, slot);
    		
    		return;
    	}
    	
    	// check if the slot is in the future demolition
    	synchronized (this) {
    		for(Integer integer : futureDemolition)
        		if(integer.intValue() == slot) {
        			return;
        		}
		}
    	
    	
    	// the new slot is checked and wan not found in the blocked queue
    	
    	int size = keyQueue.size();
    	if (size==3) {
    		return;
    	}
    	
    	    	// now we know that the blocked queue is availabe to

    	
    	// check with the table if the slot is available
    	if(table.getCardBySlot(slot)==-1) {
    		return;
    	}

    	//placeToken
    	table.placeToken(id, slot);

    	//adding to the queue of tokens
    	keyQueue.put(slot);

    	if (keyQueue.size()==3) {
        	waitingForResponseFromDealer = true;
    		dealer.notifyEvalutationFromPlayer(id);
    	}
    }
    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {
        // TODO implement
    	penalty = true;
    	notifyAll();
    }
    
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public synchronized void point() {
        // TODO implement
    	reward = true;
    	notify();
    	

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
    	
    	
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
        	
        	env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        	boolean flag = true;
        	boolean shouldClearQueue = true;
            while (!terminate) {
            	if(play) {
	                // TODO implement player key press simulator
            		
            		try {
            			// time that takes to ai to generate new key press
	                	long sleepTime = ThreadLocalRandom.current().nextLong(20, 40);
	                    synchronized (this) { 
	                    	wait(sleepTime); 
	                    }
	                } catch (InterruptedException ignored) {}
            		
	            	// generate a key
            		
            		int randSlot = ThreadLocalRandom.current().nextInt(12);
            		
            		// generate a key that is not the same as the the keys in the keyQueue
	            	while( keyQueue.checkIfTokenExist(randSlot) ) {
	            		randSlot = ThreadLocalRandom.current().nextInt(12);
	            	}
	            	
	                // tell the player about the new slot 
	                keyPressed(randSlot);
	                
            	}
            	else if(shouldClearQueue)
            	{
            		shouldClearQueue = false;
            		clearQueue();
            		
            	}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        });
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    	terminate = true;
    }


    
    
    
    public void clearQueue() {
		keyQueue.clear();
		
    }

    public int score() {
        return score;
    }
    
    public synchronized void removeSlotFromKeyQueue(int slot) {
    	shouldRemoveToken = true;
        tokenToBeRemoved = slot;
        futureDemolition.add(slot);
        notify();
    	
    }
    
    // this is called if the player asked the dealer to check 3 cards, 
    //but until the cards were checked by the dealer they already been replace 
    //because another player previously asked to check all or some of them first
    public synchronized void setWaitingForResponseFromDealer() {
    	waitingForResponseFromDealer = false;
    	
    	clearEraseCardsTokensFromQueueFlag = true;
    	
    	
    	notify();
    }
    
    public synchronized void clearFutureDemolition() {
    	futureDemolition.clear();
    }
	///////////////////for tests

	public BlockingQu getQu(){
		return keyQueue;
	}
}
