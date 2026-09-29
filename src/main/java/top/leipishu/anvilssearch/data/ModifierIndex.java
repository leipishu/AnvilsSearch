package top.leipishu.anvilssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 照搬匠魂 JEI 插件的读取方式。
 *
 * 关键逻辑：
 *   1. 增量配方（IncrementalModifierRecipe）用 getInputs() 获取完整槽位材料。
 *   2. slotless 配方 getSlots() 返回 null → 显式构造 "none" 槽位。
 *   3. requirementsError 是匠魂翻译键时自动翻译。
 *   4. 变体名称检测 recipe.tconstruct 前缀，跳过未翻译的 key。
 *   5. 剔除材料为空的空配方。
 *   6. 非增量配方且 maxLevel > baseLevel 时，展开成多个 Entry（如阅历 I~V）。
 *   7. Entry 保留原始 display 配方对象，供 UI 做前置条件动态检查。
 */
public final class ModifierIndex {

    public enum RecipeKind { SIMPLE, INCREMENTAL, MULTILEVEL, SWAPPABLE }

    // ============================================================
    // ===== 公开数据结构 =========================================
    // ============================================================

    public static final class SlotRequirement {
        public final String typeId;
        public final Component displayName;
        public final int count;

        public SlotRequirement(String typeId, Component displayName, int count) {
            this.typeId = (typeId != null && !typeId.isEmpty()) ? typeId : "unknown";
            this.displayName = displayName != null ? displayName : new TextComponent("?");
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
                         Object toolFilter, RecipeKind kind,
                         int amountPerInput, int neededPerLevel,
                         String requirementsError, Component variant) {
            this.level = level;
            this.displayName = displayName != null ? displayName : new TextComponent("?");
            this.slots = slots != null ? slots : Collections.emptyList();
            this.materials = materials != null ? materials : Collections.emptyList();
            this.materialLines = materialLines != null ? materialLines : Collections.emptyList();
            this.slotMaterials = slotMaterials;
            this.toolFilter = toolFilter;
            this.kind = kind != null ? kind : RecipeKind.SIMPLE;
            this.amountPerInput = amountPerInput;
            this.neededPerLevel = neededPerLevel;
            this.requirementsError = requirementsError;
            this.variant = variant;
        }
    }

    private static final class MaterialResult {
        final List<ItemStack> icons;
        final List<Component> lines;
        final List<ItemStack>[] slotIcons;
        @SuppressWarnings("unchecked")
        MaterialResult(List<ItemStack> icons, List<Component> lines,
                       List<ItemStack>[] slotIcons) {
            this.icons = icons != null ? icons : Collections.emptyList();
            this.lines = lines != null ? lines : Collections.emptyList();
            this.slotIcons = slotIcons;
        }
    }

    public static final class Entry {
        public final Object modifier;
        /** ★ 原始 display 配方对象，用于 UI 调用 getValidatedResult */
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
            return new TextComponent(prettify(registryPath) + " " + level);
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
            if (cache == null) cache = build();
            return cache;
        }
    }

    public static List<Entry> getGrouped() { return get(); }

    // ============================================================
    // ===== 构建 =================================================
    // ============================================================

    private static List<Entry> build() {
        long t0 = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            System.out.println("[Anvil's Search] ModifierIndex: no connection");
            return Collections.emptyList();
        }
        RecipeManager rm = mc.getConnection().getRecipeManager();

        List<Object> candidates = new ArrayList<>();
        int scanned = 0;

        Collection<Recipe<?>> allRecipes = rm.getRecipes();
        System.out.println("[Anvil's Search] total recipes in RecipeManager: "
                + allRecipes.size());

        for (Recipe<?> recipe : allRecipes) {
            if (recipe == null) continue;
            if (!isTinkerStationRecipe(recipe)) continue;
            if (!isDisplayModifierRecipe(recipe)) continue;

            scanned++;
            Class<?> cls = recipe.getClass();
            if (DIAGNOSED.add(cls)) {
                System.out.println("[Anvil's Search] ModifierRecipe diag class = "
                        + cls.getName());
            }
            candidates.add(recipe);
        }

        System.out.println("[Anvil's Search] TINKER_STATION candidates: " + candidates.size());

        // 展开 IMultiRecipe
        List<Object> displayRecipes = new ArrayList<>();
        for (Object r : candidates) {
            List<?> subs = tryExpandMultiRecipe(r);
            if (subs != null && !subs.isEmpty()) {
                for (Object sub : subs) {
                    if (sub != null && isDisplayModifierRecipe(sub)) {
                        displayRecipes.add(sub);
                    }
                }
            } else {
                displayRecipes.add(r);
            }
        }

        System.out.println("[Anvil's Search] after MultiRecipe expand: "
                + displayRecipes.size());

        if (displayRecipes.isEmpty()) return Collections.emptyList();

        List<Entry> result = new ArrayList<>();
        int skippedEmpty = 0;
        for (Object display : displayRecipes) {
            List<Entry> entries = buildEntriesFromDisplay(display);
            if (entries.isEmpty()) {
                skippedEmpty++;
            } else {
                result.addAll(entries);
            }
        }

        result.sort(Comparator.comparing(Entry::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] ModifierIndex built: " + result.size()
                + " entries from " + scanned + " candidates / "
                + displayRecipes.size() + " display recipes, skipped empty = "
                + skippedEmpty + " in "
                + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    private static List<Entry> buildEntriesFromDisplay(Object display) {
        List<Entry> entries = new ArrayList<>();
        try {
            Object entry = call(display, "getDisplayResult");
            if (entry == null) return entries;

            Object modifier = call(entry, "getModifier");
            if (modifier == null) return entries;

            String modifierId = extractModifierId(modifier);
            if (modifierId == null || modifierId.isEmpty()) return entries;

            int baseLevel = intOf(entry, "getLevel");
            if (baseLevel <= 0) baseLevel = 1;

            int color = extractColor(modifier);

            MaterialResult mr = readMaterialsFromDisplay(display);

            if (mr.icons.isEmpty()) {
                return entries;
            }

            List<SlotRequirement> slots = readSlotsFromDisplay(display);

            boolean incremental = boolOf(display, "isIncremental");
            int maxLevel = intOf(display, "getMaxLevel");
            if (maxLevel <= 0) maxLevel = baseLevel;

            RecipeKind kind = incremental
                    ? RecipeKind.INCREMENTAL
                    : detectKindByName(display);

            int amountPerInput = 0, neededPerLevel = 0;
            if (incremental) {
                amountPerInput = intField(display, "amountPerInput", "amount_per_input");
                neededPerLevel = intField(display, "neededPerLevel", "needed_per_level");
                if (amountPerInput <= 0) amountPerInput = 1;
                if (neededPerLevel <= 0) neededPerLevel = 1;
            }

            String requirementsError = null;
            if (boolOf(display, "hasRequirements")) {
                Object err = call(display, "getRequirementsError");
                if (err instanceof Component c) {
                    requirementsError = resolveTranslation(c.getString());
                } else if (err instanceof String s && !s.isEmpty()) {
                    requirementsError = resolveTranslation(s);
                }
            }

            Component variant = null;
            Object variantObj = call(display, "getVariant");
            if (variantObj instanceof Component vc) {
                String vk = vc.getString();
                if (vk != null && !vk.isEmpty()
                        && !vk.startsWith("recipe.tconstruct")
                        && !vk.startsWith("gui.tconstruct")) {
                    variant = vc;
                }
            }

            Object toolFilter = readToolRequirement(display);

            String path = modifierId;
            int colon = modifierId.indexOf(':');
            if (colon >= 0) path = modifierId.substring(colon + 1);

            if (!incremental && maxLevel > baseLevel) {
                for (int level = baseLevel; level <= maxLevel; level++) {
                    Component dn = extractDisplayName(modifier, level);
                    LevelInfo lv = new LevelInfo(level, dn, slots, mr.icons, mr.lines,
                            mr.slotIcons, toolFilter, kind, amountPerInput, neededPerLevel,
                            requirementsError, variant);

                    String id = modifierId + "#" + level;
                    entries.add(new Entry(modifier, display, id, path, color, maxLevel,
                            Collections.singletonList(lv)));
                }
            } else {
                Component dn = extractDisplayName(modifier, baseLevel);
                LevelInfo lv = new LevelInfo(baseLevel, dn, slots, mr.icons, mr.lines,
                        mr.slotIcons, toolFilter, kind, amountPerInput, neededPerLevel,
                        requirementsError, variant);

                String id = modifierId + "#" + baseLevel;
                entries.add(new Entry(modifier, display, id, path, color, maxLevel,
                        Collections.singletonList(lv)));
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] failed to build entries: " + t);
        }
        return entries;
    }

    // ============================================================
    // ===== 材料读取 =============================================
    // ============================================================

    @SuppressWarnings("unchecked")
    private static MaterialResult readMaterialsFromDisplay(Object display) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        List<ItemStack>[] slotIcons = new List[5];
        for (int i = 0; i < 5; i++) slotIcons[i] = Collections.emptyList();

        boolean incremental = boolOf(display, "isIncremental");

        if (incremental) {
            Object inputsObj = call(display, "getInputs");
            if (inputsObj instanceof List<?> slotList) {
                for (int slot = 0; slot < Math.min(slotList.size(), 5); slot++) {
                    Object slotItems = slotList.get(slot);
                    if (!(slotItems instanceof List<?> items) || items.isEmpty()) continue;
                    List<ItemStack> slotMats = new ArrayList<>();
                    for (Object o : items) {
                        if (!(o instanceof ItemStack stack) || stack.isEmpty()) continue;
                        ItemStack copy = stack.copy();
                        slotMats.add(copy);
                        icons.add(copy);
                        lines.add(new TextComponent("\u00A77"
                                + copy.getHoverName().getString()
                                + (copy.getCount() > 1 ? " \u00D7" + copy.getCount() : "")));
                    }
                    slotIcons[slot] = slotMats;
                }
            }
            return new MaterialResult(icons, lines, slotIcons);
        }

        Object direct = call(display, "getInputs");
        if (direct == null) direct = call(display, "getDisplayInputs");
        if (direct instanceof Collection<?> col && !col.isEmpty()) {
            return materializeInputs(col, slotIcons);
        }

        for (int slot = 0; slot <= 4; slot++) {
            Object v = call(display, "getDisplayItems", int.class, slot);
            if (!(v instanceof List<?> list) || list.isEmpty()) continue;

            List<ItemStack> slotList = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof ItemStack stack) || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                slotList.add(copy);
                icons.add(copy);
                lines.add(new TextComponent("\u00A77"
                        + copy.getHoverName().getString()
                        + (copy.getCount() > 1 ? " \u00D7" + copy.getCount() : "")));
            }
            slotIcons[slot] = slotList;
        }

        return new MaterialResult(icons, lines, slotIcons);
    }

    @SuppressWarnings("unchecked")
    private static MaterialResult materializeInputs(Collection<?> inputs,
                                                    List<ItemStack>[] slotIcons) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, ItemStack> repMap = new LinkedHashMap<>();

        for (Object sized : inputs) {
            if (sized == null) continue;
            ItemStack stack = extractStackFromSized(sized);
            int count = extractCount(sized);
            if (stack.isEmpty()) continue;

            ItemStack iconCopy = stack.copy();
            iconCopy.setCount(count);
            icons.add(iconCopy);

            String key = stackKey(stack);
            countMap.merge(key, count, Integer::sum);
            repMap.putIfAbsent(key, stack);
        }

        for (Map.Entry<String, Integer> e : countMap.entrySet()) {
            ItemStack rep = repMap.get(e.getKey());
            lines.add(new TextComponent("\u00A77"
                    + rep.getHoverName().getString() + " \u00D7" + e.getValue()));
        }
        return new MaterialResult(icons, lines, slotIcons);
    }

    // ============================================================
    // ===== 槽位读取 =============================================
    // ============================================================

    private static List<SlotRequirement> readSlotsFromDisplay(Object display) {
        Object slots = call(display, "getSlots");
        if (slots == null) {
            return Collections.singletonList(new SlotRequirement(
                    "none", new TranslatableComponent("gui.anvilssearch.slot.none"), 0));
        }

        Object type = call(slots, "getType");
        int count = intOf(slots, "getCount");

        if (type == null) {
            return Collections.singletonList(new SlotRequirement(
                    "none", new TranslatableComponent("gui.anvilssearch.slot.none"), 0));
        }

        String typeName = slotTypeName(type);
        String norm = normalizeSlotType(typeName);
        return Collections.singletonList(
                new SlotRequirement(norm, slotTypeDisplayName(norm), Math.max(0, count)));
    }

    // ============================================================
    // ===== 接口检查 =============================================
    // ============================================================

    private static boolean implementsInterface(Class<?> cls, String interfaceName) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Class<?> iface : c.getInterfaces()) {
                if (iface.getSimpleName().equals(interfaceName)) return true;
                if (implementsInterface(iface, interfaceName)) return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean isTinkerStationRecipe(Recipe<?> recipe) {
        return implementsInterface(recipe.getClass(), "ITinkerStationRecipe");
    }

    private static boolean isDisplayModifierRecipe(Object recipe) {
        if (recipe == null) return false;
        return implementsInterface(recipe.getClass(), "IDisplayModifierRecipe")
                || implementsInterface(recipe.getClass(), "IModifierRecipe");
    }

    private static boolean isMultiRecipe(Object recipe) {
        return implementsInterface(recipe.getClass(), "IMultiRecipe");
    }

    private static List<?> tryExpandMultiRecipe(Object recipe) {
        if (!isMultiRecipe(recipe)) return null;
        for (String mn : new String[]{"getRecipes", "getLevelRecipes", "getSubRecipes"}) {
            Object v = call(recipe, mn);
            if (v instanceof List<?> list && !list.isEmpty()) return list;
            if (v instanceof Collection<?> col && !col.isEmpty()) return new ArrayList<>(col);
        }
        return null;
    }

    // ============================================================
    // ===== 反射工具 =============================================
    // ============================================================

    private static Object call(Object target, String name) {
        return call(target, name, new Class<?>[0]);
    }

    private static Object call(Object target, String name, Class<?> pType, Object arg) {
        return call(target, name, new Class<?>[]{pType}, arg);
    }

    private static Object call(Object target, String name, Class<?>[] pTypes, Object... args) {
        if (target == null) return null;
        Method m = findMethod(target.getClass(), name, pTypes);
        if (m == null) return null;
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intOf(Object target, String name) {
        Object v = call(target, name);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static boolean boolOf(Object target, String name) {
        Object v = call(target, name);
        return v instanceof Boolean b && b;
    }

    private static int intField(Object target, String... names) {
        Object v = field(target, names);
        return v instanceof Number n ? n.intValue() : 0;
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

    private static Object field(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(target);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static String extractModifierId(Object modifier) {
        Object v = call(modifier, "getId");
        if (v == null) v = call(modifier, "getIdentifier");
        if (v == null) v = call(modifier, "getRegistryName");
        return v != null ? v.toString() : null;
    }

    private static Component extractDisplayName(Object modifier, int level) {
        Object v = call(modifier, "getDisplayName", int.class, level);
        if (v instanceof Component c) return c;
        String id = extractModifierId(modifier);
        String base = id != null ? prettify(id) : "?";
        return new TextComponent(base + (level > 1 ? " " + level : ""));
    }

    private static int extractColor(Object modifier) {
        Object v = call(modifier, "getDisplayName", int.class, 1);
        if (v instanceof Component c) {
            TextColor tc = c.getStyle().getColor();
            if (tc != null) return tc.getValue();
            for (Component s : c.getSiblings()) {
                TextColor tc2 = s.getStyle().getColor();
                if (tc2 != null) return tc2.getValue();
            }
        }
        return 0xFFFFFF;
    }

    private static Object readToolRequirement(Object display) {
        Object v = call(display, "getToolRequirement");
        if (v == null) v = call(display, "getToolIngredient");
        if (v == null) v = field(display, "toolRequirement", "tools", "toolFilter", "toolIngredient");
        return v;
    }

    private static RecipeKind detectKindByName(Object display) {
        Class<?> c = display.getClass();
        while (c != null && c != Object.class) {
            String n = c.getSimpleName();
            if (n.contains("Incremental")) return RecipeKind.INCREMENTAL;
            if (n.contains("Multilevel") || n.contains("MultiLevel")) return RecipeKind.MULTILEVEL;
            if (n.contains("Swappable")) return RecipeKind.SWAPPABLE;
            c = c.getSuperclass();
        }
        return RecipeKind.SIMPLE;
    }

    private static String slotTypeName(Object slotType) {
        if (slotType == null) return null;
        if (slotType instanceof String s) return s;
        Object v = call(slotType, "getName");
        if (v == null) v = call(slotType, "getId");
        return v != null ? String.valueOf(v) : String.valueOf(slotType);
    }

    private static String normalizeSlotType(String raw) {
        if (raw == null || raw.isEmpty()) return "none";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("upgrade"))  return "upgrade";
        if (lower.contains("abilit"))   return "ability";
        if (lower.contains("defense"))  return "defense";
        if (lower.contains("soul"))     return "soul";
        int colon = lower.lastIndexOf(':');
        if (colon >= 0 && colon < lower.length() - 1) lower = lower.substring(colon + 1);
        return lower.isEmpty() ? "none" : lower;
    }

    private static Component slotTypeDisplayName(String typeId) {
        if (typeId == null || typeId.isEmpty()) return new TextComponent("?");
        if ("none".equals(typeId)) {
            return new TranslatableComponent("gui.anvilssearch.slot.none");
        }
        TranslatableComponent tc = new TranslatableComponent(
                "gui.anvilssearch.slot." + typeId);
        String s = tc.getString();
        if (s != null && s.equals(tc.getKey())) {
            String p = typeId;
            if (!p.isEmpty()) p = Character.toUpperCase(p.charAt(0)) + p.substring(1);
            return new TextComponent(p);
        }
        return tc;
    }

    private static ItemStack extractStackFromSized(Object sized) {
        if (sized == null) return ItemStack.EMPTY;
        if (sized instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
        Object v = call(sized, "getIngredient");
        if (v == null) v = call(sized, "getInner");
        if (v == null) v = call(sized, "getItem");
        if (v == null) v = call(sized, "getStack");
        return extractFromAny(v, 2);
    }

    private static ItemStack extractFromAny(Object v, int depth) {
        if (v == null || depth < 0) return ItemStack.EMPTY;
        if (v instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s.copy();
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
        return ItemStack.EMPTY;
    }

    private static int extractCount(Object sized) {
        Object v = call(sized, "getCount");
        if (v == null) v = call(sized, "getAmount");
        if (v == null) v = call(sized, "getNeeded");
        return v instanceof Number n ? n.intValue() : 1;
    }

    private static String stackKey(ItemStack stack) {
        try {
            String regName = stack.getItem().getRegistryName() != null
                    ? stack.getItem().getRegistryName().toString()
                    : stack.getItem().toString();
            String nbt = stack.getTag() != null ? stack.getTag().toString() : "";
            return regName + "|" + nbt;
        } catch (Throwable t) {
            return stack.toString();
        }
    }

    private static String resolveTranslation(String key) {
        if (key == null || key.isEmpty()) return key;
        if (key.startsWith("recipe.tconstruct")
                || key.startsWith("gui.tconstruct")
                || key.startsWith("modifier.tconstruct")) {
            try {
                TranslatableComponent tc = new TranslatableComponent(key);
                String translated = tc.getString();
                if (!translated.equals(key)) return translated;
            } catch (Throwable ignored) {}
            return new TranslatableComponent(
                    "gui.anvilssearch.modifier.requirements_generic").getString();
        }
        return key;
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