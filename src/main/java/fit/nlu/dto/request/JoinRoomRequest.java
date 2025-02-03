package fit.nlu.dto.request;

import fit.nlu.model.Player;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {
    private String roomId;
    private Player player;
}
