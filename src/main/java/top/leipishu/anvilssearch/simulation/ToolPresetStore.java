package top.leipishu.anvilssearch.simulation;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ToolPresetStore {

    private static final Path DIR =
            FMLPaths.CONFIGDIR.get().resolve("anvilssearch-presets");

    private ToolPresetStore() {}

    public static Path dir() { return DIR; }

    /**
     * 保存预设。
     * 文件名格式：{@code <sanitizedToolId>_<yyyyMMdd-HHmmss>.json}
     * 同一工具多次保存会生成多个文件，不会互相覆盖。
     */
    public static Path save(ToolPreset preset) {
        if (preset == null || preset.toolId == null) return null;
        try {
            Files.createDirectories(DIR);

            String timestamp;
            try {
                timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                        .format(new Date(preset.savedAt));
            } catch (Throwable t) {
                timestamp = String.valueOf(preset.savedAt);
            }

            Path file = DIR.resolve(
                    sanitize(preset.toolId) + "_" + timestamp + ".json");

            // 极端情况（同一秒内两次保存）：追加毫秒避免覆盖
            if (Files.exists(file)) {
                file = DIR.resolve(
                        sanitize(preset.toolId) + "_" + preset.savedAt + ".json");
            }

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

    /**
     * 删除指定预设。
     * 由于文件名不再等于 toolId，需要按 savedAt + toolId 匹配定位。
     */
    public static boolean delete(ToolPreset preset) {
        if (preset == null || preset.toolId == null) return false;
        if (!Files.exists(DIR)) return false;

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(DIR, "*.json")) {
            for (Path p : stream) {
                ToolPreset loaded = load(p);
                if (loaded != null
                        && loaded.savedAt == preset.savedAt
                        && preset.toolId.equals(loaded.toolId)) {
                    return Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {}

        // 兜底：按老格式删（兼容旧文件）
        try {
            return Files.deleteIfExists(
                    DIR.resolve(sanitize(preset.toolId) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    /** 兼容旧调用：只按 toolId 删除（会删掉该工具的所有预设）。 */
    public static boolean delete(String toolId) {
        if (toolId == null) return false;
        boolean any = false;
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