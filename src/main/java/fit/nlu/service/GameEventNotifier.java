package fit.nlu.service;

import fit.nlu.model.Turn;

public interface GameEventNotifier {
    void notifyGameStart(String roomId);
    void notifyRoundStart(String roomId, int roundNumber);
    void notifyTurnStart(String roomId, Turn turn);
    void notifyTurnEnd(String roomId, Turn turn);
    void notifyRoundEnd(String roomId, int roundNumber);
    void notifyGameEnd(String roomId);
    void notifyTurnTimeUpdate(String roomId, Turn turn);

    void notifyTurnResultCountdown(String roomId, Turn turn);
}
