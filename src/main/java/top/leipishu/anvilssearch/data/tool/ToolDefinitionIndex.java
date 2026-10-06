package top.leipishu.anvilssearch.data.tool;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.part.IToolPart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具/武器定义索引。
 *
 * <p>扫描 {@link ForgeRegistries#ITEMS}，从 {@link IModifiable} 读 {@link ToolDefinition}，
 * 按 id 去重。使用 1.19.2 的正确 API {@code ToolPartsHook.parts(def)} 获取部件列表。
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
                    if (s != null && !s.isEmpty()
                            && !s.equals(item.getDescriptionId())) {
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

        int totalModifiable   = 0;
        int skippedEmptyDef   = 0;
        int skippedNotLoaded  = 0;
        int skippedDuplicate  = 0;
        int skippedNoParts    = 0;
        int skippedNoPartItem = 0;

        try {
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == null) continue;
                if (!(item instanceof IModifiable modifiable)) continue;
                totalModifiable++;

                ToolDefinition def;
                try {
                    def = modifiable.getToolDefinition();
                } catch (Throwable ignored) {
                    skippedEmptyDef++;
                    continue;
                }

                if (def == null || def == ToolDefinition.EMPTY) {
                    skippedEmptyDef++;
                    continue;
                }

                ResourceLocation defId;
                try {
                    defId = def.getId();
                } catch (Throwable ignored) {
                    skippedEmptyDef++;
                    continue;
                }
                if (defId == null) {
                    skippedEmptyDef++;
                    continue;
                }

                if (dedupe.containsKey(defId)) {
                    skippedDuplicate++;
                    continue;
                }

                if (!def.isDataLoaded()) {
                    skippedNotLoaded++;
                    continue;
                }

                // ★ 1.19.2：ToolPartsHook.parts(definition)
                List<IToolPart> parts;
                try {
                    parts = ToolPartsHook.parts(def);
                } catch (Throwable t) {
                    skippedNoParts++;
                    continue;
                }

                if (parts == null || parts.isEmpty()) {
                    skippedNoParts++;
                    continue;
                }

                // ★ 至少有一个部件能解析出 item
                boolean hasAnyPartItem = false;
                for (IToolPart pr : parts) {
                    if (pr == null) continue;
                    if (extractPartItemSafe(pr) != null) {
                        hasAnyPartItem = true;
                        break;
                    }
                }
                if (!hasAnyPartItem) {
                    skippedNoPartItem++;
                    System.out.println("[ToolIndex] SKIP (no resolvable part item): "
                            + defId + " parts=" + parts.size());
                    continue;
                }

                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                String path = itemId != null ? itemId.getPath() : defId.getPath();
                String category = categorize(defId);

                dedupe.put(defId, new Entry(def, defId, item, path, category));
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] ToolDefinitionIndex build failed: "
                    + t);
            t.printStackTrace();
        }

        System.out.println("[ToolIndex] scan summary:"
                + " IModifiable=" + totalModifiable
                + " emptyDef=" + skippedEmptyDef
                + " duplicate=" + skippedDuplicate
                + " notLoaded=" + skippedNotLoaded
                + " noParts=" + skippedNoParts
                + " noPartItem=" + skippedNoPartItem
                + " accepted=" + dedupe.size());

        List<Entry> result = new ArrayList<>(dedupe.values());
        result.sort(Comparator
                .comparing((Entry e) -> e.category)
                .thenComparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ToolDefinitionIndex built: "
                + result.size() + " tools in "
                + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    /**
     * ★ 1.19.2：IToolPart 继承自 IMaterialItem → ItemLike，
     * 直接调用 asItem() 即可获取对应的 Item。
     */
    private static Item extractPartItemSafe(IToolPart pr) {
        if (pr == null) return null;
        try {
            return pr.asItem();
        } catch (Throwable ignored) {
            return null;
        }
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