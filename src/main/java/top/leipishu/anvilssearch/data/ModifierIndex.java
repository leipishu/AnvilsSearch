package top.leipishu.anvilssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModifierIndex {

    public static final class LevelInfo {
        public final int level;
        public final List<Component> slotLines;
        public final List<ItemStack> materials;
        public final List<Component> materialLines;

        public LevelInfo(int level, List<Component> slotLines,
                         List<ItemStack> materials, List<Component> materialLines) {
            this.level = level;
            this.slotLines = slotLines != null ? slotLines : Collections.emptyList();
            this.materials = materials != null ? materials : Collections.emptyList();
            this.materialLines = materialLines != null ? materialLines : Collections.emptyList();
        }
    }

    public static final class Entry {
        public final Object modifier;
        public final String id;
        public final String registryPath;
        private final Component displayName;
        public final List<LevelInfo> levels;

        public Entry(Object modifier, String id, String registryPath,
                     Component displayName, List<LevelInfo> levels) {
            this.modifier = modifier;
            this.id = id;
            this.registryPath = registryPath;
            this.displayName = displayName;
            this.levels = levels != null ? levels : new ArrayList<>();
        }

        public String getDisplayName() {
            try {
                if (displayName != null) {
                    String s = displayName.getString();
                    if (s != null && !s.isEmpty() && !s.startsWith("modifier.")) return s;
                }
            } catch (Throwable ignored) {}
            return prettify(registryPath);
        }

        public Component getDisplayNameComponent(int level) {
            try {
                Method m = modifier.getClass().getMethod("getDisplayName", int.class);
                m.setAccessible(true);
                Object v = m.invoke(modifier, level);
                if (v instanceof Component c) return c;
            } catch (Throwable ignored) {}
            return displayName != null ? displayName
                    : new TextComponent(prettify(registryPath));
        }

        public List<Component> getDescriptionList(int level) {
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                try {
                    Method m = findMethod(modifier.getClass(), mn, int.class);
                    if (m != null) {
                        m.setAccessible(true);
                        Object v = m.invoke(modifier, level);
                        if (v instanceof List<?> list) {
                            List<Component> out = new ArrayList<>();
                            for (Object o : list) if (o instanceof Component c) out.add(c);
                            return out;
                        }
                    }
                } catch (Throwable ignored) {}
                try {
                    Method m = findMethod(modifier.getClass(), mn);
                    if (m != null) {
                        m.setAccessible(true);
                        Object v = m.invoke(modifier);
                        if (v instanceof List<?> list) {
                            List<Component> out = new ArrayList<>();
                            for (Object o : list) if (o instanceof Component c) out.add(c);
                            return out;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return Collections.emptyList();
        }

        public int getMaxLevel() {
            int max = 1;
            for (LevelInfo li : levels) if (li.level > max) max = li.level;
            return max;
        }
    }

    private static volatile List<Entry> cache;
    private static final Object LOCK = new Object();
    private static final Set<Class<?>> DIAGNOSED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ModifierIndex() {}

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

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            System.out.println("[Anvil's Search] ModifierIndex: no connection");
            return Collections.emptyList();
        }
        RecipeManager rm = mc.getConnection().getRecipeManager();

        Map<String, Map<Integer, LevelInfo>> byModifier = new LinkedHashMap<>();
        Map<String, Object> modifierById = new LinkedHashMap<>();
        Map<String, Component> displayById = new LinkedHashMap<>();

        int scanned = 0, kept = 0;

        for (Recipe<?> recipe : rm.getRecipes()) {
            if (recipe == null) continue;
            if (!isModifierRecipe(recipe)) continue;
            scanned++;

            try {
                Object resultEntry = readFieldAny(recipe, "result", "output", "modifier");
                if (resultEntry == null) continue;

                Object modifier = readModifierFromEntry(resultEntry);
                if (modifier == null) continue;

                String modId = extractModifierId(modifier);
                if (modId == null || modId.isEmpty()) continue;

                int level = readLevelFromEntry(resultEntry);

                List<Component> slotLines = readSlotLines(recipe);
                List<ItemStack> materials = new ArrayList<>();
                List<Component> materialLines = new ArrayList<>();
                readMaterials(recipe, materials, materialLines);

                Class<?> cls = recipe.getClass();
                if (DIAGNOSED.add(cls)) {
                    System.out.println("[Anvil's Search] ModifierRecipe diag class = "
                            + cls.getName());
                    StringBuilder fs = new StringBuilder("[Anvil's Search]   fields:");
                    Class<?> c = cls;
                    while (c != null && c != Object.class) {
                        for (Field f : c.getDeclaredFields()) {
                            fs.append(" ").append(f.getName())
                                    .append(":").append(f.getType().getSimpleName());
                        }
                        c = c.getSuperclass();
                    }
                    System.out.println(fs);
                }

                byModifier.computeIfAbsent(modId, k -> new LinkedHashMap<>())
                        .put(level, new LevelInfo(level, slotLines, materials, materialLines));

                modifierById.putIfAbsent(modId, modifier);
                displayById.putIfAbsent(modId, extractDisplayName(modifier, level));
                kept++;
            } catch (Throwable ignored) {}
        }

        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, LevelInfo>> e : byModifier.entrySet()) {
            String id = e.getKey();
            Object mod = modifierById.get(id);
            if (mod == null) continue;

            List<LevelInfo> levels = new ArrayList<>(e.getValue().values());
            levels.sort(Comparator.comparingInt(li -> li.level));

            String path = id;
            int colon = id.indexOf(':');
            if (colon >= 0) path = id.substring(colon + 1);

            result.add(new Entry(mod, id, path, displayById.get(id), levels));
        }

        result.sort(Comparator.comparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ModifierIndex built: " + result.size()
                + " modifiers, " + kept + " level-recipe(s) from " + scanned
                + " modifier recipes in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    private static boolean isModifierRecipe(Recipe<?> recipe) {
        Class<?> c = recipe.getClass();
        while (c != null && c != Object.class) {
            String n = c.getSimpleName();
            if (n.contains("ModifierRecipe")) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    private static Object readModifierFromEntry(Object entry) {
        if (entry == null) return null;
        for (String mn : new String[]{"getModifier", "getModifierId"}) {
            try {
                Method m = entry.getClass().getMethod(mn);
                Object v = m.invoke(entry);
                if (v != null && !(v instanceof String)) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String extractModifierId(Object modifier) {
        for (String mn : new String[]{"getId", "getIdentifier", "getRegistryName"}) {
            try {
                Method m = modifier.getClass().getMethod(mn);
                Object v = m.invoke(modifier);
                if (v != null) return v.toString();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int readLevelFromEntry(Object entry) {
        try {
            Method m = entry.getClass().getMethod("getLevel");
            Object v = m.invoke(entry);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
        return 1;
    }

    private static Component extractDisplayName(Object modifier, int level) {
        for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
            try {
                Method m = modifier.getClass().getMethod(mn, int.class);
                Object v = m.invoke(modifier, level);
                if (v instanceof Component c) return c;
            } catch (Throwable ignored) {}
        }
        for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
            try {
                Method m = modifier.getClass().getMethod(mn);
                Object v = m.invoke(modifier);
                if (v instanceof Component c) return c;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static List<Component> readSlotLines(Object recipe) {
        Object slots = readFieldAny(recipe, "slots", "slotRequirements", "requiredSlots");
        if (slots == null) return Collections.emptyList();

        List<Component> out = new ArrayList<>();

        try {
            Method getSlot = slots.getClass().getMethod("getSlot");
            Method getCount = slots.getClass().getMethod("getCount");
            Object slotType = getSlot.invoke(slots);
            Object cnt = getCount.invoke(slots);
            if (slotType != null && cnt instanceof Number n && n.intValue() > 0) {
                String slotName = slotTypeDisplayName(slotType);
                out.add(new TextComponent("\u00A77" + slotName
                        + (n.intValue() > 1 ? " \u00D7" + n.intValue() : "")));
                return out;
            }
        } catch (Throwable ignored) {}

        if (slots instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String name = slotTypeDisplayName(e.getKey());
                int cnt = 1;
                if (e.getValue() instanceof Number n) cnt = n.intValue();
                out.add(new TextComponent("\u00A77" + name
                        + (cnt > 1 ? " \u00D7" + cnt : "")));
            }
        } else if (slots instanceof Collection<?> col) {
            for (Object o : col) {
                out.add(new TextComponent("\u00A77" + slotTypeDisplayName(o)));
            }
        } else {
            out.add(new TextComponent("\u00A77" + slotTypeDisplayName(slots)));
        }
        return out;
    }

    private static String slotTypeDisplayName(Object slot) {
        if (slot == null) return "?";

        String id = null;
        for (String mn : new String[]{"getName", "getId", "getRegistryName"}) {
            try {
                Method m = slot.getClass().getMethod(mn);
                Object v = m.invoke(slot);
                if (v != null) { id = v.toString(); break; }
            } catch (Throwable ignored) {}
        }
        if (id == null) id = String.valueOf(slot);

        String lower = id.toLowerCase();
        if (lower.contains("upgrade") || lower.contains("升级")) return "升级";
        if (lower.contains("abilit") || lower.contains("能力"))  return "能力";
        if (lower.contains("defense") || lower.contains("防御")) return "防御";
        if (lower.contains("soul") || lower.contains("灵魂"))    return "灵魂";

        int open = id.indexOf('{');
        int close = id.indexOf('}');
        if (open >= 0 && close > open) id = id.substring(open + 1, close);

        for (String mn : new String[]{"getDisplayName"}) {
            try {
                Method m = slot.getClass().getMethod(mn);
                Object v = m.invoke(slot);
                if (v instanceof Component c) return c.getString();
            } catch (Throwable ignored) {}
        }
        return id;
    }

    private static void readMaterials(Object recipe,
                                      List<ItemStack> outItems,
                                      List<Component> outLines) {
        Object ing = readFieldAny(recipe, "ingredients", "inputs", "input");
        if (ing == null) return;

        List<Object> ingredients = new ArrayList<>();
        if (ing instanceof Object[] arr) {
            for (Object o : arr) ingredients.add(o);
        } else if (ing instanceof Collection<?> col) {
            ingredients.addAll(col);
        } else {
            ingredients.add(ing);
        }

        for (Object sized : ingredients) {
            if (sized == null) continue;
            ItemStack item = extractItemStack(sized);
            int count = extractCount(sized);

            if (item != null && !item.isEmpty()) {
                outItems.add(item);
                String name = item.getHoverName().getString();
                outLines.add(new TextComponent("\u00A77" + name
                        + (count > 1 ? " \u00D7" + count : "")));
            } else {
                outLines.add(new TextComponent("\u00A78"
                        + sized.getClass().getSimpleName()));
            }
        }
    }

    private static ItemStack extractItemStack(Object sized) {
        if (sized == null) return ItemStack.EMPTY;
        if (sized instanceof ItemStack s) return s.copy();

        for (String mn : new String[]{"getItems", "getMatchingStacks",
                "getStacks", "getMatchingItems", "getIngredientItems"}) {
            try {
                Method m = sized.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(sized);
                ItemStack got = firstItemFrom(v);
                if (!got.isEmpty()) return got;
            } catch (Throwable ignored) {}
        }

        for (String mn : new String[]{"getIngredient", "getInner"}) {
            try {
                Method m = sized.getClass().getMethod(mn);
                m.setAccessible(true);
                Object inner = m.invoke(sized);
                if (inner != null) {
                    for (String mn2 : new String[]{"getItems", "getMatchingStacks", "getStacks"}) {
                        try {
                            Method m2 = inner.getClass().getMethod(mn2);
                            m2.setAccessible(true);
                            Object v = m2.invoke(inner);
                            ItemStack got = firstItemFrom(v);
                            if (!got.isEmpty()) return got;
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        Class<?> c = sized.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getName().toLowerCase().contains("ingredient")) continue;
                f.setAccessible(true);
                try {
                    Object inner = f.get(sized);
                    if (inner == null) continue;
                    for (String mn2 : new String[]{"getItems", "getMatchingStacks", "getStacks"}) {
                        try {
                            Method m2 = inner.getClass().getMethod(mn2);
                            m2.setAccessible(true);
                            Object v = m2.invoke(inner);
                            ItemStack got = firstItemFrom(v);
                            if (!got.isEmpty()) return got;
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack firstItemFrom(Object v) {
        if (v == null) return ItemStack.EMPTY;
        if (v instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        if (v instanceof ItemStack[] arr) {
            for (ItemStack s : arr) {
                if (s != null && !s.isEmpty()) return s.copy();
            }
        }
        if (v instanceof Collection<?> col) {
            for (Object o : col) {
                if (o instanceof ItemStack s && !s.isEmpty()) return s.copy();
            }
        }
        if (v instanceof java.util.stream.Stream<?> st) {
            Object first = st.findFirst().orElse(null);
            if (first instanceof ItemStack s && !s.isEmpty()) return s.copy();
        }
        if (v instanceof Object[] arr2) {
            for (Object o : arr2) {
                if (o instanceof ItemStack s && !s.isEmpty()) return s.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static int extractCount(Object sized) {
        for (String mn : new String[]{"getCount", "getAmount", "getNeeded"}) {
            try {
                Method m = sized.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(sized);
                if (v instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        return 1;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredMethod(name, params); }
            catch (Throwable ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object readFieldAny(Object obj, String... names) {
        if (obj == null) return null;
        for (String name : names) {
            Class<?> c = obj.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
                c = c.getSuperclass();
            }
        }
        return null;
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