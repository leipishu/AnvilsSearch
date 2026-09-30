package top.leipishu.anvilssearch.data.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import top.leipishu.tinkerssearch.data.FluidPartData;
import top.leipishu.tinkerssearch.data.FluidPartDataCache;

/**
 * 桥接：从 (MaterialId, 部件 itemId) 拿到 tinkerssearch 的 PartInfo。
 * 属性、词条全部从 tinkerssearch 的数据层取，保证两个模组展示一致。
 */
public final class PartInfoLookup {

    private PartInfoLookup() {}

    public static FluidPartData.PartInfo find(MaterialId mat, ResourceLocation partItemId) {
        if (mat == null || partItemId == null) return null;

        Fluid fluid = MaterialFluidResolver.getFluid(mat);
        if (fluid == null) return null;

        try {
            FluidStack fs = new FluidStack(fluid, 1);
            FluidPartData data = FluidPartDataCache.get(fs);
            if (data == null || data.entries == null) return null;

            // 精确匹配 materialId + itemId
            for (FluidPartData.MaterialEntry e : data.entries) {
                if (e.materialId == null || !e.materialId.equals(mat)) continue;
                FluidPartData.PartInfo p = findPart(e, partItemId);
                if (p != null) return p;
            }

            // 兜底：只匹配 itemId（应对 variant 差异）
            for (FluidPartData.MaterialEntry e : data.entries) {
                FluidPartData.PartInfo p = findPart(e, partItemId);
                if (p != null) return p;
            }
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] PartInfoLookup failed: " + t);
        }
        return null;
    }

    private static FluidPartData.PartInfo findPart(FluidPartData.MaterialEntry e,
                                                   ResourceLocation partItemId) {
        if (e.parts == null) return null;
        for (FluidPartData.PartInfo p : e.parts) {
            if (p.itemId != null && p.itemId.equals(partItemId)) return p;
        }
        return null;
    }
}