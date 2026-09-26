package top.leipishu.anvilssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModifierIndex {

    public enum RecipeKind { SIMPLE, INCREMENTAL, MULTILEVEL }

    public static final class LevelInfo {
        public final int level;
        public final Component displayName;
        public final List<Component> slotLines;
        public final List<ItemStack> materials;
        public final List<Component> materialLines;
        public final Object toolFilter;
        public final RecipeKind kind;
        public final int amountPerInput;
        public final int neededPerLevel;

        public LevelInfo(int level, Component displayName,
                         List<Component> slotLines,
                         List<ItemStack> materials, List<Component> materialLines,
                         Object toolFilter, RecipeKind kind,
                         int amountPerInput, int neededPerLevel) {
            this.level = level;
            this.displayName = displayName != null ? displayName : new TextComponent("?");
            this.slotLines = slotLines != null ? slotLines : Collections.emptyList();
            this.materials = materials != null ? materials : Collections.emptyList();
            this.materialLines = materialLines != null ? materialLines : Collections.emptyList();
            this.toolFilter = toolFilter;
            this.kind = kind != null ? kind : RecipeKind.SIMPLE;
            this.amountPerInput = amountPerInput;
            this.neededPerLevel = neededPerLevel;
        }
    }

    private static final class MaterialResult {
        final List<ItemStack> icons;
        final List<Component> lines;
        MaterialResult(List<ItemStack> icons, List<Component> lines) {
            this.icons = icons != null ? icons : Collections.emptyList();
            this.lines = lines != null ? lines : Collections.emptyList();
        }
    }

    public static final class Entry {
        public final Object modifier;
        public final String id;
        public final String registryPath;
        public final int color;
        public final int maxLevel;
        public final List<LevelInfo> levels;

        public Entry(Object modifier, String id, String registryPath,
                     int color, int maxLevel, List<LevelInfo> levels) {
            this.modifier = modifier;
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
            for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
                try {
                    Method m = modifier.getClass().getMethod(mn, int.class);
                    m.setAccessible(true);
                    Object v = m.invoke(modifier, level);
                    if (v instanceof Component c) return c;
                } catch (Throwable ignored) {}
            }
            return new TextComponent(prettify(registryPath) + " " + level);
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
                            if (!out.isEmpty()) return out;
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
                            if (!out.isEmpty()) return out;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return Collections.emptyList();
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

        Map<String, List<Object>> recipesByMod = new LinkedHashMap<>();
        Map<String, Object> modifierById = new LinkedHashMap<>();

        int scanned = 0;

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

                Class<?> cls = recipe.getClass();
                if (DIAGNOSED.add(cls)) {
                    System.out.println("[Anvil's Search] ModifierRecipe diag class = " + cls.getName());
                }

                modifierById.putIfAbsent(modId, modifier);
                recipesByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(recipe);
            } catch (Throwable ignored) {}
        }

        List<Entry> result = new ArrayList<>();
        int multiCount = 0;

        for (Map.Entry<String, Object> e : modifierById.entrySet()) {
            String id = e.getKey();
            Object mod = e.getValue();
            List<Object> recipes = recipesByMod.get(id);
            if (recipes == null || recipes.isEmpty()) continue;

            try {
                int color = extractColor(mod);

                Object incrementalRecipe = null;
                Object multilevelRecipe = null;
                List<Object> simpleRecipes = new ArrayList<>();

                for (Object r : recipes) {
                    RecipeKind k = detectKind(r);
                    if (k == RecipeKind.INCREMENTAL && incrementalRecipe == null) {
                        incrementalRecipe = r;
                    } else if (k == RecipeKind.MULTILEVEL && multilevelRecipe == null) {
                        multilevelRecipe = r;
                    } else {
                        simpleRecipes.add(r);
                    }
                }

                List<LevelInfo> levels = new ArrayList<>();

                // 分支 2：MultilevelModifierRecipe
                if (multilevelRecipe != null) {
                    List<?> subRecipes = tryGetSubRecipes(multilevelRecipe);
                    if (subRecipes != null && !subRecipes.isEmpty()) {
                        int idx = 1;
                        for (Object sub : subRecipes) {
                            List<Component> lvSlots = readSlotLines(sub);
                            MaterialResult mr = readMaterialsFromDisplay(sub);
                            Object tf = readFieldAny(sub,
                                    "toolRequirement", "tools", "toolFilter", "toolIngredient");
                            Component dn = extractDisplayName(mod, idx);
                            levels.add(new LevelInfo(idx, dn, lvSlots, mr.icons, mr.lines,
                                    tf, RecipeKind.MULTILEVEL, 0, 0));
                            idx++;
                        }
                        if (levels.size() > 1) multiCount++;
                    }
                }

                // 分支 1：IncrementalModifierRecipe
                if (levels.isEmpty() && incrementalRecipe != null) {
                    int maxLv = readIntField(incrementalRecipe, 1, "maxLevel", "max_level");
                    int amountPerInput = readIntField(incrementalRecipe, 1, "amountPerInput", "amount_per_input");
                    int neededPerLevel = readIntField(incrementalRecipe, 1, "neededPerLevel", "needed_per_level");

                    List<Component> baseSlots = readSlotLines(incrementalRecipe);
                    MaterialResult baseMr = readMaterialsFromDisplay(incrementalRecipe);
                    Object tf = readFieldAny(incrementalRecipe,
                            "toolRequirement", "tools", "toolFilter", "toolIngredient");

                    for (int lv = 1; lv <= maxLv; lv++) {
                        int countPerLevel = amountPerInput;
                        if (neededPerLevel > 0 && amountPerInput > 0) {
                            countPerLevel = (int) Math.ceil(
                                    (double) neededPerLevel / amountPerInput);
                        }

                        // ★ 每个基础材料一个格子，count = countPerLevel
                        List<ItemStack> mats = new ArrayList<>();
                        List<Component> matLines = new ArrayList<>();
                        for (ItemStack s : baseMr.icons) {
                            ItemStack copy = s.copy();
                            copy.setCount(countPerLevel);
                            mats.add(copy);
                            if (matLines.isEmpty()) {
                                matLines.add(new TextComponent("\u00A77"
                                        + s.getHoverName().getString() + " \u00D7" + countPerLevel));
                            }
                        }

                        Component dn = extractDisplayName(mod, lv);
                        levels.add(new LevelInfo(lv, dn, baseSlots, mats, matLines,
                                tf, RecipeKind.INCREMENTAL, amountPerInput, neededPerLevel));
                    }
                    if (levels.size() > 1) multiCount++;
                }

                // 分支 4：多个独立 recipe
                if (levels.isEmpty() && !simpleRecipes.isEmpty()) {
                    simpleRecipes.sort(Comparator.comparing(ModifierIndex::getRecipeId));

                    int idx = 1;
                    for (Object r : simpleRecipes) {
                        List<Component> lvSlots = readSlotLines(r);
                        MaterialResult mr = readMaterialsFromDisplay(r);
                        Object toolFilter = readFieldAny(r,
                                "toolRequirement", "tools", "toolFilter", "toolIngredient");

                        Component dn = extractDisplayName(mod, idx);
                        levels.add(new LevelInfo(idx, dn, lvSlots, mr.icons, mr.lines,
                                toolFilter, RecipeKind.SIMPLE, 0, 0));
                        idx++;
                    }
                }

                if (levels.isEmpty()) continue;

                String path = id;
                int colon = id.indexOf(':');
                if (colon >= 0) path = id.substring(colon + 1);

                result.add(new Entry(mod, id, path, color, levels.size(), levels));
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] failed " + id + ": " + t);
            }
        }

        result.sort(Comparator.comparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ModifierIndex built: " + result.size()
                + " modifiers (" + multiCount + " multi-level) from " + scanned
                + " modifier recipes in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    // ============================================================
    // ===== 材料 =================================================
    // ============================================================

    private static MaterialResult readMaterialsFromDisplay(Object recipe) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        if (recipe == null) return new MaterialResult(icons, lines);

        // 特殊 1：IncrementalModifierRecipe 用单个 input:Ingredient + amountPerInput
        Object inputField = readFieldAny(recipe, "input");
        if (inputField instanceof Ingredient ing) {
            int amount = readIntField(recipe, 1, "amountPerInput", "amount_per_input");
            if (amount < 1) amount = 1;
            ItemStack extracted = firstFromIngredient(ing);
            if (!extracted.isEmpty()) {
                // ★ 一个格子，count = amount
                ItemStack copy = extracted.copy();
                copy.setCount(amount);
                icons.add(copy);
                lines.add(new TextComponent("\u00A77"
                        + extracted.getHoverName().getString() + " \u00D7" + amount));
                return new MaterialResult(icons, lines);
            }
        }

        // 通用路径
        Object inputsObj = null;
        for (String mn : new String[]{"getInputs", "getDisplayInputs"}) {
            Method m = findMethod(recipe.getClass(), mn);
            if (m == null || m.getParameterCount() != 0) continue;
            try {
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof List<?> l && !l.isEmpty()) { inputsObj = v; break; }
            } catch (Throwable ignored) {}
        }
        if (inputsObj == null) {
            Object v = readFieldAny(recipe, "inputs", "ingredients", "input");
            if (v != null) inputsObj = v;
        }
        if (inputsObj == null) return new MaterialResult(icons, lines);

        List<Object> list = new ArrayList<>();
        if (inputsObj instanceof Object[] arr) {
            for (Object o : arr) list.add(o);
        } else if (inputsObj instanceof Collection<?> col) {
            list.addAll(col);
        } else {
            list.add(inputsObj);
        }

        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, ItemStack> repMap = new LinkedHashMap<>();

        for (Object sized : list) {
            if (sized == null) continue;
            ItemStack stack = extractStackFromSized(sized);
            int count = extractCount(sized);
            if (stack.isEmpty()) continue;

            // ★ 每个 SizedIngredient 一个格子（不跨格子合并），count 保留
            ItemStack iconCopy = stack.copy();
            iconCopy.setCount(count);
            icons.add(iconCopy);

            // 文本仍按物品合并 ×N
            String key;
            try {
                String regName = stack.getItem().getRegistryName() != null
                        ? stack.getItem().getRegistryName().toString()
                        : stack.getItem().toString();
                String nbt = stack.getTag() != null ? stack.getTag().toString() : "";
                key = regName + "|" + nbt;
            } catch (Throwable t) {
                key = stack.toString();
            }
            countMap.merge(key, count, Integer::sum);
            repMap.putIfAbsent(key, stack);
        }

        for (Map.Entry<String, Integer> e : countMap.entrySet()) {
            ItemStack rep = repMap.get(e.getKey());
            int count = e.getValue();
            lines.add(new TextComponent("\u00A77"
                    + rep.getHoverName().getString() + " \u00D7" + count));
        }

        return new MaterialResult(icons, lines);
    }

    private static ItemStack firstFromIngredient(Ingredient ing) {
        if (ing == null) return ItemStack.EMPTY;
        try {
            ItemStack[] items = ing.getItems();
            if (items.length > 0 && !items[0].isEmpty()) return items[0].copy();
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private static ItemStack extractStackFromSized(Object sized) {
        if (sized == null) return ItemStack.EMPTY;
        if (sized instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        if (sized instanceof Ingredient ing) return firstFromIngredient(ing);

        for (String mn : new String[]{"getIngredient", "getInner", "getItem", "getStack"}) {
            Method m = findMethod(sized.getClass(), mn);
            if (m == null || m.getParameterCount() != 0) continue;
            try {
                m.setAccessible(true);
                Object v = m.invoke(sized);
                ItemStack got = extractFromAny(v, 2);
                if (!got.isEmpty()) return got;
            } catch (Throwable ignored) {}
        }

        Class<?> c = sized.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                String fn = f.getName().toLowerCase();
                if (!fn.contains("ingredient") && !fn.contains("input")
                        && !fn.contains("item") && !fn.contains("stack")) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(sized);
                    ItemStack got = extractFromAny(v, 2);
                    if (!got.isEmpty()) return got;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractFromAny(Object v, int depth) {
        if (v == null || depth < 0) return ItemStack.EMPTY;
        if (v instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        if (v instanceof Ingredient ing) return firstFromIngredient(ing);
        if (v instanceof net.minecraft.world.item.Item item) return new ItemStack(item);

        if (v instanceof ItemStack[] arr) {
            for (ItemStack s : arr) if (s != null && !s.isEmpty()) return s.copy();
        }
        if (v instanceof Object[] arr2) {
            for (Object o : arr2) {
                ItemStack s = extractFromAny(o, depth - 1);
                if (!s.isEmpty()) return s;
            }
        }
        if (v instanceof Collection<?> col) {
            for (Object o : col) {
                ItemStack s = extractFromAny(o, depth - 1);
                if (!s.isEmpty()) return s;
            }
        }
        if (v instanceof java.util.stream.Stream<?> st) {
            Object first = st.findFirst().orElse(null);
            return extractFromAny(first, depth - 1);
        }
        if (depth > 0) {
            for (String mn : new String[]{"getItems", "getMatchingStacks", "getStacks", "getItem"}) {
                Method m = findMethod(v.getClass(), mn);
                if (m == null || m.getParameterCount() != 0) continue;
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(v);
                    ItemStack s = extractFromAny(r, depth - 1);
                    if (!s.isEmpty()) return s;
                } catch (Throwable ignored) {}
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

    // ============================================================
    // ===== 槽位 =================================================
    // ============================================================

    private static List<Component> readSlotLines(Object recipe) {
        Object slots = readFieldAny(recipe, "slots", "slotRequirements", "requiredSlots");
        if (slots == null) return Collections.emptyList();

        List<Component> out = new ArrayList<>();

        String slotName = null;
        int count = 0;

        for (String mn : new String[]{"getSlot", "slot", "getSlotType", "slotType", "getType"}) {
            try {
                Method m = findMethod(slots.getClass(), mn);
                if (m == null || m.getParameterCount() != 0) continue;
                m.setAccessible(true);
                Object v = m.invoke(slots);
                if (v != null) { slotName = slotTypeDisplayName(v); break; }
            } catch (Throwable ignored) {}
        }
        if (slotName == null) {
            Object fv = readFieldAny(slots, "slot", "slotType", "type");
            if (fv != null) slotName = slotTypeDisplayName(fv);
        }

        for (String mn : new String[]{"getCount", "count", "getAmount", "getSize"}) {
            try {
                Method m = findMethod(slots.getClass(), mn);
                if (m == null || m.getParameterCount() != 0) continue;
                m.setAccessible(true);
                Object v = m.invoke(slots);
                if (v instanceof Number n) { count = n.intValue(); break; }
            } catch (Throwable ignored) {}
        }
        if (count == 0) {
            Object fv = readFieldAny(slots, "count", "amount", "size");
            if (fv instanceof Number n) count = n.intValue();
        }

        if (slotName != null && count > 0) {
            out.add(new TextComponent("\u00A77" + slotName
                    + (count > 1 ? " \u00D7" + count : "")));
        }
        return out;
    }

    private static String slotTypeDisplayName(Object slot) {
        if (slot == null) return "?";

        String id = null;
        for (String mn : new String[]{"getName", "getId", "getRegistryName",
                "name", "id", "getLocation"}) {
            try {
                Method m = findMethod(slot.getClass(), mn);
                if (m == null || m.getParameterCount() != 0) continue;
                m.setAccessible(true);
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
        int ob = id.indexOf('[');
        int cb = id.indexOf(']');
        if (ob >= 0 && cb > ob) id = id.substring(ob + 1, cb);

        if (id.toLowerCase().startsWith("slot=")) id = id.substring(5);
        int comma = id.indexOf(',');
        if (comma > 0) id = id.substring(0, comma).trim();

        return id;
    }

    // ============================================================
    // ===== 工具 =================================================
    // ============================================================

    private static List<?> tryGetSubRecipes(Object recipe) {
        for (String mn : new String[]{"getRecipes", "getLevelRecipes", "getSubRecipes"}) {
            Method m = findMethod(recipe.getClass(), mn);
            if (m == null || m.getParameterCount() != 0) continue;
            try {
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof List<?> list && list.size() > 1) return list;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static RecipeKind detectKind(Object recipe) {
        Class<?> c = recipe.getClass();
        while (c != null && c != Object.class) {
            String n = c.getSimpleName();
            if (n.contains("Incremental")) return RecipeKind.INCREMENTAL;
            if (n.contains("Multilevel") || n.contains("MultiLevel"))
                return RecipeKind.MULTILEVEL;
            c = c.getSuperclass();
        }
        Object input = readFieldAny(recipe, "input");
        Object amount = readFieldAny(recipe, "amountPerInput", "amount_per_input");
        if (input instanceof Ingredient && amount instanceof Number) {
            return RecipeKind.INCREMENTAL;
        }
        return RecipeKind.SIMPLE;
    }

    private static boolean isModifierRecipe(Recipe<?> recipe) {
        Class<?> c = recipe.getClass();
        while (c != null && c != Object.class) {
            if (c.getSimpleName().contains("ModifierRecipe")) return true;
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

    private static Component extractDisplayName(Object modifier, int level) {
        for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
            try {
                Method m = modifier.getClass().getMethod(mn, int.class);
                m.setAccessible(true);
                Object v = m.invoke(modifier, level);
                if (v instanceof Component c) return c;
            } catch (Throwable ignored) {}
        }
        String id = extractModifierId(modifier);
        String base = id != null ? prettify(id) : "?";
        return new TextComponent(base + (level > 1 ? " " + level : ""));
    }

    private static int readIntField(Object obj, int def, String... names) {
        for (String name : names) {
            Object v = readFieldAny(obj, name);
            if (v instanceof Number n) return n.intValue();
        }
        return def;
    }

    private static int extractColor(Object modifier) {
        try {
            Method m = modifier.getClass().getMethod("getDisplayName", int.class);
            m.setAccessible(true);
            Object v = m.invoke(modifier, 1);
            if (v instanceof Component c) {
                TextColor tc = c.getStyle().getColor();
                if (tc != null) return tc.getValue();
                for (Component s : c.getSiblings()) {
                    TextColor tc2 = s.getStyle().getColor();
                    if (tc2 != null) return tc2.getValue();
                }
            }
        } catch (Throwable ignored) {}
        return 0xFFFFFF;
    }

    private static String getRecipeId(Object recipe) {
        try {
            Method m = recipe.getClass().getMethod("getId");
            Object v = m.invoke(recipe);
            if (v != null) return v.toString();
        } catch (Throwable ignored) {}
        return "";
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