package com.ansub.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    static final long SLIDE = 1500;
    static final float GAP = 0.35f;

    static float[] slide(long ms, int dir) {
        float zoom = 1.12f, travel = Math.min(0.005f * ms / 1000f, zoom - 1), top = (zoom - 1) / 2;
        return new float[] {zoom, zoom, -dir * travel / 2, top, dir * travel / 2, top};
    }

    static int slideDir(int seed) {
        return seed % 2 == 0 ? 1 : -1;
    }

    static final long ROUND = 2000;
    static final float SUN_R = 3.4f, SUN_GAP = 0.6f, SUN_TURN = 45f, SUN_DEPTH = 0.8f, SUN_EYE = 1.6f;

    static float[] sun(float u, float w, float h) {
        double arc = Math.asin((1 + SUN_GAP) / SUN_R) * u, phi = Math.toRadians(SUN_TURN) * u;
        float zc = SUN_DEPTH * u * u, s = SUN_EYE / (SUN_EYE + zc);
        float cx = w / 2 + SUN_R * (float) Math.sin(arc) * w * s, cy = h / 2 + SUN_R * (1 - (float) Math.cos(arc)) * w * s;
        float b = h / (2 * w);
        float[] corners = {-0.5f, -b, 0.5f, -b, 0.5f, b, -0.5f, b}, q = new float[9];
        for (int i = 0; i < 8; i += 2) {
            float x = corners[i], f = SUN_EYE / (SUN_EYE + zc + x * (float) Math.sin(phi));
            q[i] = cx + x * (float) Math.cos(phi) * f * w;
            q[i + 1] = cy + corners[i + 1] * f * w;
        }
        q[8] = Math.max(0.35f, 1f - zc * 0.5f);
        return q;
    }

    static long realTime(long ms, float scale) {
        return scale > 0 ? Math.round(ms / (double) scale) : ms;
    }

    static float[] push(float p, int dir) {
        float far = 1f + GAP;
        return new float[] {dir * p * far, -dir * (1f - p) * far};
    }

    static float[] cover(int sw, int sh, int w, int h) {
        float s = Math.max((float) w / sw, (float) h / sh);
        return new float[] {s, (w - sw * s) / 2, 0f};
    }

    static String episode(String id, String videoId) {
        if (id == null || videoId == null || !videoId.startsWith(id + ":")) return null;
        String[] p = videoId.substring(id.length() + 1).split(":");
        try {
            if (p.length == 2) return "S" + Integer.parseInt(p[0]) + " · E" + Integer.parseInt(p[1]);
            if (p.length == 1) return "E" + Integer.parseInt(p[0]);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    static String timeLeft(long offsetMs, long durationMs) {
        if (durationMs <= 0 || offsetMs >= durationMs) return null;
        long m = Math.max(1, Math.round((durationMs - offsetMs) / 60000.0));
        return (m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m") + " left";
    }

    static float progress(long offset, long duration) {
        return duration <= 0 ? 0f : Math.min(1f, offset / (float) duration);
    }

    static String stremioLink(String type, String id, String videoId) {
        return "stremio:///detail/" + type + "/" + id + (videoId == null || videoId.equals(id) ? "" : "/" + videoId);
    }

    static List<String> mix(List<String> a, List<String> b, java.util.Set<String> skip, int max) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < Math.max(a.size(), b.size()) && out.size() < max; i++)
            for (List<String> l : Arrays.asList(a, b))
                if (i < l.size() && !skip.contains(l.get(i)) && out.size() < max) out.add(l.get(i));
        return new ArrayList<>(out);
    }

    // Bing's caption is "Title (© credit)": the title only.
    static String bingTitle(String copyright) {
        if (copyright == null) return "";
        int i = copyright.indexOf(" (\u00a9");
        return (i > 0 ? copyright.substring(0, i) : copyright).trim();
    }
}
