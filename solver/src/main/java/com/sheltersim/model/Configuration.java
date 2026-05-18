package com.sheltersim.model;

import java.util.List;

public final class Configuration {
    public final List<PlacedObject> objects; // length 6
    public int score;
    public int rank; // assigned after sorting; mutable

    public Configuration(List<PlacedObject> objects, int score) {
        this.objects = objects;
        this.score = score;
    }
}
