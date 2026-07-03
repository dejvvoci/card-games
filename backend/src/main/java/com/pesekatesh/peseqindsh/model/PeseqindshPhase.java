package com.pesekatesh.peseqindsh.model;

public enum PeseqindshPhase {
    WAITING_FOR_PLAYERS,
    CUTTING,        // prerja e letrave para shpërndarjes
    PLAYING,        // dora aktive: hedhje + tërheqje
    ROUND_FINISHED, // dikush mbylli dorën, pikët e raundit u llogaritën
    GAME_OVER       // dikush arriti 500 pikë
}
