package top.leipishu.anvilssearch.data.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import top.leipishu.tinkerssearch.recipe.MaterialResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * MaterialId → Fluid 反查。
 * 复用 tinkerssearch 的 MaterialResolver，保证语义一致。
 */
public final class MaterialFluidResolver {

    private static volatile Map<MaterialId, Fluid> cache;
    private static final Object LOCK = new Object();

    private MaterialFluidResolver() {}

    public static void invalidate() {
        synchronized (LOCK) { cache = null; }
    }

    public static Fluid getFluid(MaterialId mat) {
        if (mat == null) return null;
        return getMap().get(mat);
    }

    private static Map<MaterialId, Fluid> getMap() {
        Map<MaterialId, Fluid> local = cache;
        if (local != null) return local;
        synchronized (LOCK) {
            if (cache != null) return cache;
            cache = build();
            return cache;
        }
    }

    private static Map<MaterialId, Fluid> build() {
        long t0 = System.currentTimeMillis();
        Map<MaterialId, Fluid> map = new HashMap<>();

        for (Fluid fluid : ForgeRegistries.FLUIDS) {
            try {
                ResourceLocation mid = MaterialResolver.resolveAsResourceLocation(fluid);
                if (mid == null) continue;
                MaterialId mat = new MaterialId(mid);
                map.putIfAbsent(mat, fluid);
            } catch (Throwable ignored) {}
        }

        System.out.println("[Anvil's Search] MaterialFluidResolver: " + map.size()
                + " entries in " + (System.currentTimeMillis() - t0) + "ms");
        return map;
    }
}