package top.leipishu.anvilssearch.data.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.tinkerssearch.recipe.CastingRecipeHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部件 → 可用材料 索引。
 * 数据源：从所有 ToolDefinition 通过 ToolPartsHook.parts() 反查部件 item。
 * ★ 支持两类部件：
 *   1. IMaterialItem 部件（镐头、手柄等）：走 CastingRecipeHelper.materialCanProduce 判定
 *   2. 非 IMaterialItem 的特殊部件（黏液头颅等）：从 MaterialRegistry 反查同 statType 的材料
 */
public final class PartMaterialIndex {

    public static final class Entry {
        public final MaterialId id;
        public final String registryPath;

        public Entry(MaterialId id, String registryPath) {
            this.id = id;
            this.registryPath = registryPath;
        }

        public String getDisplayName() {
            try {
                return MaterialDetailBuilder.materialNameComponent(id).getString();
            } catch (Throwable ignored) {}
            return prettify(registryPath);
        }
    }

    public static final class PartEntry {
        public final ResourceLocation partId;
        public final Item partItem;
        public final String statTypePath;
        public final String displayNameCache;
        public final List<Entry> materials;

        public PartEntry(ResourceLocation partId, Item partItem, String statTypePath,
                         String displayNameCache, List<Entry> materials) {
            this.partId = partId;
            this.partItem = partItem;
            this.statTypePath = statTypePath;
            this.displayNameCache = displayNameCache;
            this.materials = materials;
        }

        public String getDisplayName() {
            try {
                if (partItem != null) {
                    String s = new ItemStack(partItem).getHoverName().getString();
                    if (s != null && !s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
            return displayNameCache;
        }
    }

    private static volatile List<PartEntry> cache;
    private static final Object LOCK = new Object();

    private PartMaterialIndex() {}

    public static void invalidate() {
        synchronized (LOCK) { cache = null; }
    }

    public static List<PartEntry> get() {
        List<PartEntry> local = cache;
        if (local != null) return local;
        synchronized (LOCK) {
            if (cache != null) return cache;
            cache = build();
            return cache;
        }
    }

    public static List<Entry> forPart(ResourceLocation partId) {
        for (PartEntry p : get()) {
            if (p.partId.equals(partId)) return p.materials;
        }
        return Collections.emptyList();
    }

    public static List<Entry> forStatType(String statTypePath) {
        if (statTypePath == null) return Collections.emptyList();
        for (PartEntry p : get()) {
            if (statTypePath.equals(p.statTypePath)) return p.materials;
        }
        return Collections.emptyList();
    }

    private static List<PartEntry> build() {
        long t0 = System.currentTimeMillis();
        List<PartEntry> result = new ArrayList<>();

        Map<ResourceLocation, String> partToStatType = new LinkedHashMap<>();
        Map<ResourceLocation, MaterialStatsId> specialPartStatTypes = new LinkedHashMap<>();
        int toolCount = 0;

        for (ToolDefinitionIndex.Entry toolEntry : ToolDefinitionIndex.get()) {
            try {
                if (!toolEntry.definition.isDataLoaded()) continue;

                // ★ 1.19.2：ToolPartsHook.parts(definition)
                List<IToolPart> parts = ToolPartsHook.parts(toolEntry.definition);
                if (parts == null || parts.isEmpty()) continue;
                toolCount++;

                for (IToolPart pr : parts) {
                    if (pr == null) continue;
                    Item partItem = extractPartItem(pr);
                    if (partItem == null) continue;

                    ResourceLocation pid = ForgeRegistries.ITEMS.getKey(partItem);
                    if (pid == null) continue;

                    String stPath = statTypePath(pr);

                    // ★ 非 IMaterialItem 的特殊部件：记录 statType，稍后反查
                    if (!(partItem instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem)) {
                        MaterialStatsId st = pr.getStatType();
                        if (st != null) specialPartStatTypes.put(pid, st);
                        continue;
                    }

                    partToStatType.putIfAbsent(pid, stPath);
                }
            } catch (Throwable ignored) {}
        }

        System.out.println("[Anvil's Search] PartMaterialIndex: scanned " + toolCount
                + " tools, " + partToStatType.size() + " normal parts, "
                + specialPartStatTypes.size() + " special parts");

        List<MaterialId> allMats = collectAllMaterialIds();

        for (Map.Entry<ResourceLocation, String> e : partToStatType.entrySet()) {
            ResourceLocation pid = e.getKey();
            Item item = ForgeRegistries.ITEMS.getValue(pid);
            if (!(item instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi))
                continue;

            List<Entry> mats = new ArrayList<>();
            for (MaterialId mid : allMats) {
                try {
                    if (CastingRecipeHelper.materialCanProduce(mi, mid))
                        mats.add(new Entry(mid, mid.getPath()));
                } catch (Throwable ignored) {}
            }
            if (mats.isEmpty()) continue;
            mats.sort(Comparator.comparing(Entry::getDisplayName,
                    String.CASE_INSENSITIVE_ORDER));
            result.add(new PartEntry(pid, item, e.getValue(),
                    hoverName(item, pid), mats));
        }

        for (Map.Entry<ResourceLocation, MaterialStatsId> e : specialPartStatTypes.entrySet()) {
            ResourceLocation pid = e.getKey();
            MaterialStatsId statType = e.getValue();
            if (statType == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(pid);
            if (item == null) continue;

            List<Entry> mats = new ArrayList<>();
            for (MaterialId mid : allMats) {
                try {
                    if (MaterialRegistry.getInstance()
                            .getMaterialStats(mid, statType).isPresent())
                        mats.add(new Entry(mid, mid.getPath()));
                } catch (Throwable ignored) {}
            }
            if (mats.isEmpty()) continue;
            mats.sort(Comparator.comparing(Entry::getDisplayName,
                    String.CASE_INSENSITIVE_ORDER));
            result.add(new PartEntry(pid, item, statIdPath(statType),
                    hoverName(item, pid), mats));
        }

        if (result.isEmpty() && !allMats.isEmpty()) {
            List<Entry> mats = new ArrayList<>();
            for (MaterialId mid : allMats) mats.add(new Entry(mid, mid.getPath()));
            mats.sort(Comparator.comparing(Entry::getDisplayName,
                    String.CASE_INSENSITIVE_ORDER));
            for (Map.Entry<ResourceLocation, String> e : partToStatType.entrySet()) {
                ResourceLocation pid = e.getKey();
                Item item = ForgeRegistries.ITEMS.getValue(pid);
                if (item == null) continue;
                result.add(new PartEntry(pid, item, e.getValue(),
                        hoverName(item, pid), new ArrayList<>(mats)));
            }
        }

        result.sort(Comparator.comparing(PartEntry::getDisplayName,
                String.CASE_INSENSITIVE_ORDER));

        System.out.println("[Anvil's Search] PartMaterialIndex built: " + result.size()
                + " parts in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    private static String hoverName(Item item, ResourceLocation pid) {
        try {
            String s = new ItemStack(item).getHoverName().getString();
            if (s != null && !s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        return pid.getPath();
    }

    /**
     * ★ 1.19.2：IToolPart 继承自 IMaterialItem → ItemLike，
     * 直接调用 asItem() 即可获取对应的 Item。
     */
    private static Item extractPartItem(IToolPart pr) {
        if (pr == null) return null;
        try {
            return pr.asItem();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String statTypePath(IToolPart pr) {
        try {
            Object st = pr.getStatType();
            if (st instanceof MaterialStatsId msid) return statIdPath(msid);
            if (st == null) return "";
            String s = String.valueOf(st);
            int colon = s.indexOf(':');
            return (colon >= 0) ? s.substring(colon + 1) : s;
        } catch (Throwable ignored) { return ""; }
    }

    private static String statIdPath(MaterialStatsId st) {
        if (st == null) return "";
        for (String mn : new String[]{"getLocation", "getPath"}) {
            try {
                java.lang.reflect.Method m = st.getClass().getMethod(mn);
                Object v = m.invoke(st);
                if (v instanceof ResourceLocation rl) return rl.getPath();
                if (v instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
        }
        String s = String.valueOf(st);
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) return s.substring(colon + 1);
        return s;
    }

    private static List<MaterialId> collectAllMaterialIds() {
        try {
            IMaterialRegistry reg = MaterialRegistry.getInstance();
            if (reg == null) return Collections.emptyList();
            Collection<IMaterial> mats = null;
            try { mats = reg.getAllMaterials(); }
            catch (Throwable t) {
                try {
                    java.lang.reflect.Method m = reg.getClass().getMethod("getMaterials");
                    Object o = m.invoke(reg);
                    if (o instanceof Collection) {
                        @SuppressWarnings("unchecked")
                        Collection<IMaterial> c = (Collection<IMaterial>) o;
                        mats = c;
                    }
                } catch (Throwable ignored) {}
            }
            if (mats == null) return Collections.emptyList();
            List<MaterialId> list = new ArrayList<>();
            for (IMaterial m : mats) {
                if (m == null || m == IMaterial.UNKNOWN) continue;
                MaterialId id = m.getIdentifier();
                if (id != null) list.add(id);
            }
            return list;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
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