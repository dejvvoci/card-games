package com.pesekatesh.stats;

/** Statistika të grumbulluara për një lojtar në një lojë specifike */
public class StatsResponse {
    public long gamesPlayed;
    public long wins;      // placement == 1
    public long second;    // placement == 2
    public long third;     // placement == 3 (gjithmonë 0 për lojëra 2-lojtarësh si Peseqindsh)
    public long fourth;    // placement == 4 (gjithmonë 0 për lojëra 2-lojtarësh si Peseqindsh)
}
