package fit.nlu.model;

public class MessageMetadata  {
    private boolean isGuess;
    private boolean isCorrect;
    private boolean isSystemMessage;
    private String color;

    public MessageMetadata() {
        this.isGuess = false;
        this.isCorrect = false;
        this.isSystemMessage = false;
    }

    public boolean isGuess() {
        return isGuess;
    }

    public void setGuess(boolean guess) {
        isGuess = guess;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        isSystemMessage = systemMessage;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
