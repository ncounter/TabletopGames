package pandemic;

import actions.*;
import components.*;
import content.*;
import core.*;
import pandemic.actions.*;
import utilities.Hash;

import java.util.Random;

import static pandemic.Constants.*;
import static pandemic.actions.MovePlayer.placePlayer;

public class PandemicForwardModel implements ForwardModel {

    /**
     * Random generator for this game.
     */
    protected Random rnd;
    
    @Override
    public void setup(GameState firstState) {

        PandemicGameState state = (PandemicGameState) firstState;
        PandemicParameters gameParameters = (PandemicParameters) firstState.getGameParameters();
        rnd = new Random(gameParameters.game_seed);

        // 1 research station in Atlanta
        new AddResearchStation("Atlanta").execute(state);

        // init counters
        firstState.findCounter("Outbreaks").setValue(0);
        firstState.findCounter("Research Stations").setValue(gameParameters.n_research_stations);
        firstState.findCounter("Infection Rate").setValue(0);
        for (String color : Constants.colors) {
            firstState.findCounter("Disease " + color).setValue(0);
        }

        // infection
        Deck infectionDeck = firstState.findDeck("Infections");
        Deck infectionDiscard = firstState.findDeck("Infection Discard");
        infectionDeck.shuffle(rnd);
        int nCards = gameParameters.n_infection_cards_setup;
        int nTimes = gameParameters.n_infections_setup;
        for (int j = 0; j < nTimes; j++) {
            for (int i = 0; i < nCards; i++) {
                Card c = infectionDeck.draw();

                // Place matching color (nTimes - j) cubes and place on matching city
                new InfectCity(gameParameters, c, nTimes - j).execute(state);

                // Discard card
                new DrawCard(infectionDeck, infectionDiscard).execute(state);
            }
        }

        // give players cards
        Deck playerCards = firstState.findDeck("Player Roles");
        Deck playerDeck = firstState.findDeck("Player Deck");
        playerCards.shuffle(rnd);
        int nCardsPlayer = gameParameters.n_cards_per_player.get(state.getNPlayers());
        long maxPop = 0;
        int startingPlayer = -1;

        for (int i = 0; i < state.getNPlayers(); i++) {
            // Draw a player card
            Card c = playerCards.draw();

            // Give the card to this player
            Area playerArea = state.getAreas().get(i);
            playerArea.setComponent(Constants.playerCardHash, c);

            // Also add this player in Atlanta
            placePlayer(state, "Atlanta", i);

            // Give players cards
            Deck playerHandDeck = (Deck) playerArea.getComponent(Constants.playerHandHash);

            playerDeck.shuffle(rnd);
            for (int j = 0; j < nCardsPlayer; j++) {
                new DrawCard(playerDeck, playerHandDeck).execute(state);
            }

            for (Card card: playerHandDeck.getCards()) {
                Property property = card.getProperty(Hash.GetInstance().hash("population"));
                if (property != null){
                    long pop = ((PropertyLong) property).value;
                    if (pop > maxPop) {
                        startingPlayer = i;
                        maxPop = pop;
                    }
                }
            }
        }

        // Epidemic cards
        playerDeck.shuffle(rnd);
        int noCards = playerDeck.getCards().size();
        int noEpidemicCards = gameParameters.n_epidemic_cards;
        int range = noCards / noEpidemicCards;
        for (int i = 0; i < noEpidemicCards; i++) {
            int index = i * range + i + rnd.nextInt(range);

            Card card = new Card();
            card.setProperty(Hash.GetInstance().hash("name"), new PropertyString("epidemic"));
            new AddCardToDeck(card, playerDeck, index).execute(state);

        }

        // Player with highest population starts
        state.setActivePlayer(startingPlayer);
    }

    @Override
    public void next(GameState currentState, Action action) {
        PandemicGameState pgs = (PandemicGameState)currentState;
        PandemicParameters gameParameters = (PandemicParameters) currentState.getGameParameters();

        if (currentState.getReactivePlayers().size() == 0) {
            // Only advance round step if no one is reacting
            currentState.roundStep += 1;
        }
        playerActions(pgs, action);

        if (action instanceof CureDisease) {
            // Check win condition
            boolean all_cured = true;
            for (String c : Constants.colors) {
                if (pgs.findCounter("Disease " + c).getValue() < 1) all_cured = false;
            }
            if (all_cured) {
                currentState.setGameOver(GAME_WIN);
                System.out.println("WIN!");
            }
        }

        boolean reacted = currentState.removeReactivePlayer();  // Reaction (if any) done

        if (!reacted && pgs.roundStep >= gameParameters.n_actions_per_turn) {
            pgs.roundStep = 0;
            drawCards(pgs, gameParameters);

            if (!pgs.isQuietNight()) {
                infectCities(pgs, gameParameters);
                pgs.setQuietNight(false);
            }

            // Set the next player as active
            pgs.nextPlayer();
        }

        // TODO: wanna play event card?
    }

    private void playerActions(PandemicGameState currentState, Action action) {
        action.execute(currentState);
        if (action instanceof QuietNight) {
            currentState.setQuietNight(true);
        }
    }

    private void drawCards(GameState currentState, PandemicParameters gameParameters) {
        int noCardsDrawn = gameParameters.n_cards_draw;
        int activePlayer = currentState.getActingPlayer();

        String tempDeckID = currentState.tempDeck();
        DrawCard action = new DrawCard("Player Deck", tempDeckID);
        for (int i = 0; i < noCardsDrawn; i++) {  // Draw cards for active player from player deck into a new deck
            Deck cityDeck = currentState.findDeck("Player Deck");
            boolean canDraw = cityDeck.getCards().size() > 0;

            // if player cannot draw it means that the deck is empty -> GAME OVER
            if (!canDraw){
                currentState.setGameOver(GAME_LOSE);
                System.out.println("No more cards to draw");
            }
            action.execute(currentState);

        }
        Deck tempDeck = currentState.findDeck(tempDeckID);
        boolean epidemic = false;
        for (Card c : tempDeck.getCards()) {  // Check the drawn cards

            // If epidemic card, do epidemic, only one per draw
            if (((PropertyString)c.getProperty(nameHash)).value.hashCode() == Constants.epidemicCard) {
                if (!epidemic) {
                    epidemic(currentState, gameParameters);
                    epidemic = true;
                }
            } else {  // Otherwise, give card to player
                Area area = currentState.getAreas().get(activePlayer);
                Deck deck = (Deck) area.getComponent(Constants.playerHandHash);
                if (deck != null) {
                    // deck size doesn't go beyond 7  TODO: action list should only contain discard card action
                    if (!new AddCardToDeck(c, deck).execute(currentState)){
                        // player needs to discard a card
                        currentState.addReactivePlayer(activePlayer);
                    }
                }
            }
        }
        currentState.clearTempDeck();
    }

    private void epidemic(GameState currentState, PandemicParameters gameParameters) {

        // 1. infection counter idx ++
        currentState.findCounter("Infection Rate").increment(1);

        // 2. 3 cubes on bottom card in infection deck, then add this card on top of infection discard
        Card c = currentState.findDeck("Infections").pickLast();
        if (c == null){
            // cannot draw card
            currentState.setGameOver(GAME_LOSE);
            System.out.println("No more cards to draw");
            return;
        }
        new InfectCity(gameParameters, c, gameParameters.n_cubes_epidemic).execute(currentState);
        if (checkInfectionGameEnd(currentState, gameParameters, c)) return;

        // 3. shuffle infection discard deck, add back on top of infection deck
        Deck infectionDiscard = currentState.findDeck("Infection Discard");
        infectionDiscard.shuffle(rnd);
        for (Card card: infectionDiscard.getCards()) {
            new AddCardToDeck(card, currentState.findDeck("Infections")).execute(currentState);
        }
    }

    private void infectCities(GameState currentState, PandemicParameters gameParameters) {
        Counter infectionCounter = currentState.findCounter("Infection Rate");
        int noCardsDrawn = gameParameters.infection_rate[infectionCounter.getValue()];
        String tempDeckID = currentState.tempDeck();
        DrawCard action = new DrawCard("Infections", tempDeckID);
        for (int i = 0; i < noCardsDrawn; i++) {  // Draw cards for active player from player deck into a new deck
            action.execute(currentState);
        }
        Deck tempDeck = currentState.findDeck(tempDeckID);
        for (Card c : tempDeck.getCards()) {  // Check the drawn cards
            new InfectCity(gameParameters, c, gameParameters.n_cubes_infection).execute(currentState);
            if (checkInfectionGameEnd(currentState, gameParameters, c)) return;
        }
        currentState.clearTempDeck();
    }

    private boolean checkInfectionGameEnd(GameState currentState, PandemicParameters gameParameters, Card c) {
        if (currentState.findCounter("Outbreaks").getValue() >= gameParameters.lose_max_outbreak) {
            currentState.setGameOver(GAME_LOSE);
            System.out.println("Too many outbreaks");
            return true;
        }
        if (currentState.findCounter("Disease Cube " + ((PropertyColor)c.getProperty(colorHash)).valueStr).getValue() < 0) {
            currentState.setGameOver(GAME_LOSE);
            System.out.println("Ran out of disease cubes");
            return true;
        }

        // Discard this infection card
        new AddCardToDeck(c, currentState.findDeck("Infection Discard")).execute(currentState);
        return false;
    }
}
