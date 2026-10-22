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
    static boolean isSound(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String ext : new String[] {".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac"}) if (n.endsWith(ext)) return true;
        return false;
    }

    static boolean isImage(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    // "a,b,a" -> [a, b], in order. Used for the HDMI inputs hidden from the home.
    static Set<String> splitSet(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String part : s.split(",")) if (!part.trim().isEmpty() && !part.trim().equals("none")) out.add(part.trim());
        return out;
    }

    static String joinSet(Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String part : s) b.append(b.length() == 0 ? "" : ",").append(part);
        return b.toString();
    }

    // The custom banner for pkg among the files in the banners folder: "<pkg>.<picture extension>".
    static final java.util.List<String> TV_PLAYERS = java.util.Arrays.asList("com.tcl.tv", "com.android.tv",
            "com.droidlogic.android.tv", "com.google.android.tv.inputplayer");

    static boolean slideshowOnSourceLost(int before, int after, String foreground, boolean enabled) {
        return enabled && before == 0 && after != 0 && TV_PLAYERS.contains(foreground);
    }

    static String fileKey(String id) {
        return id.replaceAll("[^A-Za-z0-9._]", "_");
    }

    static String bannerFile(String pkg, String[] files) {
        if (files == null) return null;
        for (String f : files) {
            int dot = f.lastIndexOf('.');
            if (dot > 0 && f.substring(0, dot).equals(pkg) && isImage(f)) return f;
        }
        return null;
    }

    // Wallpaper rotation: the choices offered on the TV, as "<number><s|m|h|w>"; "off" or anything
    // else that doesn't parse means no rotation.
    static final String[] ROTATE_CHOICES = {"off", "5m", "10m", "30m", "1h", "4h", "12h", "24h", "1w"};

    static long intervalMillis(String s) {
        if (s == null || s.length() < 2) return 0;
        long unit;
        switch (s.charAt(s.length() - 1)) {
            case 's': unit = 1000L; break;
            case 'm': unit = 60 * 1000L; break;
            case 'h': unit = 3600 * 1000L; break;
            case 'w': unit = 7 * 24 * 3600 * 1000L; break;
            default: return 0;
        }
        try {
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            return n > 0 ? n * unit : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // The wallpaper after current in the rotation, wrapping around; the first one if current isn't in it.
    static String nextInRotation(java.util.List<String> rotation, String current) {
        if (rotation.isEmpty()) return null;
        int i = rotation.indexOf(current);
        return rotation.get(i < 0 ? 0 : (i + 1) % rotation.size());
    }

    // Due once interval has passed since the last change. A last change in the future (the clock was
    // set back) counts as due, so the rotation can't get stuck.
    static boolean rotationDue(long lastChange, long now, long interval) {
        return interval > 0 && (now - lastChange >= interval || lastChange > now);
    }

    // The screensaver's slow zoom-and-pan for picture number seed: {startScale, endScale, startX,
    // startY, endX, endY}, with x/y as a fraction of the view. Zooms in or out by 8-12% and drifts
    // between two corners, never past the picture's edge: |offset| <= (scale - 1) / 2.
    static float[] drift(int seed) {
        java.util.Random r = new java.util.Random(seed * 7919L + 17);
        float small = 1.02f, big = 1.10f + r.nextFloat() * 0.04f;
        boolean zoomIn = r.nextBoolean();
        float s0 = zoomIn ? small : big, s1 = zoomIn ? big : small;
        int corner = r.nextInt(4);
        float[] d = {s0, s1, 0, 0, 0, 0};
        float m0 = (s0 - 1) / 2, m1 = (s1 - 1) / 2;
        float sx = (corner & 1) == 0 ? -1 : 1, sy = (corner & 2) == 0 ? -1 : 1;
        d[2] = sx * m0; d[3] = sy * m0;
        d[4] = -sx * m1; d[5] = -sy * m1;
        return d;
    }

    // Bing's caption is "Title (© credit)": the title only.
    static String bingTitle(String copyright) {
        if (copyright == null) return "";
        int i = copyright.indexOf(" (\u00a9");
        return (i > 0 ? copyright.substring(0, i) : copyright).trim();
    }
}
