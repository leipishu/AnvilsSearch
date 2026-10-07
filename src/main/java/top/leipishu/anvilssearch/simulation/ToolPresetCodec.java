package top.leipishu.anvilssearch.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolPresetCodec {

    public static final String FORMAT  = "anvilssearch-tool";
    public static final int    VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ToolPresetCodec() {}

    public static String encode(ToolPreset preset) {
        if (preset == null || preset.toolId == null) return "";
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("tool", preset.toolId);
        root.addProperty("savedAt", preset.savedAt);

        JsonObject mats = new JsonObject();
        for (Map.Entry<Integer, MaterialId> e : preset.materials.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            mats.addProperty(String.valueOf(e.getKey()), e.getValue().toString());
        }
        root.add("materials", mats);

        return GSON.toJson(root);
    }

    public static ToolPreset decode(String json) {
        if (json == null) return null;
        String s = json.trim();
        if (s.isEmpty()) return null;

        try {
            JsonElement el = JsonParser.parseString(s);
            if (!el.isJsonObject()) return null;
            JsonObject root = el.getAsJsonObject();

            if (!root.has("format") || !root.has("tool")) return null;
            if (!FORMAT.equals(root.get("format").getAsString())) return null;

            String toolId = root.get("tool").getAsString();
            if (toolId.isEmpty()) return null;

            long savedAt = root.has("savedAt")
                    ? root.get("savedAt").getAsLong()
                    : System.currentTimeMillis();

            Map<Integer, MaterialId> mats = new LinkedHashMap<>();
            if (root.has("materials") && root.get("materials").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e
                        : root.getAsJsonObject("materials").entrySet()) {
                    try {
                        int idx = Integer.parseInt(e.getKey());
                        if (idx < 0) continue;
                        MaterialId mid = new MaterialId(e.getValue().getAsString());
                        mats.put(idx, mid);
                    } catch (Throwable ignored) {}
                }
            }

            return new ToolPreset(toolId, mats, savedAt);
        } catch (Throwable t) {
            return null;
        }
    }
}