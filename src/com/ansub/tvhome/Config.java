package com.ansub.tvhome;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
