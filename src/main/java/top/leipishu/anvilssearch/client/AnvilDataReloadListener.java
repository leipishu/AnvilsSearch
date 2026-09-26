package top.leipishu.anvilssearch.client;

import top.leipishu.anvilssearch.data.PartMaterialIndex;
import top.leipishu.anvilssearch.data.ToolDefinitionIndex;
import top.leipishu.anvilssearch.data.MaterialFluidResolver;

/**
 * 打开面板时通知各数据层做懒加载；
 * 配方重载时清缓存（由 AnvilsSearch.onRecipesUpdated 调用）。
 */
public final class AnvilDataReloadListener {

    private AnvilDataReloadListener() {}

    public static void onPanelOpen() {
        // 数据层用懒加载，此处无需操作；保留钩子以便将来强制重建
    }

    public static void invalidate() {
        PartMaterialIndex.invalidate();
        ToolDefinitionIndex.invalidate();
        MaterialFluidResolver.invalidate();
    }
}