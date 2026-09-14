package com.sheltersim.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sheltersim.engine.OrientationCache;
import com.sheltersim.model.Configuration;
import com.sheltersim.model.Orientation;
import com.sheltersim.model.PlacedObject;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class JsonSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void write(List<Configuration> configs,
                      long evaluated, long valid, long runtimeMs,
                      String outputPath, OrientationCache cache) throws IOException {
        JsonObject root = new JsonObject();

        root.add("orientations", serializeOrientations(cache));

        JsonObject stats = new JsonObject();
        stats.addProperty("evaluated", evaluated);
        stats.addProperty("valid", valid);
        stats.addProperty("runtimeMs", runtimeMs);
        root.add("stats", stats);

        root.add("configurations", serializeConfigs(configs, true));

        writeTo(root, outputPath);
    }

    // Working copy: no stats block, no rank field.
    public void writeWorkingCopy(List<Configuration> configs, String path,
                                 OrientationCache cache) throws IOException {
        JsonObject root = new JsonObject();
        root.add("orientations", serializeOrientations(cache));
        root.add("configurations", serializeConfigs(configs, false));
        writeTo(root, path);
    }

    private JsonObject serializeOrientations(OrientationCache cache) {
        JsonObject obj = new JsonObject();
        for (Orientation o : cache.getShelterOrientations())
            obj.add("Shelter/" + o.id, cellMapToJson(o.cellMap));
        for (Orientation o : cache.getKitchenOrientations())
            obj.add("Kitchen/" + o.id, cellMapToJson(o.cellMap));
        for (Orientation o : cache.getBathroomOrientations())
            obj.add("Bathroom/" + o.id, cellMapToJson(o.cellMap));
        return obj;
    }

    private JsonArray cellMapToJson(char[][] cellMap) {
        JsonArray arr = new JsonArray();
        for (char[] row : cellMap)
            arr.add(new String(row));
        return arr;
    }

    private JsonArray serializeConfigs(List<Configuration> configs, boolean includeRank) {
        JsonArray arr = new JsonArray();
        for (Configuration cfg : configs) {
            JsonObject c = new JsonObject();
            if (includeRank) c.addProperty("rank", cfg.rank);
            int sumBath = 0, sumKit = 0;
            for (int d : cfg.bathroomDists) sumBath += d;
            for (int d : cfg.kitchenDists)  sumKit  += d;
            c.addProperty("score", cfg.score);
            c.addProperty("bathroomDistance", sumBath);
            c.addProperty("kitchenDistance", sumKit);
            c.addProperty("bathroomDistanceNormalized", cfg.bathroomDistanceNormalized);
            c.addProperty("kitchenDistanceNormalized", cfg.kitchenDistanceNormalized);
            JsonArray bathArr = new JsonArray();
            for (int d : cfg.bathroomDists) bathArr.add(d);
            JsonArray kitArr = new JsonArray();
            for (int d : cfg.kitchenDists)  kitArr.add(d);
            c.add("bathroomDists", bathArr);
            c.add("kitchenDists", kitArr);
            c.add("objects", serializeObjects(cfg.objects));
            arr.add(c);
        }
        return arr;
    }

    private JsonArray serializeObjects(List<PlacedObject> objects) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < objects.size(); i++) {
            PlacedObject p = objects.get(i);
            JsonObject obj = new JsonObject();
            obj.addProperty("type", capitalize(p.type.name()));
            obj.addProperty("index", i + 1);
            obj.addProperty("orientationId", p.orientation.id);
            obj.addProperty("topLeftRow", p.topLeftRow);
            obj.addProperty("topLeftCol", p.topLeftCol);
            arr.add(obj);
        }
        return arr;
    }

    private void writeTo(JsonObject root, String path) throws IOException {
        java.io.File file = new java.io.File(path);
        java.io.File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();

        try (Writer w = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
