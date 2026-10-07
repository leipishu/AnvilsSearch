package top.leipishu.anvilssearch.data;

import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 泛化版 SearchHelper，匹配语义与本体一致。 */
public final class AnvilSearchHelper {

    private AnvilSearchHelper() {}

    public static <T> List<T> filter(List<T> src, String keyword,
                                     Function<T, String> displayName,
                                     Function<T, String> registryPath) {
        if (src == null || src.isEmpty()) return new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>(src);

        String k = keyword.trim().toLowerCase();
        List<T> out = new ArrayList<>();

        for (T t : src) {
            if (t == null) continue;

            String name = displayName != null ? displayName.apply(t) : null;
            String clean = name == null ? "" : name.replace("Molten ", "").replace("熔融", "");
            if (clean.toLowerCase().contains(k)) { out.add(t); continue; }

            String path = registryPath != null ? registryPath.apply(t) : null;
            if (path != null && path.toLowerCase().contains(k)) { out.add(t); continue; }

            try {
                PinyinSearch.PinyinResult py = PinyinSearch.getPinyin(clean);
                if (py.fullPinyin.contains(k) || py.initials.contains(k)) out.add(t);
            } catch (Throwable ignored) {}
        }
        return out;
    }
}