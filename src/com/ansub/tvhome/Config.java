package com.ansub.tvhome;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Parsing for the settings passed with am start. No Android classes, so ci/unit-test.sh can run it with plain java.
final class Config {
    private Config() {}

    // Keys the map may never take: Home, Back, the arrows and OK, volume, power, Enter, Menu and the
    // media keys. The map reaches a system-wide key filter, so a typo or another app's extra must not
    // be able to swallow the keys every app needs.
    private static final java.util.Set<Integer> RESERVED = new java.util.HashSet<>(java.util.Arrays.asList(
            3, 4, 19, 20, 21, 22, 23, 24, 25, 26, 66, 82, 85, 86, 87, 88, 89, 90, 160, 164));

    // "4020:com.stremio.one,84:pkg" -> {4020=com.stremio.one, 84=pkg}. Entries that don't parse, and
    // reserved keys, are skipped.
    static Map<Integer, String> parseKeys(String s) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String entry : s.split(",")) {
            int colon = entry.indexOf(':');
            if (colon < 0) continue;
            String pkg = entry.substring(colon + 1).trim();
            if (pkg.isEmpty()) continue;
            try {
                int code = Integer.parseInt(entry.substring(0, colon).trim());
                if (!RESERVED.contains(code)) out.put(code, pkg);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    // A key from a game controller (InputDevice SOURCE_GAMEPAD or SOURCE_JOYSTICK). Pads send key codes
    // like 190 (BUTTON_3) that the map uses for the YouTube remote button, so they're never remapped.
    static boolean fromController(int source) {
        return (source & 0x401) == 0x401 || (source & 0x1000010) == 0x1000010;
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

    // The custom banner for pkg among the files in the banners folder: "<pkg>.<picture extension>".
    static String bannerFile(String pkg, String[] files) {
        if (files == null) return null;
        for (String f : files) {
            int dot = f.lastIndexOf('.');
            if (dot > 0 && f.substring(0, dot).equals(pkg) && isImage(f)) return f;
        }
        return null;
    }
}
