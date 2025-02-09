package fit.nlu.model;

import lombok.Data;
import lombok.Getter;

@Data
public class DrawingData {
    public static enum ActionType {
        START, MOVE, UP, CLEAR, UNDO, REDO
    }

    private float x;
    private float y;
    @Getter
    private ActionType action; // "start", "move", "up"
    private int color;
    private float strokeWidth;

    public DrawingData(float x, float y, ActionType action, int color, float strokeWidth) {
        this.x = x;
        this.y = y;
        this.action = action;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    public ActionType getActionType() {
        return action;
    }
}
