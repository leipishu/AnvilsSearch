package top.leipishu.anvilssearch.data.tool;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.definition.PartRequirement;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import java.util.*;

/**
 * 工具/武器定义索引。
 * 扫描 ForgeRegistries.ITEMS，从 IModifiable 读 ToolDefinition，按 id 去重。
 *
 * ★ 不再强制要求所有部件都是 IMaterialItem —— 特殊工具（如黏液头盔）
 *   只要有能拿到 item 的部件就保留，材料列表由 PartMaterialIndex 处理。
 */
public final class ToolDefinitionIndex {

    public static final class Entry {
        public final ToolDefinition definition;
        public final ResourceLocation id;
        public final Item item;
        public final String registryPath;
        public final String category;

        public Entry(ToolDefinition definition, ResourceLocation id, Item item,
                     String registryPath, String category) {
            this.definition = definition;
            this.id = id;
            this.item = item;
            this.registryPath = registryPath;
            this.category = category;
        }

        public String getDisplayName() {
            try {
                if (item != null) {
                    String s = item.getDescription().getString();
                    if (s != null && !s.isEmpty() && !s.equals(item.getDescriptionId())) {
                        return s;
                    }
                }
            } catch (Throwable ignored) {}
            return prettify(registryPath != null ? registryPath : id.getPath());
        }
    }

    private static volatile List<Entry> cache;
    private static final Object LOCK = new Object();

    private ToolDefinitionIndex() {}

    public static void invalidate() {
        synchronized (LOCK) { cache = null; }
    }

    public static List<Entry> get() {
        List<Entry> local = cache;
        if (local != null) return local;
        synchronized (LOCK) {
            if (cache != null) return cache;
            cache = build();
            return cache;
        }
    }

    private static List<Entry> build() {
        long t0 = System.currentTimeMillis();
        Map<ResourceLocation, Entry> dedupe = new LinkedHashMap<>();

        try {
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == null) continue;
                if (!(item instanceof IModifiable modifiable)) continue;

                ToolDefinition def;
                try { def = modifiable.getToolDefinition(); }
                catch (Throwable ignored) { continue; }

                if (def == null || def == ToolDefinition.EMPTY) continue;

                ResourceLocation defId;
                try { defId = def.getId(); }
                catch (Throwable ignored) { continue; }
                if (defId == null) continue;

                if (dedupe.containsKey(defId)) continue;

                // ★ 过滤：data 未加载 / 无部件 / 所有部件都拿不到 item
                try {
                    if (!def.isDataLoaded()) continue;
                    List<PartRequirement> parts = def.getData().getParts();
                    if (parts == null || parts.isEmpty()) continue;

                    boolean hasAnyPart = false;
                    for (PartRequirement pr : parts) {
                        if (pr == null) continue;
                        if (extractPartItemSafe(pr) != null) { hasAnyPart = true; break; }
                    }
                    if (!hasAnyPart) continue;
                } catch (Throwable ignored) {
                    continue;
                }

                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                String path = itemId != null ? itemId.getPath() : defId.getPath();
                String category = categorize(defId);

                dedupe.put(defId, new Entry(def, defId, item, path, category));
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] ToolDefinitionIndex build failed: " + t);
        }

        List<Entry> result = new ArrayList<>(dedupe.values());
        result.sort(Comparator
                .comparing((Entry e) -> e.category)
                .thenComparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ToolDefinitionIndex built: " + result.size()
                + " tools in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    private static Item extractPartItemSafe(PartRequirement pr) {
        if (pr == null) return null;
        for (String mn : new String[]{"getPart", "getMaterialItem", "getItem"}) {
            try {
                java.lang.reflect.Method m = pr.getClass().getMethod(mn);
                Object v = m.invoke(pr);
                if (v instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi)
                    return mi.asItem();
                if (v instanceof Item item) return item;
            } catch (Throwable ignored) {}
        }
        for (String fn : new String[]{"part", "item", "materialItem"}) {
            try {
                java.lang.reflect.Field f = pr.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(pr);
                if (v instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi)
                    return mi.asItem();
                if (v instanceof Item item) return item;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String categorize(ResourceLocation defId) {
        String id = defId.getPath().toLowerCase();
        if (id.contains("bow") || id.contains("crossbow")
                || id.contains("slingshot") || id.contains("ballista")) {
            return "ranged";
        }
        return "melee";
    }

    private static String prettify(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] w = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : w) {
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(s.charAt(0)));
            if (s.length() > 1) sb.append(s.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : path;
    }
}