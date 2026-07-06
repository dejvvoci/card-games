package com.pesekatesh.stats;

import com.pesekatesh.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/** Rezultati i një lojtari të vetëm në një ndeshje të përfunduar (1 rresht për lojtar për ndeshje) */
@Entity
@Table(name = "game_result")
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameType gameType;

    @Column(nullable = false)
    private String roomId;

    /** Vendi përfundimtar në ndeshje: 1 = fitues, 2..N sipas renditjes së pikëve */
    @Column(nullable = false)
    private int placement;

    @Column(nullable = false)
    private int totalPlayers;

    @Column(nullable = false)
    private Instant playedAt = Instant.now();

    protected GameResult() {}

    public GameResult(User user, GameType gameType, String roomId, int placement, int totalPlayers) {
        this.user = user;
        this.gameType = gameType;
        this.roomId = roomId;
        this.placement = placement;
        this.totalPlayers = totalPlayers;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public GameType getGameType() { return gameType; }
    public String getRoomId() { return roomId; }
    public int getPlacement() { return placement; }
    public int getTotalPlayers() { return totalPlayers; }
    public Instant getPlayedAt() { return playedAt; }
}
