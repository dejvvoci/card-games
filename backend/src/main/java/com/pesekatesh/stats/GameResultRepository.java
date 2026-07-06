package com.pesekatesh.stats;

import com.pesekatesh.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {
    long countByUserAndGameType(User user, GameType gameType);
    long countByUserAndGameTypeAndPlacement(User user, GameType gameType, int placement);
}
