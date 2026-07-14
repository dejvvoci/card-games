package com.pesekatesh.stats;

import com.pesekatesh.derr.DerrPlayer;
import com.pesekatesh.derr.DerrState;
import com.pesekatesh.model.GameState;
import com.pesekatesh.model.Player;
import com.pesekatesh.peseqindsh.model.PeseqindshPlayer;
import com.pesekatesh.peseqindsh.model.PeseqindshState;
import com.pesekatesh.user.User;
import com.pesekatesh.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class GameResultService {

    private final GameResultRepository gameResultRepository;
    private final UserRepository userRepository;

    public GameResultService(GameResultRepository gameResultRepository, UserRepository userRepository) {
        this.gameResultRepository = gameResultRepository;
        this.userRepository = userRepository;
    }

    /** Thirret kur një ndeshje Pesëkatësh mbaron (GAME_OVER) — regjistron vendin e secilit lojtar human i loguar */
    public void recordPesekateshResult(GameState state) {
        List<Player> ranked = state.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getTotalScore).reversed())
                .toList();
        int totalPlayers = ranked.size();
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            if (p.isBot() || p.getUserId() == null) continue;
            recordIfUserExists(p.getUserId(), GameType.PESEKATESH, state.getRoomId(), i + 1, totalPlayers);
        }
    }

    /** Thirret kur një ndeshje Peseqindsh mbaron (GAME_OVER) — regjistron vendin e secilit lojtar human i loguar */
    public void recordPeseqindshResult(PeseqindshState state) {
        List<PeseqindshPlayer> ranked = state.getPlayers().stream()
                .sorted(Comparator.comparingInt(PeseqindshPlayer::getTotalScore).reversed())
                .toList();
        int totalPlayers = ranked.size();
        for (int i = 0; i < ranked.size(); i++) {
            PeseqindshPlayer p = ranked.get(i);
            if (p.isBot() || p.getUserId() == null) continue;
            recordIfUserExists(p.getUserId(), GameType.PESEQINDSH, state.getRoomId(), i + 1, totalPlayers);
        }
    }

    /**
     * Thirret kur një ndeshje "Derri në Dorë" mbaron (GAME_OVER) — regjistron vendin e secilit lojtar
     * human i loguar, sipas rendit të shpëtimit (1 = i pari që shpëtoi, N = "Derri" i fundit).
     */
    public void recordDerrResult(DerrState state) {
        List<Integer> order = state.getFinishOrder();
        int totalPlayers = state.getPlayers().size();
        for (int i = 0; i < order.size(); i++) {
            DerrPlayer p = state.getPlayerBySeat(order.get(i));
            if (p == null || p.isBot() || p.getUserId() == null) continue;
            recordIfUserExists(p.getUserId(), GameType.DERR, state.getRoomId(), i + 1, totalPlayers);
        }
    }

    private void recordIfUserExists(Long userId, GameType gameType, String roomId, int placement, int totalPlayers) {
        userRepository.findById(userId).ifPresent(user ->
                gameResultRepository.save(new GameResult(user, gameType, roomId, placement, totalPlayers)));
    }

    public StatsResponse getStats(User user, GameType gameType) {
        StatsResponse response = new StatsResponse();
        response.gamesPlayed = gameResultRepository.countByUserAndGameType(user, gameType);
        response.wins = gameResultRepository.countByUserAndGameTypeAndPlacement(user, gameType, 1);
        response.second = gameResultRepository.countByUserAndGameTypeAndPlacement(user, gameType, 2);
        response.third = gameResultRepository.countByUserAndGameTypeAndPlacement(user, gameType, 3);
        response.fourth = gameResultRepository.countByUserAndGameTypeAndPlacement(user, gameType, 4);
        return response;
    }
}
