package com.sheltersim.model;

import java.util.List;

public final class Configuration {
    public final List<PlacedObject> objects; // length 6

    /** Per-shelter BFS path lengths to the bathroom, one entry per shelter (length 4). */
    public final int[] bathroomDists;
    /** Per-shelter BFS path lengths to the kitchen room, one entry per shelter (length 4). */
    public final int[] kitchenDists;

    /**
     * Mean of per-shelter normalized bathroom distances, in [0, 1].
     * Set by Main after all configs are collected and global min/max are known.
     */
    public float bathroomDistanceNormalized;
    /**
     * Mean of per-shelter normalized kitchen distances, in [0, 1].
     * Set by Main after all configs are collected and global min/max are known.
     */
    public float kitchenDistanceNormalized;

    /** Weighted composite score — set by Main after normalization. Lower is better. */
    public float score;

    public int rank; // assigned after sorting; mutable

    public Configuration(List<PlacedObject> objects, int[] bathroomDists, int[] kitchenDists) {
        this.objects      = objects;
        this.bathroomDists = bathroomDists;
        this.kitchenDists  = kitchenDists;
    }
}
