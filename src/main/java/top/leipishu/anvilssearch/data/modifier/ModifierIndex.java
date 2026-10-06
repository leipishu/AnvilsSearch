package top.leipishu.anvilssearch.data.modifier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.leipishu.anvilssearch.data.modifier.ModifierReflect.*;

/**
 * 强化索引对外 API。
 *
 * 配方种类：
 *   - SIMPLE      —— 单级固定配方
 *   - INCREMENTAL —— 叠加配方（锋利等），有明确等级上限，用 ⚡ + "每级 N 材料"
 *   - UNLIMITED   —— 无上限配方（延展、储液、泼洒等），用 ∞ + "无上限"
 *   - MULTILEVEL  —— 多级配方（阅历等），展开为 I~N
 *   - SWAPPABLE   —— 变体配方
 */
public final class ModifierIndex {

    public enum RecipeKind { SIMPLE, INCREMENTAL, MULTILEVEL, SWAPPABLE, UNLIMITED }

    // ============================================================
    // ===== 公开数据结构 =========================================
    // ============================================================

    public static final class SlotRequirement {
        public final String typeId;
        public final Component displayName;
        public final int count;

        public SlotRequirement(String typeId, Component displayName, int count) {
            this.typeId = (typeId != null && !typeId.isEmpty()) ? typeId : "unknown";
            this.displayName = displayName != null ? displayName : Component.literal("?");
            this.count = Math.max(0, count);
        }
    }

    public static final class LevelInfo {
        public final int level;
        public final Component displayName;
        public final List<SlotRequirement> slots;
        public final List<ItemStack> materials;
        public final List<Component> materialLines;
        public final List<ItemStack>[] slotMaterials;
        public final Object toolFilter;
        public final Object toolRequirement;
        public final Object recipe;
        public final RecipeKind kind;
        public final int amountPerInput;
        public final int neededPerLevel;
        public final String requirementsError;
        public final Component variant;

        @SuppressWarnings("unchecked")
        public LevelInfo(int level, Component displayName,
                         List<SlotRequirement> slots,
                         List<ItemStack> materials, List<Component> materialLines,
                         List<ItemStack>[] slotMaterials,
                         Object toolFilter, Object toolRequirement, Object recipe,
                         RecipeKind kind,
                         int amountPerInput, int neededPerLevel,
                         String requirementsError, Component variant) {
            this.level = level;
            this.displayName = displayName != null ? displayName : Component.literal("?");
            this.slots = slots != null ? slots : Collections.emptyList();
            this.materials = materials != null ? materials : Collections.emptyList();
            this.materialLines = materialLines != null ? materialLines : Collections.emptyList();
            this.slotMaterials = slotMaterials;
            this.toolFilter = toolFilter;
            this.toolRequirement = toolRequirement;
            this.recipe = recipe;
            this.kind = kind != null ? kind : RecipeKind.SIMPLE;
            this.amountPerInput = amountPerInput;
            this.neededPerLevel = neededPerLevel;
            this.requirementsError = requirementsError;
            this.variant = variant;
        }
    }

    public static final class Entry {
        public final Object modifier;
        public final Object recipe;
        public final String id;
        public final String registryPath;
        public final int color;
        public final int maxLevel;
        public final List<LevelInfo> levels;

        public Entry(Object modifier, Object recipe, String id, String registryPath,
                     int color, int maxLevel, List<LevelInfo> levels) {
            this.modifier = modifier;
            this.recipe = recipe;
            this.id = id;
            this.registryPath = registryPath;
            this.color = color;
            this.maxLevel = maxLevel;
            this.levels = levels != null ? levels : new ArrayList<>();
        }

        public String getDisplayName() {
            try {
                Component c = getDisplayNameComponent(1);
                String s = c.getString();
                if (s != null && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
            return prettify(registryPath);
        }

        public Component getDisplayNameComponent(int level) {
            Object v = call(modifier, "getDisplayName", int.class, level);
            if (v instanceof Component c) {
                String s = c.getString();
                if (s != null && !s.isEmpty()) return c;
            }
            try {
                Object levelDisplay = field(modifier,
                        "levelDisplay", "level_display", "display", "LEVEL_DISPLAY");
                if (levelDisplay != null) {
                    for (Method m : levelDisplay.getClass().getMethods()) {
                        if (!"nameForLevel".equals(m.getName())) continue;
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length != 2 || pt[1] != int.class) continue;
                        m.setAccessible(true);
                        Object result = m.invoke(levelDisplay, modifier, level);
                        if (result instanceof Component c2) {
                            String s = c2.getString();
                            if (s != null && !s.isEmpty()) return c2;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return Component.literal(prettify(registryPath) + " " + level);
        }

        public List<Component> getDescriptionList(int level) {
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                Object v = call(modifier, mn, int.class, level);
                if (v == null) v = call(modifier, mn);
                if (v instanceof List<?> list) {
                    List<Component> out = new ArrayList<>();
                    for (Object o : list) if (o instanceof Component c) out.add(c);
                    if (!out.isEmpty()) return out;
                }
            }
            return Collections.emptyList();
        }
    }

    // ============================================================
    // ===== 缓存 =================================================
    // ============================================================

    private static volatile List<Entry> cache;
    private static volatile List<Entry> cacheGrouped;
    private static final Object LOCK = new Object();

    private ModifierIndex() {}

    public static void invalidate() {
        synchronized (LOCK) {
            cache = null;
            cacheGrouped = null;
        }
    }

    public static List<Entry> get() {
        List<Entry> local = cache;
        if (local != null) return local;
        synchronized (LOCK) {
            if (cache == null) cache = ModifierIndexBuilder.build();
            return cache;
        }
    }

    public static List<Entry> getGrouped() {
        List<Entry> local = cacheGrouped;
        if (local != null) return local;
        synchronized (LOCK) {
            if (cacheGrouped != null) return cacheGrouped;
            cacheGrouped = group(get());
            return cacheGrouped;
        }
    }

    // ============================================================
    // ===== 分组 =================================================
    // ============================================================

    private static List<Entry> group(List<Entry> raw) {
        if (raw.isEmpty()) return raw;

        Map<String, List<Entry>> byKey = new LinkedHashMap<>();
        for (Entry e : raw) {
            String key = e.registryPath != null ? e.registryPath : e.id;
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> ge : byKey.entrySet()) {
            String path = ge.getKey();
            List<Entry> group = ge.getValue();
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }
            Entry first = group.get(0);

            List<LevelInfo> merged = new ArrayList<>();
            for (Entry e : group) merged.addAll(e.levels);
            merged.sort(Comparator.comparingInt(l -> l.level));

            List<LevelInfo> unique = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (LevelInfo lv : merged) {
                String sig = levelSignature(lv);
                if (seen.add(sig)) unique.add(lv);
            }
            if (unique.isEmpty()) unique.addAll(merged);

            if (unique.size() < merged.size()) {
                System.out.println("[Anvil's Search] group " + path + ": "
                        + merged.size() + " -> " + unique.size() + " (deduped)");
            }

            result.add(new Entry(first.modifier, first.recipe, path,
                    first.registryPath, first.color, unique.size(), unique));
        }

        result.sort(Comparator.comparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static String levelSignature(LevelInfo lv) {
        StringBuilder sb = new StringBuilder();
        sb.append(lv.level).append('|');
        try {
            sb.append(lv.displayName.getString());
        } catch (Throwable ignored) {}
        sb.append('|');
        for (SlotRequirement sr : lv.slots) {
            sb.append(sr.typeId).append(',').append(sr.count).append(';');
        }
        sb.append('|');
        for (ItemStack s : lv.materials) {
            if (s == null) continue;
            sb.append(stackKey(s)).append('*').append(s.getCount()).append(';');
        }
        return sb.toString();
    }

    // ============================================================
    // ===== 内部工具 =============================================
    // ============================================================

    private static String prettify(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] w = path.split("[_:/]");
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