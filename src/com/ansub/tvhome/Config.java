package com.ansub.tvhome;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Parsing for the settings passed with am start. No Android classes, so ci/unit-test.sh can run it with plain java.
final class Config {
    private Config() {}

    // "4020:com.stremio.one,84:pkg" -> {4020=com.stremio.one, 84=pkg}. Entries that don't parse are skipped.
    static Map<Integer, String> parseKeys(String s) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String entry : s.split(",")) {
            int colon = entry.indexOf(':');
            if (colon < 0) continue;
            String pkg = entry.substring(colon + 1).trim();
            if (pkg.isEmpty()) continue;
            try { out.put(Integer.parseInt(entry.substring(0, colon).trim()), pkg); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }

    // Wallpaper images: the picture formats Android decodes everywhere.
    static boolean isImage(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    // "a,b,a" -> [a, b], in order. Used for the HDMI inputs hidden from the home.
    static Set<String> splitSet(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String part : s.split(",")) if (!part.trim().isEmpty()) out.add(part.trim());
        return out;
    }

    static String joinSet(Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String part : s) b.append(b.length() == 0 ? "" : ",").append(part);
        return b.toString();
    }
}
