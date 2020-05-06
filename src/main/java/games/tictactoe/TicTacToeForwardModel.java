package games.tictactoe;

import actions.IAction;
import components.Grid;
import core.AbstractGameState;
import core.ForwardModel;
import turnorder.TurnOrder;


public class TicTacToeForwardModel extends ForwardModel {


    @Override
    public void next(AbstractGameState currentState, TurnOrder turnOrder, IAction action) {
        action.Execute(currentState, turnOrder);
        if (turnOrder.getTurnCounter() == 9)
            currentState.endGame();

        checkWinCondition((TicTacToeGameState) currentState);
        turnOrder.endPlayerTurn(currentState);
    }

    private void checkWinCondition(TicTacToeGameState gameState){
        Grid<Character> grid = gameState.getGrid();

        //check rows
        for (int x = 0; x < grid.getWidth(); x++){
            if (grid.getElement(x, 0).equals(grid.getElement(x, 1)) &&
                    grid.getElement(x, 0).equals(grid.getElement(x, 2)) &&
                    !grid.getElement(x, 0).equals(' ')){
                gameState.registerWinner(grid.getElement(x, 0));
                return;
            }
        }

        // check columns
        for (int y = 0; y < grid.getHeight(); y++){
            if (grid.getElement(0, y).equals(grid.getElement(1, y)) &&
                    grid.getElement(0, y).equals(grid.getElement(2, y)) &&
                    !grid.getElement(0, y).equals(' ')){
                gameState.registerWinner(grid.getElement(0, y));
                return;
            }
        }

        //check diagonals
        if (grid.getElement(0, 0).equals(grid.getElement(1, 1)) &&
                grid.getElement(0, 0).equals(grid.getElement(2, 2)) &&
                !grid.getElement(1, 1).equals(' ')){
            gameState.registerWinner(grid.getElement(1, 1));
            return;
        }

        if (grid.getElement(0, 2).equals(grid.getElement(1, 1)) &&
                grid.getElement(0, 2).equals(grid.getElement(2, 0)) &&
                !grid.getElement(0, 2).equals(' ')){
            gameState.registerWinner(grid.getElement(1, 1));
        }
    }
}