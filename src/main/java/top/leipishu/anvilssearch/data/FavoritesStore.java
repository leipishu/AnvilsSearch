package top.leipishu.anvilssearch.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 强化收藏持久化。
 * 文件：config/anvilssearch-favorites.json
 * 格式：{ "modifiers": ["tconstruct:sharpness", ...] }
 *
 * 写入策略：debounce，切换后 1 秒内的多次改动合并成一次 IO。
 * 由 AnvilsSearch.onClientTick 与 ModifierSearchTab.onDeactivate 触发 flush。
 */
public final class FavoritesStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FAVORITES = new LinkedHashSet<>();
    private static final Path FILE = FMLPaths.CONFIGDIR.get()
            .resolve("anvilssearch-favorites.json");

    private static boolean loaded = false;
    private static boolean dirty = false;
    private static long dirtyAt = 0L;
    private static final long WRITE_DELAY_MS = 1000L;

    private FavoritesStore() {}

    public static boolean isFavorite(String id) {
        if (id == null || id.isEmpty()) return false;
        ensureLoaded();
        return FAVORITES.contains(id);
    }

    public static void toggle(String id) {
        if (id == null || id.isEmpty()) return;
        ensureLoaded();
        if (!FAVORITES.remove(id)) {
            FAVORITES.add(id);
        }
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    public static Set<String> all() {
        ensureLoaded();
        return new LinkedHashSet<>(FAVORITES);
    }

    public static void flushIfDirty() {
        if (!dirty) return;
        if (System.currentTimeMillis() - dirtyAt < WRITE_DELAY_MS) return;
        flush();
    }

    public static void flush() {
        if (!dirty && loaded) return;
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String s : FAVORITES) arr.add(s);
            root.add("modifiers", arr);
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("[Anvil's Search] failed to save favorites: " + e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            JsonElement arr = obj.get("modifiers");
            if (arr == null || !arr.isJsonArray()) return;
            FAVORITES.clear();
            for (JsonElement e : arr.getAsJsonArray()) {
                if (e != null && e.isJsonPrimitive()) {
                    FAVORITES.add(e.getAsString());
                }
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] failed to load favorites: " + t);
        }
    }
}