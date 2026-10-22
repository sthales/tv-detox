package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Pictures for the screensaver from three public sources that need no key: Places (Bing's picture of
// the day, 4K), Earth (NASA EPIC, the whole planet) and Space (NASA's image library). Each source is
// checked at most once a day, only when the home or the screensaver starts; a failed check is tried
// again an hour later. The newest 8 of each are kept next to a .txt with their caption.
final class OnlinePictures {
    static final String KEY = "online:";
    static final String[] SOURCES = {"places", "earth", "space"};
    private static final int KEEP = 8;
    private static final long DAY = 24 * 3600 * 1000L, RETRY = 3600 * 1000L;
    private static final String[] SPACE_TERMS = {"nebula", "galaxy", "hubble", "webb", "earth from iss", "aurora"};
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    private OnlinePictures() {}

    static boolean enabled(Context c, String src) {
        return c.getSharedPreferences(MainActivity.PREFS, 0).getBoolean("src_" + src, true);
    }

    static File dir(Context c, String src) {
        return MainActivity.folder(c, "online/" + src);
    }

    static File file(Context c, String key) {
        String rest = key.substring(KEY.length());
        int slash = rest.indexOf('/');
        return new File(dir(c, rest.substring(0, slash)), rest.substring(slash + 1));
    }

    // The downloaded pictures of the switched-on sources, as screensaver slides.
    static List<Showcase.Slide> slides(Context c) {
        List<Showcase.Slide> out = new ArrayList<>();
        for (String src : SOURCES) {
            if (!enabled(c, src)) continue;
            String[] names = dir(c, src).list();
            if (names == null) continue;
            Arrays.sort(names);
            for (String n : names) {
                if (!Config.isImage(n)) continue;
                out.add(new Showcase.Slide(KEY + src + "/" + n, caption(new File(dir(c, src), n)), src.equals("earth")));
            }
        }
        return out;
    }

    private static String caption(File picture) {
        File t = new File(picture.getPath() + ".txt");
        try (InputStream in = new java.io.FileInputStream(t)) {
            return new String(read(in), "UTF-8").trim();
        } catch (Exception e) {
            return null;
        }
    }

    // Checks the sources that are due, in the background. Safe to call often.
    static void refresh(final Context ctx) {
        final Context c = ctx.getApplicationContext();
        worker.execute(() -> {
            SharedPreferences p = c.getSharedPreferences(MainActivity.PREFS, 0);
            for (String src : SOURCES) {
                if (!enabled(c, src)) continue;
                long now = System.currentTimeMillis();
                if (now - p.getLong("online_" + src + "_at", 0) < DAY) continue;
                if (now - p.getLong("online_" + src + "_try", 0) < RETRY) continue;
                p.edit().putLong("online_" + src + "_try", now).apply();
                try {
                    int got = src.equals("places") ? places(c) : src.equals("earth") ? earth(c) : space(c);
                    if (got > 0) p.edit().putLong("online_" + src + "_at", now).apply();
                    prune(dir(c, src));
                } catch (Throwable ignored) {
                    // Offline or the source changed: keep what's there and try again in an hour.
                }
            }
        });
    }

    // Bing: the last 8 pictures of the day, UHD, with their place names.
    private static int places(Context c) throws Exception {
        JSONArray images = new JSONObject(text("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=pt-BR"))
                .getJSONArray("images");
        int got = 0;
        for (int i = 0; i < images.length(); i++) {
            JSONObject im = images.getJSONObject(i);
            String name = "bing-" + im.optString("startdate", String.valueOf(i)) + ".jpg";
            got += save(dir(c, "places"), name, "https://www.bing.com" + im.getString("urlbase") + "_UHD.jpg",
                    Config.bingTitle(im.optString("copyright")) + " · Bing");
        }
        return got;
    }

    // NASA EPIC: the latest day's natural-colour pictures of the whole Earth.
    private static int earth(Context c) throws Exception {
        JSONArray days = new JSONArray(text("https://epic.gsfc.nasa.gov/api/natural"));
        int got = 0;
        for (int i = 0; i < days.length() && i < KEEP; i++) {
            JSONObject d = days.getJSONObject(i);
            String image = d.getString("image"), date = d.getString("date");
            String ymd = date.substring(0, 10).replace('-', '/');
            got += save(dir(c, "earth"), image + ".jpg",
                    "https://epic.gsfc.nasa.gov/archive/natural/" + ymd + "/jpg/" + image + ".jpg",
                    "Earth from space · " + date.substring(0, 16) + " UTC · NASA");
        }
        return got;
    }

    // NASA's image library: today's search term (nebula, galaxy, ...), large versions.
    private static int space(Context c) throws Exception {
        String term = SPACE_TERMS[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % SPACE_TERMS.length];
        JSONArray items = new JSONObject(text("https://images-api.nasa.gov/search?media_type=image&page_size=40&q="
                + java.net.URLEncoder.encode(term, "UTF-8"))).getJSONObject("collection").getJSONArray("items");
        int got = 0;
        for (int i = 0; i < items.length() && got < KEEP; i++) {
            JSONObject data = items.getJSONObject(i).getJSONArray("data").getJSONObject(0);
            String id = data.getString("nasa_id");
            if (!id.matches("[A-Za-z0-9._-]+")) continue;
            got += save(dir(c, "space"), id + ".jpg", "https://images-assets.nasa.gov/image/" + id + "/" + id + "~large.jpg",
                    data.optString("title", "Space") + " · NASA");
        }
        return got;
    }

    // Downloads url into dir/name (through a temp file, so a cut download never shows) with its caption.
    private static int save(File dir, String name, String url, String caption) throws Exception {
        File target = new File(dir, name);
        if (target.exists()) return 1;
        File tmp = new File(dir, name + ".part");
        HttpURLConnection h = open(url);
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally {
            h.disconnect();
        }
        if (tmp.length() < 10 * 1024 || !tmp.renameTo(target)) {
            tmp.delete();
            return 0;
        }
        try (OutputStream out = new FileOutputStream(new File(dir, name + ".txt"))) {
            out.write(caption.getBytes("UTF-8"));
        }
        return 1;
    }

    // Keeps the newest KEEP pictures (and their captions).
    private static void prune(File dir) {
        File[] pics = dir.listFiles((d, n) -> Config.isImage(n));
        if (pics == null || pics.length <= KEEP) return;
        Arrays.sort(pics, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < pics.length; i++) {
            pics[i].delete();
            new File(pics[i].getPath() + ".txt").delete();
        }
    }

    private static String text(String url) throws Exception {
        HttpURLConnection h = open(url);
        try (InputStream in = h.getInputStream()) {
            return new String(read(in), "UTF-8");
        } finally {
            h.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "tv-detox-home/1.0 (screensaver)");
        h.setInstanceFollowRedirects(true);
        if (h.getResponseCode() / 100 != 2) throw new java.io.IOException("HTTP " + h.getResponseCode() + " " + url);
        return h;
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
