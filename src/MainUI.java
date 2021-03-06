import AV_Components.AudioPlayer;
import Game_Constants.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;

/** Clue Game - Main User Interface, and Main logic for game.
 *  Game structure is manipulated within MainUI  */
public class MainUI extends JFrame implements MouseListener, ActionListener, MouseMotionListener{

    //Frame Components.
    private Hub hub;
    private Console bottomPanel;
    private Board board;

    //MainUI Variables.
    private int turn;               //Current turn number.
    private boolean canMove;        //Conditional affecting whether new room is selectable.
    private boolean inDisprove;     //Conditional affecting whether disproval sequence is initiated.
    private boolean inAccuse;       //Conditional affecting whether accusation sequence is initiated.
    private boolean turnToggle;     //Conditional affecting beginning of a new turn.
    private boolean humanTurn;      //Conditional determining whether action is human or AI.

    private Player[] players;       //All players in the game.
    private AI[] aiPlayers;         //Of the players, those that are AI.
    private Card[] envelope;        //Array containing the mystery answer.
    private Card[] accusation;      //Array containing the accusers guess at answer.

    private javax.swing.Timer diceRoll, diceRollStop;   //Timers.
    private AudioPlayer dice, button;

    private Deck deck;
    private int easterEggCount;
    private final Area easterEggTrigger = new Area(new Rectangle(300,285,10,10));

    private Random rand = new Random();

    /** Constructor.  */
    public MainUI(ArrayList<GamePiece> selection){

        //Set initial variable states.
        canMove = true;
        inDisprove = false;
        inAccuse = false;
        humanTurn = true;
        turnToggle = false;
        turn = 0;

        //Instantiate classes and structures.
        deck = new Deck();
        players = new Player[3];
        aiPlayers = new AI[2];
        accusation = new Card[3];

        //Main Panel.
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 1));
        setBackground(Color.BLACK);

        //Fill envelope with cards.
        envelope = deck.fillEnvelope();

        //Create AI and Players.
        aiPlayers[0] = new AI(1, deck.dealHand(), selection.get(1));
        aiPlayers[1] = new AI(2, deck.dealHand(), selection.get(2));
        players[0] = new Player(0, deck.dealHand(), selection.get(0));
        players[1] = aiPlayers[0];
        players[2] = aiPlayers[1];

        //Initialize UI Components.
        hub = new Hub(players[0]);
        board = new Board(players);
        bottomPanel = new Console(players[0]);

        //Add Components.
        add(board);
        add(hub);
        add(bottomPanel);

        //Set JFrame conditionals.
        setSize(800, 810);
        setResizable(false);
        setName("Clue");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);

        dice = new AudioPlayer("Dice.wav");
        button = new AudioPlayer("Button.wav");

        //Add Mouse Listeners.
        addMouseListener(this);
        addMouseMotionListener(this);

        //Create Timers.
        diceRoll = new javax.swing.Timer(50, this);
        diceRollStop = new javax.swing.Timer(500, this);
        diceRollStop.setRepeats(false);

        //Add Action Listeners.
        hub.getMakeAccusation().addActionListener(this);
        hub.getMakeAssumption().addActionListener(this);
        hub.getEndTurn().addActionListener(this);
        hub.getAssumptionWindow().getButton().addActionListener(this);
        hub.getAccusationWindow().getButton().addActionListener(this);
        bottomPanel.getEnterButton().addActionListener(this);
        for(int i=0; i<bottomPanel.getHand().length; i++)
            bottomPanel.getHand()[i].addActionListener(this);


        System.out.println(envelope[0] + " " + envelope[1] + " " + envelope[2] );
    }

    /** Action Listener block that controls flow of game logic
     *  with a series of nested conditionals.  */
    public void actionPerformed(ActionEvent e){

        if(e.getSource() != diceRoll && e.getSource() != diceRollStop)
            button.play();

        //If console isn't empty (forcing enter button press).
        if(!bottomPanel.messageConfirmed()) {

            //If enter button clicked.
            if (e.getSource() == bottomPanel.getEnterButton()) {

                //Clear console.
                bottomPanel.clearConsoleMessage();

                //If not human, Get AI next action.
                if(!humanTurn && !inDisprove && !inAccuse)
                    performAIAction(aiPlayers[turn-1].getNextAction(turnToggle));

                //If in Assumption State.
                else if(inDisprove)
                    displayDisproval();

                //If in Accusation State.
                else if (inAccuse) {
                    checkAccusation();
                }

                //If beginning of turn.
                else if (turnToggle){
                    //If not in room.
                    if(!players[turn].getMovement().isInARoom()) {
                        //Reset assumption area and move player.
                        bottomPanel.resetAssumption();
                        dice.play();
                        diceRoll.start();
                        diceRollStop.start();
                    }
                    //Else set assumption area to current room.
                    else
                        bottomPanel.setRoomAssumption(players[turn].getMovement().getEquivalentRoom());
                }
                //Else increment turn.
                else
                    nextTurn();
            }
        }

        //If triggered by diceRoll timer.
        else if (e.getSource() == diceRoll) {
            int randomImage = rand.nextInt(6);
            hub.setDiceImage(Dice.values()[randomImage].getImage());
        }
        //If triggered by diceRollStop timer.
        else if (e.getSource() == diceRollStop) {
            displayMovement();
        }
        //If Human Player's turn to disprove assumption.
        else if (inDisprove && humanTurn) {
            //Detect which card human player is disproving with.
            for(int i=0; i<bottomPanel.getHand().length; i++)
                if (e.getSource() == bottomPanel.getHand()[i]){

                    //Leave assumption state.
                    inDisprove = false;
                    //Remove card from assuming AI Memory.
                    aiPlayers[turn-1].removeCard(players[0].getHand().get(i));

                    //Reset console panel and display disproval in console.
                    bottomPanel.resetButtons();
                    bottomPanel.changeConsoleMessage("You disproved " + players[turn].getName() + ".");
                }
        }
        //If Human Player's turn and not disproving.
        else if (humanTurn){

            //If human player clicked on Accusation button.
            if (e.getSource() == hub.getMakeAccusation())
                //Disable MainUI until Accusation Selection is complete.
                setEnabled(false);

            //If human player clicked on Assumption button.
            else if (e.getSource() == hub.getMakeAssumption())
                //Disable MainUI until Assumption Selection is complete.
                setEnabled(false);

            //If human player ended turn.
            else if (e.getSource() == hub.getEndTurn())
                //Increment turn.
                nextTurn();

            //If human player finalized accusation with in-window button press.
            else if (e.getSource() == hub.getAccusationWindow().getButton())
                //Run Accusation state.
                displayPlayerAccusation();

            //If human player finalized assumption with in-window button press.
            else if (e.getSource() == hub.getAssumptionWindow().getButton())
                //Run Assumption state.
                displayPlayerAssumption();
        }
    }

    /** Mouse Click listener used for setting player movement.  */
    public void mouseClicked(MouseEvent e){

        //If human player's turn to choose movement & not in a hall.
        if(canMove && humanTurn) {
            Rooms clicked = roomClicked(e.getPoint());

            //If point clicked is within a room, start movement timers (dice rolls).
            if (clicked != null && bottomPanel.messageConfirmed()) {
                players[turn].getMovement().setDestination(clicked);
                dice.play();
                diceRoll.start();
                diceRollStop.start();
            }
            else if (easterEggTrigger.contains(e.getPoint()))
                easterEggCount++;

            if(easterEggCount == 10) {
                board.startEgg();
                easterEggCount = 0;
            }
        }
    }

    /** Mouse Movement listener used for displaying intermediary passages.  */
    public void mouseMoved(MouseEvent e){

        //If human player's turn, player is in room, and all prompts are clear.
        if(humanTurn && canMove && bottomPanel.messageConfirmed()) {
            if (players[turn].getMovement().isInARoom()) {

                //Directory for pathway image.
                String directory = "Images/Display/Pathways/"
                        + players[turn].getMovement().getLocation().getName() + "/";

                Rooms hoverLocation = roomHovered(e.getPoint());

                //Show board image updated with pathway, or refresh original image.
                if (hoverLocation != null)
                    board.setBoardIcon(new ImageIcon(directory + hoverLocation.getName() + ".jpeg"));
                else
                    board.resetBoardIcon();
            }

            //Refresh original board image.
            else
                board.resetBoardIcon();
        }
    }

    /** roomHovered detects which room mouse pointer is located in.  */
    private Rooms roomHovered(Point point) {
        for (Rooms rooms : Rooms.values())
            if (rooms.getBoundaryBox().contains(point)
                    && !rooms.getBoundaryBox().contains(board.getGamePiecePoint(turn)))
                return rooms;
        return null;
    }

    /** roomClicked detects which room was clicked.  */
    private Rooms roomClicked(Point point){
        for (Rooms rooms : Rooms.values())
            if (rooms.getBoundaryBox().contains(point) && rooms != players[turn].getMovement().getLocation())
                return rooms;
        return null;
    }

    /** nextTurn increments the turn count by one, and shows appropriate
     *  prompts, depending on if next turn is a human or AI.  */
    public void nextTurn() {

        //Disable buttons.
        hub.toggleButtonsEnabled(false);

        //Reset count for easter egg.
        easterEggCount=0;

        //Allow player movement.
        canMove = true;
        turnToggle = true;

        //Increment turn.
        if (turn <2)
            turn++;
        else
            turn = 0;

        //Change player image display in hub.
        players[turn].getSound().play();
        hub.changeTurnIndicator(players[turn].getPlayerIcon());

        //Clear assumptions.
        bottomPanel.resetAssumption();

        //Determine human or AI turn.
        humanTurn = (turn == 0);

        //Display turn in console.
        bottomPanel.changeConsoleMessage(players[turn].getName() + "'s turn.");
    }

    /** displayPlayerAccusation gets input accusation as done by human.  */
    public void displayPlayerAccusation(){

        //Toggle state to in accusation.
        inAccuse = true;

        //Set panels to user-provided accusation and populate array with choices.
        bottomPanel.setSuspectAssumption(hub.getAccusationWindow().getSuspectGuess());
        accusation[2] = hub.getAccusationWindow().getSuspectGuess();
        accusation[1] = players[turn].getMovement().getEquivalentRoom();
        bottomPanel.setWeaponAssumption(hub.getAccusationWindow().getWeaponGuess());
        accusation[0] = hub.getAccusationWindow().getWeaponGuess();

        //Display accusation in console.
        bottomPanel.changeConsoleMessage(players[turn].getName() + " has made an accusation!");

        //Disable accusation window and set MainUI to clickable.
        hub.getAccusationWindow().setVisible(false);
        setEnabled(true);
        hub.toggleButtonsEnabled(false);

    }

    /** diplayAIAccusation gets input accusation as done by AI.  */
    public void displayAIAccusation(){

        //Toggle state to in accusation.
        inAccuse = true;

        //Get / Display Suspect accusation guess from AI.
        Card guess = aiPlayers[turn -1].getPersonGuess();
        bottomPanel.setSuspectAssumption(guess);
        accusation[2] = guess;

        //Get Rooms accusation guess from AI.
        accusation[1] = players[turn].getMovement().getEquivalentRoom();

        //Det / Display Weapon accusation guess from AI.
        guess = aiPlayers[turn - 1].getWeaponGuess();
        bottomPanel.setWeaponAssumption(guess);
        accusation[0] = guess;

        //Display accusation in console.
        bottomPanel.changeConsoleMessage(players[turn].getName() + " has made an accusation!");
    }

    /** displayPlayerAssumption gets input assumption as done by human.  */
    public void displayPlayerAssumption(){

        //Toggle state to in assumption.
        inDisprove = true;

        //Set Assumption Cards in Console to user's input.
        bottomPanel.setSuspectAssumption(hub.getAssumptionWindow().getSuspectGuess());
        bottomPanel.setWeaponAssumption(hub.getAssumptionWindow().getWeaponGuess());

        //Hide assumption window and re-enable MainUI.
        hub.getAssumptionWindow().setVisible(false);
        setEnabled(true);
        hub.toggleButtonsEnabled(false);

        //Display assumption in console.
        bottomPanel.changeConsoleMessage(players[turn].getName() + " has made an assumption!");
    }

    /** displayAIAssumption gets input assumption as done by AI.  */
    public void displayAIAssumption(){

        //Force AI to only make assumptions when not in assumption state.
        if(!inDisprove) {

            //Toggle assumption state.
            inDisprove = true;

            //Display AI's assumption.
            bottomPanel.setSuspectAssumption(aiPlayers[turn - 1].getPersonGuess());
            bottomPanel.setWeaponAssumption(aiPlayers[turn - 1].getWeaponGuess());

            //Display assumption in console.
            bottomPanel.changeConsoleMessage(players[turn].getName() + " has made an assumption!");
        }
    }

    /** displayMovement displays the results from a dice Roll for both human and AI.  */
    public void displayMovement(){

        //Stop diceRoll timers.
        dice.stop();
        diceRoll.stop();
        diceRollStop.stop();

        //Create random number between 1-6 and set corresponding dice image in hub.
        int roll = rand.nextInt(6) + 1;
        ImageIcon rollIcon = Dice.values()[roll - 1].getImage();
        hub.setDiceImage(rollIcon);

        //Move this player's game piece according to distance travelled.
        players[turn].getMovement().gamePieceMove(roll);

        //Remove any passageways that may be highlighted on the board.
        board.resetBoardIcon();

        //If this player moves into a room.
        if (players[turn].getMovement().isInARoom()) {

            //Enable buttons if it is a human player's turn.
            hub.toggleButtonsEnabled(humanTurn);

            //Set movement capability to false, disabling mouse listeners.
            canMove = false;

            //Change room assumption to matching room.
            bottomPanel.setRoomAssumption(players[turn].getMovement().getEquivalentRoom());

            //If player is AI.
            if(!humanTurn) {

                //Modify turnToggle state and display the AI's movements in console.
                turnToggle = false;
                bottomPanel.changeConsoleMessage(players[turn].getName()
                        + " moved to the " + players[turn].getMovement().getLocation().getName() + ".");
            }
        }
        //If player ended in hallway, end turn.
        else
            nextTurn();
    }

    /** displayDisproval checks opponent players ability to disprove and does so.
     *  Disprovals are made clockwise to the assuming player.
     *  The disproval is displayed if assuming player is human.
     *  AI re-weighting is done here, based on assumption.  */
    public void displayDisproval(){

        //Add guessed cards to an ArrayList.
        ArrayList<Card> guess = new ArrayList<Card>();
        guess.add(bottomPanel.getSuspectAssumption());
        guess.add(bottomPanel.getWeaponAssumption());
        guess.add(players[turn].getMovement().getEquivalentRoom());

        //Toggle humanTurn to true, for the purpose of button click recognition.
        humanTurn = true;

        //Adjust AI weights.
        for (int i= 0; i<aiPlayers.length; i++)
            if(turn-1 != i)
                aiPlayers[i].addWeight(guess);

        //Action is dependent on which player has assumed.
        switch(turn){

            //Human player has assumed:
            case 0:

                //If 1st AI can disprove.
                if(players[1].disproved(guess) != null) {
                    //Display disproval.
                    inDisprove = false;
                    bottomPanel.highlightDisproval(players[1].disproved(guess));
                    bottomPanel.changeConsoleMessage(players[1].getName() + " disproves you.");
                }
                //If 2nd  AI can disprove.
                else if(players[2].disproved(guess) != null) {
                    //Display disproval.
                    inDisprove = false;
                    bottomPanel.highlightDisproval(players[2].disproved(guess));
                    bottomPanel.changeConsoleMessage(players[2].getName() + " disproves you.");
                }
                //If neither player can disprove.
                else {
                    //Display no disproval in console.
                    bottomPanel.changeConsoleMessage("You were not disproved.");
                    inDisprove = false;
                    aiPlayers[0].makeIrrefutable(guess, 0);
                    aiPlayers[1].makeIrrefutable(guess, 0);
                }
                break;

            //1st AI has assumed.
            case 1:

                //If 2nd AI can disprove.
                if(players[2].disproved(guess) != null) {
                    //Remove disproving card from Memory and display that
                    //a disproval has occurred.
                    inDisprove = false;
                    aiPlayers[0].removeCard(players[2].disproved(guess));
                    bottomPanel.changeConsoleMessage(players[1].getName()
                            + " was disproved by " + players[2].getName());
                }
                //If human can disprove.
                else if(players[0].disproved(guess) != null)
                    //Highlight disproval buttons for user.
                    bottomPanel.highlightDisprovables(guess);
                //If neither player can disprove.
                else {
                    //Display no disproval in console.
                    inDisprove = false;
                    bottomPanel.changeConsoleMessage(players[1].getName() + " was not disproved.");
                    aiPlayers[0].makeIrrefutable(guess, 1);
                    aiPlayers[1].makeIrrefutable(guess, 1);
                }
                break;

            //2nd AI has assumed.
            case 2:

                //If human can disprove.
                if(players[0].disproved(guess) != null)
                    //Highlight disproval buttons for user.
                    bottomPanel.highlightDisprovables(guess);
                //If 1st AI can disprove.
                else if(players[1].disproved(guess) != null) {
                    //Remove disproving card from Memory and display that
                    //a disproval has occurred.
                    inDisprove = false;
                    aiPlayers[0].removeCard(players[1].disproved(guess));
                    bottomPanel.changeConsoleMessage(players[2].getName()
                            + " was disproved by " + players[1].getName());
                }
                //If neither player can disprove.
                else {
                    //Display no disproval in console.
                    inDisprove = false;
                    bottomPanel.changeConsoleMessage(players[2].getName() + " was not disproved.");
                    aiPlayers[0].makeIrrefutable(guess, 2);
                    aiPlayers[1].makeIrrefutable(guess, 2);
                }
                break;
        }

        //Modify turnToggle;
        turnToggle = false;
    }

    /** performAIAction enacts AI's next move based on int provided
     *  by AI class logic.
     * @param actionValue action to be performed:
     *                    1: Movement from either room or hallway.
     *                    2: Accusation.
     *                    3: Assumption.  */
    private void performAIAction(int actionValue){
        switch (actionValue){
            case 1:
                dice.play();
                diceRoll.start();
                diceRollStop.start();
                break;
            case 2:
                displayAIAccusation();
                break;
            case 3:
                displayAIAssumption();
                break;
        }
    }

    /** Check an accusation against envelope.
     *   If correct display win screen, else display Game Over.  */
    private void checkAccusation(){

        boolean winCheck = true;

        for (int i=0; i<envelope.length; i++) {
            if (envelope[i] != accusation[i])
                winCheck = false;
        }

        AudioPlayer.loopMain(false);

        if(winCheck && turn ==0)
            new GameOverUI("Images/Display/Main Menu/win_screen.png");
        else
            new GameOverUI("Images/Display/Main Menu/lose_screen.png");

        hub.getNoteBookWindow().dispose();
        dispose();
    }

    /** Required for abstract implementations, but not used. */
    public void mousePressed(MouseEvent e){}
    public void mouseReleased(MouseEvent e){}
    public void mouseEntered(MouseEvent e){}
    public void mouseExited(MouseEvent e){}
    public void mouseDragged(MouseEvent e){}
}
