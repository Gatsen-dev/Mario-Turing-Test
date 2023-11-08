package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;
import engine.sprites.Mario;

public class Agent implements MarioAgent {

    private enum JumpType {ENEMY, WALL, GAP, NONE}
    private boolean[] action;
    private JumpType jumpType = JumpType.NONE;
    private int jumpHeight = 0;
    private int jumpCounter = 0;

    private class Rectangle {
        private float x, y, width, height;

        public Rectangle(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(float x, float y) {
            return x >= this.x && y >= this.y && x <= this.x + this.width && y <= this.y + this.height;
        }
    }

    private boolean enemyNearby(MarioForwardModel model, Rectangle rect) {
        float[] enemyPos = model.getEnemiesFloatPos();
        for (int i=0; i<model.getEnemiesFloatPos().length; i+=3) {
            if (rect.contains(enemyPos[i+1], enemyPos[i+2])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        action = new boolean[MarioActions.numberOfActions()];
        action[MarioActions.RIGHT.getValue()] = true;
    }

    private void setJump(JumpType type, int height) {
        jumpType = type;
        jumpHeight = height;
        jumpCounter = 0;
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        boolean enemyNearby = enemyNearby(model, new Rectangle(model.getMarioFloatPos()[0] - 50, model.getMarioFloatPos()[1] - 50, 100.0f, 100.0f));
        if (enemyNearby && jumpType == JumpType.NONE && model.isMarioOnGround()) {
            setJump(JumpType.ENEMY, 2);
        }

        if (jumpType == JumpType.ENEMY) {
            action[MarioActions.JUMP.getValue()] = true;
            jumpCounter++;
        }
        if (jumpCounter >= jumpHeight) {
            action[MarioActions.JUMP.getValue()] = false;
            setJump(JumpType.NONE, -1);
        }


        return action;
    }

    @Override
    public String getAgentName() {
        return "myAgent";
    }
}
