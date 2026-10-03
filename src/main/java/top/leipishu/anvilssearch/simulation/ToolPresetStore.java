package top.leipishu.anvilssearch.simulation;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ToolPresetStore {

    private static final Path DIR =
            FMLPaths.CONFIGDIR.get().resolve("anvilssearch-presets");

    private ToolPresetStore() {}

    public static Path dir() { return DIR; }

    public static Path save(ToolPreset preset) {
        if (preset == null || preset.toolId == null) return null;
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(sanitize(preset.toolId) + ".json");
            Files.writeString(file, ToolPresetCodec.encode(preset),
                    StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            System.err.println("[Anvil's Search] save preset failed: " + e);
            return null;
        }
    }

    public static ToolPreset load(Path file) {
        if (file == null || !Files.exists(file)) return null;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return ToolPresetCodec.decode(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<ToolPreset> listAll() {
        List<ToolPreset> out = new ArrayList<>();
        if (!Files.exists(DIR)) return out;
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(DIR, "*.json")) {
            for (Path p : stream) {
                ToolPreset preset = load(p);
                if (preset != null) out.add(preset);
            }
        } catch (IOException e) {
            System.err.println("[Anvil's Search] list presets failed: " + e);
        }
        out.sort((a, b) -> Long.compare(b.savedAt, a.savedAt));
        return out;
    }

    public static boolean delete(String toolId) {
        if (toolId == null) return false;
        try {
            return Files.deleteIfExists(DIR.resolve(sanitize(toolId) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}