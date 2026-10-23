package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Stremio {
    static final String PACKAGE = "com.stremio.one";
    private static final String API = "https://api.strem.io/api/", CINEMETA = "https://v3-cinemeta.strem.io/";
    private static final int MAX = 10, KEEP_IMAGES = 200;
    private static final long EVERY = 60 * 1000L;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static long refreshedAt;

    static final class Item {
        String id, type, name, poster, background, logo, description, rating, year, genres, episode, under, link;
        float progress = -1f;
    }

    private Stremio() {}

    static String key(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, 0).getString("stremio_key", "");
    }

    static List<Item> continueWatching(Context c) { return read(c, "continue"); }

    static List<Item> popular(Context c) { return read(c, "popular"); }

    static void refresh(Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        if (done == null && now - refreshedAt < EVERY) return;
        refreshedAt = now;
        worker.execute(() -> {
            try {
                JSONObject out = new JSONObject();
                Set<String> library = new HashSet<>();
                List<Item> watching, popular;
                try { watching = library(c, library); } catch (Exception e) { watching = continueWatching(c); }
                try { popular = popular(library); } catch (Exception e) { popular = new ArrayList<>(); }
                if (popular.isEmpty()) popular = popular(c);
                out.put("continue", items(watching));
                out.put("popular", items(popular));
                File f = new File(dir(c), "shelf.json"), tmp = new File(dir(c), "shelf.json.part");
                try (OutputStream o = new FileOutputStream(tmp)) { o.write(out.toString().getBytes("UTF-8")); }
                tmp.renameTo(f);
            } catch (Throwable ignored) {
            }
            if (done != null) ui.post(done);
        });
    }

    private static List<Item> library(Context c, Set<String> ids) throws Exception {
        List<Item> out = new ArrayList<>();
        String key = key(c);
        if (key.isEmpty()) return out;
        JSONObject req = new JSONObject().put("authKey", key).put("collection", "libraryItem")
                .put("ids", new JSONArray()).put("all", true);
        JSONArray all = new JSONObject(post(API + "datastoreGet", req)).getJSONArray("result");
        List<JSONObject> watching = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.getJSONObject(i);
            boolean removed = it.optBoolean("removed"), temp = it.optBoolean("temp");
            if (!removed) ids.add(it.optString("_id"));
            JSONObject st = it.optJSONObject("state");
            if (st == null || "other".equals(it.optString("type")) || (removed && !temp)) continue;
            if (st.optLong("timeOffset") > 0) watching.add(it);
        }
        Collections.sort(watching, (a, b) -> last(b).compareTo(last(a)));
        for (JSONObject it : watching.subList(0, Math.min(MAX, watching.size()))) {
            JSONObject st = it.getJSONObject("state");
            Item m = new Item();
            m.id = it.optString("_id");
            m.type = it.optString("type");
            m.name = it.optString("name");
            m.poster = medium(it.optString("poster", null));
            String video = st.isNull("video_id") ? null : st.optString("video_id", null);
            m.episode = Config.episode(m.id, video);
            m.under = m.episode != null ? m.episode : Config.timeLeft(st.optLong("timeOffset"), st.optLong("duration"));
            m.progress = Config.progress(st.optLong("timeOffset"), st.optLong("duration"));
            m.link = Config.stremioLink(m.type, m.id, video);
            if (m.id.startsWith("tt")) try {
                details(m, new JSONObject(OnlinePictures.text(CINEMETA + "meta/" + m.type + "/" + m.id + ".json")).getJSONObject("meta"));
            } catch (Exception ignored) {}
            out.add(m);
        }
        return out;
    }

    private static String last(JSONObject it) {
        JSONObject st = it.optJSONObject("state");
        String w = st == null ? "" : st.optString("lastWatched", "");
        return w.isEmpty() || "null".equals(w) ? it.optString("_mtime", "") : w;
    }

    private static List<Item> popular(Set<String> library) {
        Map<String, JSONObject> metas = new LinkedHashMap<>();
        List<List<String>> lists = new ArrayList<>();
        for (String type : new String[] {"movie", "series"}) {
            List<String> ids = new ArrayList<>();
            try {
                JSONArray a = new JSONObject(OnlinePictures.text(CINEMETA + "catalog/" + type + "/top.json")).getJSONArray("metas");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject m = a.getJSONObject(i);
                    ids.add(m.optString("id"));
                    metas.put(m.optString("id"), m);
                }
            } catch (Exception ignored) {}
            lists.add(ids);
        }
        List<Item> out = new ArrayList<>();
        for (String id : Config.mix(lists.get(0), lists.get(1), library, MAX)) {
            JSONObject meta = metas.get(id);
            Item m = new Item();
            m.id = id;
            m.type = meta.optString("type");
            m.name = meta.optString("name");
            m.poster = medium(meta.optString("poster", null));
            m.link = Config.stremioLink(m.type, id, null);
            details(m, meta);
            m.under = m.year;
            out.add(m);
        }
        return out;
    }

    private static void details(Item m, JSONObject meta) {
        if (m.poster == null) m.poster = medium(meta.optString("poster", null));
        m.background = meta.optString("background", null);
        m.logo = meta.optString("logo", null);
        m.description = meta.optString("description", null);
        m.rating = meta.optString("imdbRating", null);
        m.year = meta.optString("releaseInfo", null);
        JSONArray g = meta.optJSONArray("genres");
        if (g != null) {
            List<String> two = new ArrayList<>();
            for (int i = 0; i < g.length() && i < 2; i++) two.add(g.optString(i));
            m.genres = android.text.TextUtils.join(" · ", two);
        }
    }

    private static String medium(String poster) {
        return poster == null ? null : poster.replace("/poster/small/", "/poster/medium/");
    }

    private static JSONArray items(List<Item> list) throws Exception {
        JSONArray a = new JSONArray();
        for (Item m : list) {
            JSONObject o = new JSONObject();
            for (java.lang.reflect.Field f : Item.class.getDeclaredFields())
                if (f.getType() == String.class && f.get(m) != null) o.put(f.getName(), f.get(m));
            o.put("progress", m.progress);
            a.put(o);
        }
        return a;
    }

    private static List<Item> read(Context c, String row) {
        List<Item> out = new ArrayList<>();
        try (InputStream in = new java.io.FileInputStream(new File(dir(c), "shelf.json"))) {
            JSONArray a = new JSONObject(new String(OnlinePictures.read(in), "UTF-8")).getJSONArray(row);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item m = new Item();
                for (java.lang.reflect.Field f : Item.class.getDeclaredFields())
                    if (f.getType() == String.class && o.has(f.getName())) f.set(m, o.getString(f.getName()));
                m.progress = (float) o.optDouble("progress", -1);
                out.add(m);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static Bitmap image(Context c, String url, int w, int h, boolean fit) {
        if (url == null || url.isEmpty()) return null;
        File dir = MainActivity.folder(c, "stremio/images");
        File f = new File(dir, Integer.toHexString(url.hashCode()) + "-" + url.length());
        if (!f.exists()) try {
            File tmp = new File(dir, f.getName() + ".part");
            HttpURLConnection h2 = OnlinePictures.open(url);
            try (InputStream in = h2.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } finally {
                h2.disconnect();
            }
            if (!tmp.renameTo(f)) return null;
            prune(dir);
        } catch (Exception e) {
            return null;
        }
        f.setLastModified(System.currentTimeMillis());
        if (!fit) return MainActivity.crop(f, w, h);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static void prune(File dir) {
        File[] all = dir.listFiles();
        if (all == null || all.length <= KEEP_IMAGES) return;
        Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP_IMAGES; i < all.length; i++) all[i].delete();
    }

    private static File dir(Context c) {
        return MainActivity.folder(c, "stremio");
    }

    private static String post(String url, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new java.net.URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        try {
            try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
            try (InputStream in = h.getInputStream()) { return new String(OnlinePictures.read(in), "UTF-8"); }
        } finally {
            h.disconnect();
        }
    }
}
