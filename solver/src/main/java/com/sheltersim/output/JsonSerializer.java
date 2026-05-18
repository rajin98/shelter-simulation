package com.sheltersim.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sheltersim.model.Configuration;
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
                      String outputPath) throws IOException {
        JsonObject root = new JsonObject();

        JsonObject stats = new JsonObject();
        stats.addProperty("evaluated", evaluated);
        stats.addProperty("valid", valid);
        stats.addProperty("runtimeMs", runtimeMs);
        root.add("stats", stats);

        root.add("configurations", serializeConfigs(configs, true));

        writeTo(root, outputPath);
    }

    // Working copy: no stats block, no rank field.
    public void writeWorkingCopy(List<Configuration> configs, String path) throws IOException {
        JsonObject root = new JsonObject();
        root.add("configurations", serializeConfigs(configs, false));
        writeTo(root, path);
    }

    private JsonArray serializeConfigs(List<Configuration> configs, boolean includeRank) {
        JsonArray arr = new JsonArray();
        for (Configuration cfg : configs) {
            JsonObject c = new JsonObject();
            if (includeRank) c.addProperty("rank", cfg.rank);
            c.addProperty("score", cfg.score);
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
            obj.add("cells", serializeCells(p));
            arr.add(obj);
        }
        return arr;
    }

    private JsonArray serializeCells(PlacedObject p) {
        JsonArray arr = new JsonArray();
        int rows = p.orientation.rows;
        int cols = p.orientation.cols;
        for (int dr = 0; dr < rows; dr++) {
            for (int dc = 0; dc < cols; dc++) {
                char ch = p.orientation.cellMap[dr][dc];
                if (ch == '0') continue;
                JsonObject cell = new JsonObject();
                cell.addProperty("row", p.topLeftRow + dr);
                cell.addProperty("col", p.topLeftCol + dc);
                cell.addProperty("label", String.valueOf(ch));
                arr.add(cell);
            }
        }
        return arr;
    }

    private void writeTo(JsonObject root, String path) throws IOException {
        // Ensure parent directories exist.
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
