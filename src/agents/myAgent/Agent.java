package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;
import engine.sprites.Mario;

import java.util.Arrays;

public class Agent implements MarioAgent {

    private enum JumpType {ENEMY, WALL, GAP, STAIRS, NONE}
    private boolean[] action;
    private JumpType jumpType = JumpType.NONE;
    private int jumpHeight = 0;
    private int jumpCounter = 0;
    private int leftCounter = 0;
    private float prevY;

    /**
     * A class that builds a rectangle using the inputted starting coordinates, width, and height
     * This was used in the TrondEllingsen agent, and is used to act as sort of a collision check with enemies.
     */
    private class Rectangle {
        private float x, y, width, height;

        public Rectangle(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * Checks whether a coordinate is contained within this rectangle.
         * @param x the x coordinate
         * @param y the y coordinate
         * @return A boolean stating whether the coordinate is bounded by the rectangle.
         */
        public boolean contains(float x, float y) {
            return x >= this.x && y >= this.y && x <= this.x + this.width && y <= this.y + this.height;
        }
    }

    /**
     * Gets the closest enemy's position as an array of coordinates
     * @param model Mario's model
     * @param rect A Rectangle to check whether an enemy is within this rectangle's bounds
     * @return An array of [x,y] coordinates of the closest enemy's position
     */
    private float[] getClosestEnemyPos(MarioForwardModel model, Rectangle rect) {
        float[] enemyPositions = model.getEnemiesFloatPos(); // Get all enemies & their pos
        for (int i = 0; i < enemyPositions.length; i += 3) {
            if (rect.contains(enemyPositions[i + 1], enemyPositions[i + 2])) {
                return new float[]{enemyPositions[i+1], enemyPositions[i+2]};
            }
        }
        return null; // No enemy within the rectangle's bounds
    }

//    private float[] checkForEnemyOnScreen(MarioForwardModel model, Rectangle rect) {
//        float[] enemyPositions = model.getEnemiesFloatPos(); // Get all enemies & their pos
//        for (int i = 0; i < enemyPositions.length; i += 3) {
//            if (rect.contains(enemyPositions[i + 1], enemyPositions[i + 2])) {
//                return new float[]{enemyPositions[i+1], enemyPositions[i+2]};
//            }
//        }
//        return null;
//    }

    /**
     * Checks if a wall is in front of Mario.
     * This was adapted from the TrondEllingsen Agent, and modified to suit our needs
     * @param model Mario's model
     * @return An integer representing the height of the wall in front.
     */
    private int wallInFront(MarioForwardModel model) {
        int marioTileX = model.getMarioScreenTilePos()[0];
        int marioTileY = model.getMarioScreenTilePos()[1];
        int[][] scene = model.getScreenSceneObservation();

        // Check for wall in front of Mario. The +1 in index accounts for Mario's width and the next tile in front
        int y = marioTileY;
        int height = 0;
        while (y > 0 && scene[marioTileX + 1][y] != 0) {
            height++;
            y--;
        }
        return height;
    }

    /**
     * Checks if there is a set of stairs in front of mario.
     * @param model Mario's model
     * @return An integer representing the number of stairs in front, or 0 if there aren't any stairs.
     */
    private int stairsInFront(MarioForwardModel model){
        int marioTileX = model.getMarioScreenTilePos()[0];
        int marioTileY = model.getMarioScreenTilePos()[1];
        int[][] scene = model.getScreenSceneObservation();

        int y = marioTileY; 
        int x = marioTileX;
        int stairCounter = 0;
        while ((y > 0 &&  x > 0) && scene[x+1][y] != 0 && stairCounter < 4){ // Cutoff at no greater than 4 stairs
            stairCounter++; 
            y--;
            x++;
        }
        return stairCounter; 
    }

    /**
     * Check if there is a sizable gap in front of mario
     * A gap exists if there is a hole that is greater than 3 tiles deep.
     * The loop continues in the +x direction until either a gap is not found or the max width of 8 tiles is reached.
     * @param model Mario's model
     * @return An integer representing the width of the gap.
     */
    private int gapInFront(MarioForwardModel model) {
        int marioTileX = model.getMarioScreenTilePos()[0];
        int marioTileY = model.getMarioScreenTilePos()[1];
        int[][] scene = model.getScreenSceneObservation();

        int x = marioTileX + 1;
        int gapsize = 0;
        while (x < marioTileX + 5) { // Cutoff at 5 tiles
            for (int y = Math.max(marioTileY, 0); y <= marioTileY + 3; y++) {
                if (y >= 16) break;
                if (scene[x][y] != 0) { // Block 1 space in front of mario + y position is not empty
                    return gapsize;
                }
            }
            gapsize++;
            x++;
        }

        return gapsize;
    }

    /**
     * Checks if a ceiling is above mario up to 10 tiles up
     * @param model Mario's Model
     * @return The height of the ceiling, or -1 if there is no ceiling
     */
    private int hasCeilingAbove(MarioForwardModel model) {
        int marioTileX = model.getMarioScreenTilePos()[0];
        int marioTileY = model.getMarioScreenTilePos()[1];
        int[][] scene = model.getScreenSceneObservation();
        int height = -1;

        for (int y = marioTileY; y >= 0; y--) {
            if (scene[marioTileX][y] != 0) {
                height = marioTileY - y;
            }
            if (action[MarioActions.LEFT.getValue()] && scene[marioTileX - 1][y] != 0) {
                height = marioTileY - y;
            }
            if (action[MarioActions.RIGHT.getValue()] && scene[marioTileX + 1][y] != 0) {
                height = marioTileY - y;
            }
        }
        return height;
    }

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        action = new boolean[MarioActions.numberOfActions()];
        action[MarioActions.RIGHT.getValue()] = true;
    }

    /**
     * Sets the type and height of a jump mario is about to make
     * @param type The type of jump, can be any of type JumpType (enum)
     * @param height The height of the jump mario is going to take.
     */
    private void setJump(JumpType type, int height) {
        jumpType = type;
        jumpHeight = height;
        jumpCounter = 0;
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {

        //Variables used throughout mario's decision making
        float[] marioPos = model.getMarioFloatPos();
        float[] enemyLocation = getClosestEnemyPos(model, new Rectangle(marioPos[0] - 48, marioPos[1] - 100, 96.0f, 200.0f));
        int ceilingHeight = hasCeilingAbove(model);
        boolean isFalling = (prevY < marioPos[1]);
        int wallHeight = wallInFront(model);
        int gapSize = gapInFront(model);
        int numStairs = stairsInFront(model);

        if (enemyLocation != null) { // Enemy detection
            if (enemyLocation[0] < marioPos[0] && model.isMarioOnGround()) { // Enemy to the left
                action[MarioActions.LEFT.getValue()] = true;
                action[MarioActions.RIGHT.getValue()] = false;
            }
            else {
                action[MarioActions.LEFT.getValue()] = false;
                action[MarioActions.RIGHT.getValue()] = true;
            }
            if (jumpType == JumpType.NONE) {
                if (Math.abs(enemyLocation[1] - marioPos[1]) < 2.0f) { // Enemy nearly same y level
                    setJump(JumpType.ENEMY, 1);
                }
                else if (enemyLocation[1] < marioPos[1] && Math.abs(enemyLocation[1] - marioPos[1]) > 8) { // Enemy above mario
                    if (Math.abs(enemyLocation[1] - marioPos[1]) <= 40) { // Enemy not significantly above mario
                        setJump(JumpType.ENEMY, ceilingHeight != -1 ? ceilingHeight - 1 : 0);
                    }
                }
                else if (enemyLocation[1] > marioPos[1]) { // Enemy below mario
                    if (Math.abs(enemyLocation[1] - marioPos[1]) > 40) { // Enemy significantly below mario
                        setJump(JumpType.ENEMY, 0);
                    }
                    else { // Enemy not significantly below mario
                        setJump(JumpType.ENEMY, 4);
                    }
                }
            }
        }

        //Wall detected jump
        if (jumpType == JumpType.NONE && wallHeight != 0 && model.isMarioOnGround()) {
            setJump(JumpType.WALL, wallHeight > 1 ? wallHeight + 3 : 2);
        }

        //Stair detected jump
        if((jumpType == JumpType.NONE || jumpType == JumpType.WALL) && numStairs > 2){
            setJump(JumpType.STAIRS, numStairs + 4);
        }

        //Gap detected jump
        if (jumpType == JumpType.NONE && gapSize > 0) {
            setJump(JumpType.GAP, 5);
        }

        // Forces mario to not chase to the left indefinitely
        if (action[MarioActions.LEFT.getValue()]) {
            leftCounter++;
        }
        else {
            leftCounter = 0;
        }
        if (leftCounter >= 15) {
            action[MarioActions.LEFT.getValue()] = false;
            leftCounter = 0;
        }

        //Setting mario's action based off of calculated conditions
        action[MarioActions.RIGHT.getValue()] = !((gapSize > 0 && isFalling) && !action[MarioActions.LEFT.getValue()]);
        action[MarioActions.SPEED.getValue()] = ((wallHeight >= 4) || (gapSize > 4) || (numStairs >= 4));
        action[MarioActions.JUMP.getValue()] = (jumpType != JumpType.NONE);

        // Jump timer for varying jump height
        if (action[MarioActions.JUMP.getValue()]) {
            jumpCounter++;
        }
        if (jumpCounter > jumpHeight) {
            action[MarioActions.JUMP.getValue()] = false;
            setJump(JumpType.NONE, -1);
        }

        //Sets his current Y as his prev Y for the next game tick
        prevY = marioPos[1];
        return action;
    }

    @Override
    public String getAgentName() {
        return "myAgent";
    }
}