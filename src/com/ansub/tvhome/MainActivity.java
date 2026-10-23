package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 6, MAX_INPUTS = 4;

    private static final String CHIP = "chip";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int MARGIN = 96, GAP = 36, RADIUS = 22;
    // Apple TV style: the apps sit low and the second row only peeks at the bottom edge (40 px of
    // 1080) until Down reveals it.
    private static final int ROW1_TOP = 816, ROW_STEP = 224;
    private static final int GRID_MIN_TOP = 400; // more rows push the grid up, but not into the chip
    private static final int ICON_TOP = 76; // the chip's top

    // The Settings tile opens a small menu: the home screen's own settings, then the TV's.
    private static final String[] SETTINGS_ITEMS = {"Wallpaper", "Brightness", "TV settings"};
    // Android TV 11 and later (Google TV too) has no Display page, but its "Display & Sound" page holds Picture.
    private static final String[] TV_DISPLAY_SOUND = {"com.android.tv.settings",
            "com.android.tv.settings.device.displaysound.DisplaySoundActivity"};
    // Wallpaper pictures, pushed by wallpaper.sh into the app's own folder (so it can delete them).
    // The saved wallpaper is IMAGE + file name.
    private static final String IMAGE = "image:";
    // Wallpapers, saved by name: a gradient top to bottom, then a glow from the top left and one from
    // the bottom right. Dark ones first, then bright ones that get darker towards the bottom, so the
    // white labels stay easy to read. Only colours, no images, so every one costs the same as Dusk,
    // the default.
    private static final String[] WALLPAPER_NAMES = {"Dusk", "Midnight", "Plum", "Forest", "Graphite", "Ocean", "Ember",
            "Sunset", "Lagoon", "Aurora", "Coral", "Iris", "Meadow", "Gold"};
    private static final int[][] WALLPAPERS = {
        {0xFF15100F, 0xFF0A0809, 0xFF050405, 0x36FF8048, 0x2EAA3C8C},
        {0xFF0E1220, 0xFF080A13, 0xFF040509, 0x384A6CFF, 0x2E5A3CC8},
        {0xFF170E19, 0xFF0C070D, 0xFF050306, 0x38C050B4, 0x2E6440D2},
        {0xFF0C150F, 0xFF070C09, 0xFF030604, 0x3050B478, 0x28289682},
        {0xFF151617, 0xFF0B0C0D, 0xFF050506, 0x1CFFFFFF, 0x16A0B4C8},
        {0xFF0A1517, 0xFF060C0E, 0xFF030607, 0x3426B4C8, 0x2E2A64C0},
        {0xFF190D08, 0xFF0D0605, 0xFF060303, 0x40FF5A24, 0x32C8283C},
        {0xFF8C3A5E, 0xFF5E2348, 0xFF22101E, 0xA0FF8A4C, 0x80E0457A},
        {0xFF14588E, 0xFF15366E, 0xFF0A1632, 0x9030C8E0, 0x807A4CE0},
        {0xFF0F4048, 0xFF0F2E3A, 0xFF08141C, 0x9038E0A0, 0x90A050F0},
        {0xFFC0604E, 0xFF8A3456, 0xFF2E1230, 0x90FFB070, 0x70E04090},
        {0xFF5A3CAE, 0xFF3A2A84, 0xFF16123A, 0x90C080FF, 0x804A7CFF},
        {0xFF2E6E4C, 0xFF1C4A40, 0xFF0A201E, 0x90A8E070, 0x6030A8A8},
        {0xFFB07624, 0xFF86401E, 0xFF2A1410, 0x90FFD060, 0x70FF5040},
    };
    private float scale = 1f;

    static Typeface regular, semibold;

    private ScrollView scroller;

    // The app's own folders on shared storage: no permission needed, and the app may delete in them.
    // Scripts push into /sdcard/Android/data/com.ansub.tvhome/files/{wallpapers,banners}.
    private File folder(String name) {
        return folder(this, name);
    }

    static File folder(Context c, String name) {
        File base = c.getExternalFilesDir(null);
        File f = new File(base != null ? base : c.getFilesDir(), name);
        f.mkdirs();
        return f;
    }

    private File images() { return folder("wallpapers"); }
    private File banners() { return folder("banners"); }
    // The last two pictures shown, so moving back and forth in the Wallpaper list doesn't decode again.
    private final android.util.LruCache<String, Bitmap> decoded = new android.util.LruCache<>(2);
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingPreview;
    // Pictures are decoded here, off the main thread: a 5K PNG takes seconds on this chip.
    private static final java.util.concurrent.ExecutorService decoder = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.Map<String, Drawable> thumbs = new java.util.HashMap<>();
    private String previewing; // the wallpaper the list is on, so a late decode for another is dropped
    // Back on the home hides the page and leaves the wallpaper and a small clock; any key brings it back.
    private boolean ambient;
    private Showcase showcase; // the screensaver view, while Back has it up
    private LinearLayout npBox;
    private ImageView npArt;
    private TextView npText;
    private LineIcon npState;
    private final Runnable npRefresh = this::refreshNowPlaying;
    private FrameLayout root; // the page, inside a vertical scroller
    private int tileW, tileH, gridTop, bottomRowTop; // app grid geometry, set by buildTiles
    private final List<View> tiles = new ArrayList<>();
    // Apps with a custom banner: drawn on glass, so a logo with a transparent background floats on it.
    private final java.util.Set<String> glassBanners = new java.util.HashSet<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open
    // The tile OK went down on. An app that closes on OK's key-down (Netflix's "Exit") hands the matching
    // key-up to the tile still focused here, so a key-up only opens the app it went down on (#30).
    private String okDownKey;

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private TextView alertsTile; // Control Center's Alerts tile while the panel is open
    private Dialog controlCenter;
    private final Runnable bellChanged = this::refreshBell;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));
        showWallpaper(wallpaper());

        shownLocale = java.util.Locale.getDefault();
        openFrom(getIntent());

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(MARGIN), px(ROW1_TOP - 64), -2, -2));

        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        NotifService.onChange = bellChanged;

    }

    @Override
    protected void onDestroy() {
        if (NotifService.onChange == bellChanged) NotifService.onChange = null;
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFrom(intent);
        if (saveConfig(intent)) {
            showWallpaper(wallpaper());
            buildTiles();
        }
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String keys = intent == null ? null : intent.getStringExtra("keys");
        String wall = intent == null ? null : intent.getStringExtra("wallpaper");
        String hidden = intent == null ? null : intent.getStringExtra("hidden_inputs");
        String rotate = intent == null ? null : intent.getStringExtra("rotate");
        String name = intent == null ? null : intent.getStringExtra("name");
        String avatar = intent == null ? null : intent.getStringExtra("avatar"); // avatar.sh: redraw only
        String rotation = intent == null ? null : intent.getStringExtra("rotation");
        // banner.sh only needs the tiles redrawn; the picture itself is the file.
        String banner = intent == null ? null : intent.getStringExtra("banner");
        String settings = intent == null ? null : intent.getStringExtra("settings");
        String every = intent == null ? null : intent.getStringExtra("showcase_every");
        String showcase = intent == null ? null : intent.getStringExtra("showcase");
        String sources = intent == null ? null : intent.getStringExtra("sources");
        String drift = intent == null ? null : intent.getStringExtra("saver_drift");
        String slide = intent == null ? null : intent.getStringExtra("saver_slide");
        String sharp = intent == null ? null : intent.getStringExtra("saver_4k");
        String tvSaver = intent == null ? null : intent.getStringExtra("tv_saver");
        String saverSound = intent == null ? null : intent.getStringExtra("saver_sound");
        String saverVolume = intent == null ? null : intent.getStringExtra("saver_volume");
        String lower = intent == null ? null : intent.getStringExtra("lower");
        if (apps == null && quick == null && keys == null && wall == null && hidden == null && banner == null
                && rotate == null && rotation == null && name == null && avatar == null && settings == null
                && every == null && showcase == null && sources == null && drift == null && slide == null && sharp == null
                && tvSaver == null && lower == null && saverSound == null && saverVolume == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        if ("export".equals(settings)) {
            try { Backup.export(this); } catch (Exception ignored) {}
        } else if ("import".equals(settings)) {
            try { Backup.load(this); } catch (Exception ignored) {}
            recreate();
        }
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (every != null) e.putString("showcase_every", every.trim());
        if (showcase != null) e.putString("showcase", Config.joinSet(Config.splitSet(showcase)));
        if (sources != null) {
            java.util.Set<String> on = Config.splitSet(sources);
            for (String src : OnlinePictures.SOURCES) e.putBoolean("src_" + src, on.contains("all") || on.contains(src));
        }
        if (drift != null) e.putBoolean("saver_drift", drift.trim().equals("on"));
        if (slide != null) e.putBoolean("saver_slide", slide.trim().equals("on"));
        if (sharp != null) e.putBoolean("saver_4k", sharp.trim().equals("on"));
        if (tvSaver != null) e.putBoolean("tv_saver", tvSaver.trim().equals("on"));
        if (lower != null) e.putString("lower", Config.joinSet(Config.splitSet(lower)));
        if (saverSound != null) e.putString("saver_sound", saverSound.trim().equals("off") ? "" : saverSound.trim());
        if (saverVolume != null) try { e.putInt("saver_volume", Integer.parseInt(saverVolume.trim())); } catch (NumberFormatException ignored) {}
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        if (keys != null) e.putString("keys", keys.trim());
        if (wall != null) e.putString("wallpaper", wall.trim());
        if (rotate != null) e.putString("rotate", rotate.trim()).putLong("rotate_at", System.currentTimeMillis());
        if (rotation != null) e.putString("rotation", Config.joinSet(Config.splitSet(rotation)));
        if (name != null) e.putString("name", name.trim());
        if (hidden != null) {
            if (hidden.trim().equalsIgnoreCase("none")) e.remove("hidden_inputs");
            else e.putString("hidden_inputs", Config.joinSet(Config.splitSet(hidden)));
        }
        e.commit(); // not apply(): a script may restart the TV or the app right after sending this
        if (tvSaver != null) applySaver(this);
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted)
            if (!p.isEmpty() && !out.contains(p) && (isInput(p) ? input(p) != null : launchIntent(pm, p) != null)) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        healServices();
        applySaver(this);
        NowPlaying.listen(this, npRefresh);
        refreshNowPlaying();
        rotateIfDue();
        OnlinePictures.refresh(this);
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    // At boot this TCL wipes the accessibility setting and its boot manager won't bind the bell's
    // listener. Once the home is running both work again, so it turns them back on here. Writing the
    // setting needs WRITE_SECURE_SETTINGS, which extras.sh grants; without it this does nothing.
    private void healServices() {
        if (NotifService.live == null && android.os.Build.VERSION.SDK_INT >= 24) try {
            android.service.notification.NotificationListenerService.requestRebind(
                    new android.content.ComponentName(this, NotifService.class));
        } catch (Exception ignored) {}
        String keys = getSharedPreferences(PREFS, 0).getString("keys", null);
        if (keys == null || keys.trim().isEmpty()) return;
        long updated = 0;
        try { updated = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime; } catch (Exception ignored) {}
        SharedPreferences heal = getSharedPreferences("heal", 0);
        boolean fresh = heal.getLong("keys_rebound_for", 0) != updated;
        if (KeyService.live != null && !fresh) return;
        heal.edit().putLong("keys_rebound_for", updated).apply();
        rebindKeys();
    }

    private boolean rebinding;

    private void rebindKeys() {
        if (rebinding) return;
        final android.content.ComponentName me = new android.content.ComponentName(this, KeyService.class);
        final android.content.ContentResolver cr = getContentResolver();
        final String name = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
        try {
            StringBuilder b = new StringBuilder();
            String on = Settings.Secure.getString(cr, name);
            if (on != null) for (String s : on.split(":")) {
                android.content.ComponentName c = android.content.ComponentName.unflattenFromString(s);
                if (c != null && !c.equals(me)) b.append(b.length() == 0 ? "" : ":").append(s);
            }
            final String others = b.toString();
            Settings.Secure.putString(cr, name, others.isEmpty() ? null : others);
            rebinding = true;
            ui.postDelayed(() -> {
                rebinding = false;
                try {
                    Settings.Secure.putString(cr, name, (others.isEmpty() ? "" : others + ":") + me.flattenToShortString());
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                } catch (SecurityException ignored) {}
            }, 1500);
        } catch (SecurityException ignored) {}
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            refreshApps();
            refreshBell();
        }
        else okDownKey = null; // an OK still held finishes in the window that took over
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() {
        if (!ambient) showAmbient(true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!ambient) return super.dispatchKeyEvent(event);
        if (showcase != null && showcase.key(event)) return true;
        if (event.getAction() == KeyEvent.ACTION_UP) showAmbient(false);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(rotateTick);
        showAmbient(false);
        NowPlaying.unlisten(this, npRefresh);
    }

    private void showAmbient(boolean on) {
        if (ambient == on || scroller == null) return;
        ambient = on;
        scroller.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        if (on) {
            showcase = new Showcase(this, slideSource(this), regular, semibold);
            addContentView(showcase, new FrameLayout.LayoutParams(-1, -1));
            showcase.start();
            refreshNowPlaying();
        } else if (showcase != null) {
            showcase.stop();
            ((ViewGroup) showcase.getParent()).removeView(showcase);
            showcase = null;
        }
        if (!on && getCurrentFocus() == null) {
            View back = tile(focusedKey);
            if (back == null && !tiles.isEmpty()) back = tiles.get(0);
            if (back != null) back.requestFocus();
        }
    }

    // The screensaver's pictures: wallpapers marked ★ (pref "showcase"), then the online ones that are
    // switched on, else the current wallpaper. Used by Back on the home and by the Dream.
    static Showcase.Source slideSource(final Context c) {
        return new Showcase.Source() {
            @Override public List<Showcase.Slide> slides() {
                SharedPreferences p = c.getSharedPreferences(PREFS, 0);
                java.util.Set<String> chosen = Config.splitSet(p.getString("showcase", null));
                List<Showcase.Slide> out = new ArrayList<>();
                for (String n : wallpaperNames(c)) if (chosen.contains(n)) out.add(new Showcase.Slide(n, null, false));
                out.addAll(OnlinePictures.slides(c));
                if (out.isEmpty()) out.add(new Showcase.Slide(p.getString("wallpaper", WALLPAPER_NAMES[0]), null, false));
                return out;
            }
            @Override public Bitmap load(Showcase.Slide s, int w, int h) {
                if (s.key.startsWith(IMAGE)) return crop(new File(folder(c, "wallpapers"), s.key.substring(IMAGE.length())), w, h);
                if (s.key.startsWith(OnlinePictures.KEY)) {
                    File f = OnlinePictures.file(c, s.key);
                    return s.fit ? fitted(f, w, h) : crop(f, w, h);
                }
                int i = Arrays.asList(WALLPAPER_NAMES).indexOf(s.key);
                if (i < 0) return null;
                Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                flatten(gradient(WALLPAPERS[i], w / 1920f), b);
                return b;
            }
            @Override public long interval() {
                long ms = Config.intervalMillis(c.getSharedPreferences(PREFS, 0).getString("showcase_every", "5m"));
                return ms > 0 ? ms : 5 * 60 * 1000L;
            }
            @Override public boolean drift() { return c.getSharedPreferences(PREFS, 0).getBoolean("saver_drift", false); }
            @Override public boolean slide() { return c.getSharedPreferences(PREFS, 0).getBoolean("saver_slide", false); }
            @Override public boolean sharp4k() { return c.getSharedPreferences(PREFS, 0).getBoolean("saver_4k", true); }
        };
    }

    private static final String[] SAVER_EVERY = {"1m", "5m", "15m", "30m"};
    private static final String[] SAVER_EVERY_LABELS = {"1 min", "5 min", "15 min", "30 min"};
    private static final String[] SOURCE_LABELS = {"Places", "Earth", "Space"};

    private static final int[] SAVER_VOLUMES = {10, 20, 30};

    private void showSaverSettings(final int row) {
        final SharedPreferences p = getSharedPreferences(PREFS, 0);
        String on = Strings.t("On"), off = Strings.t("Off");
        int every = Math.max(0, Arrays.asList(SAVER_EVERY).indexOf(p.getString("showcase_every", "5m")));
        final String[] sounds = Ambience.names(this);
        final int sound = Arrays.asList(sounds).indexOf(p.getString("saver_sound", ""));
        int vol = 0;
        for (int i = 0; i < SAVER_VOLUMES.length; i++) if (SAVER_VOLUMES[i] == p.getInt("saver_volume", 20)) vol = i;
        final int volume = vol;
        final List<String> rows = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        rows.add(Strings.t("Start now"));
        actions.add(null);
        rows.add(Strings.t("Change picture") + ": " + String.format(Strings.t("every %s"), Strings.t(SAVER_EVERY_LABELS[every])));
        actions.add(() -> p.edit().putString("showcase_every", SAVER_EVERY[(every + 1) % SAVER_EVERY.length]).commit());
        rows.add(Strings.t("Your pictures") + " \u2605: " + Config.splitSet(p.getString("showcase", null)).size());
        actions.add(null);
        for (int i = 0; i < OnlinePictures.SOURCES.length; i++) {
            final String src = OnlinePictures.SOURCES[i];
            rows.add(Strings.t(SOURCE_LABELS[i]) + ": " + (OnlinePictures.enabled(this, src) ? on : off));
            actions.add(() -> p.edit().putBoolean("src_" + src, !OnlinePictures.enabled(this, src)).commit());
        }
        rows.add(Strings.t("Sound") + ": " + (sound < 0 ? off : Ambience.label(sounds[sound])));
        actions.add(() -> p.edit().putString("saver_sound", sound + 1 < sounds.length ? sounds[sound + 1] : "").commit());
        rows.add(Strings.t("Volume") + ": " + SAVER_VOLUMES[volume] + "%");
        actions.add(() -> p.edit().putInt("saver_volume", SAVER_VOLUMES[(volume + 1) % SAVER_VOLUMES.length]).commit());
        rows.add(Strings.t("Drift") + ": " + (p.getBoolean("saver_drift", false) ? on : off));
        actions.add(() -> p.edit().putBoolean("saver_drift", !p.getBoolean("saver_drift", false)).putBoolean("saver_slide", false).commit());
        rows.add(Strings.t("Slide") + ": " + (p.getBoolean("saver_slide", false) ? on : off));
        actions.add(() -> p.edit().putBoolean("saver_slide", !p.getBoolean("saver_slide", false)).putBoolean("saver_drift", false).commit());
        rows.add("4K: " + (p.getBoolean("saver_4k", true) ? on : off));
        actions.add(() -> p.edit().putBoolean("saver_4k", !p.getBoolean("saver_4k", true)).commit());
        rows.add(Strings.t("Use as TV screensaver") + ": " + (p.getBoolean("tv_saver", true) ? on : off));
        actions.add(() -> p.edit().putBoolean("tv_saver", !p.getBoolean("tv_saver", true)).commit());
        panel(Strings.t("Screensaver"), rows, null, 0, row, which -> {}, which -> {
            if (which == 0) {
                showAmbient(true);
                return;
            }
            if (which == 2) {
                showWallpaperScreen(null);
                return;
            }
            actions.get(which).run();
            applySaver(this);
            OnlinePictures.refresh(this);
            showSaverSettings(which);
        }, null);
    }

    private static final String GOOGLE_SAVER = "com.google.android.apps.tv.dreamx/.service.Backdrop";

    static void applySaver(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        String ours = new android.content.ComponentName(c, Dream.class).flattenToShortString();
        android.content.ContentResolver r = c.getContentResolver();
        try {
            String now = Settings.Secure.getString(r, "screensaver_components");
            if (p.getBoolean("tv_saver", true)) {
                if (ours.equals(now)) return;
                if (now != null && !now.isEmpty()) p.edit().putString("tv_saver_prev", now).commit();
                Settings.Secure.putString(r, "screensaver_components", ours);
            } else if (ours.equals(now)) {
                Settings.Secure.putString(r, "screensaver_components", p.getString("tv_saver_prev", GOOGLE_SAVER));
            }
        } catch (Exception ignored) {
        }
    }

    // Gradients first, then the pictures in the wallpapers folder by name.
    static List<String> wallpaperNames(Context c) {
        List<String> out = new ArrayList<>(Arrays.asList(WALLPAPER_NAMES));
        String[] files = folder(c, "wallpapers").list();
        if (files != null) {
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            for (String f : files) if (Config.isImage(f)) out.add(IMAGE + f);
        }
        return out;
    }

    // The whole picture scaled to fit w x h, centred on black (for square pictures like NASA's Earth).
    static Bitmap fitted(File f, int w, int h) {
        Bitmap src = crop(f, Math.min(w, h), Math.min(w, h));
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.BLACK);
        float k = Math.min((float) w / src.getWidth(), (float) h / src.getHeight());
        cv.translate((w - src.getWidth() * k) / 2, (h - src.getHeight() * k) / 2);
        cv.scale(k, k);
        cv.drawBitmap(src, 0, 0, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    // Static gradient, drawn once by the window: no extra view, no animation.
    private Drawable background(int[] c) {
        return gradient(c, scale);
    }

    static Drawable gradient(int[] c, float k) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {c[0], c[1], c[2]});
        GradientDrawable top = radial(c[3], 0.08f, -0.12f, Math.round(1250 * k));
        GradientDrawable bottom = radial(c[4], 1.0f, 1.1f, Math.round(1000 * k));
        for (GradientDrawable g : new GradientDrawable[] {base, top, bottom}) g.setDither(true);
        return new LayerDrawable(new Drawable[] {base, top, bottom});
    }

    // The saved wallpaper: a gradient's name, or "image:" and a file in images(). Dusk if none is saved.
    private String wallpaper() {
        return getSharedPreferences(PREFS, 0).getString("wallpaper", WALLPAPER_NAMES[0]);
    }

    // Shows a wallpaper by name. An image that can't be read falls back to Dusk.
    private void showWallpaper(String name) {
        Bitmap b = wallpaperBitmap(name);
        if (b == null) {
            name = WALLPAPER_NAMES[0];
            b = wallpaperBitmap(name);
        }
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), b));
        if (scroller != null) scroller.setContentDescription("wallpaper:" + name);
    }

    // The wallpaper drawn once into one screen-size picture: gradient layers, or the picture with its
    // fade. This GPU redraws full-screen gradients on every frame of a focus animation (73 ms a frame
    // for Dusk), but copies one picture easily. Null for a picture that can't be read. Safe off the
    // main thread.
    private Bitmap wallpaperBitmap(String name) {
        String key = name.startsWith(IMAGE) ? imageKey(name) : name;
        Bitmap b = decoded.get(key);
        if (b != null) return b;
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        if (name.startsWith(IMAGE)) {
            b = crop(new File(images(), name.substring(IMAGE.length())), w, h);
            if (b == null) return null;
            GradientDrawable clock = radial(0x8C000000, 0f, 0f, px(1100));
            GradientDrawable apps = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {Color.TRANSPARENT, Color.TRANSPARENT, 0x99000000});
            for (GradientDrawable g : new GradientDrawable[] {clock, apps}) g.setDither(true);
            flatten(new LayerDrawable(new Drawable[] {clock, apps}), b);
        } else {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            flatten(background(WALLPAPERS[Math.max(0, Arrays.asList(WALLPAPER_NAMES).indexOf(name))]), b);
        }
        decoded.put(key, b);
        return b;
    }

    private static void flatten(Drawable d, Bitmap onto) {
        d.setBounds(0, 0, onto.getWidth(), onto.getHeight());
        d.draw(new Canvas(onto));
    }

    private List<String> wallpapers() {
        return wallpaperNames(this);
    }

    // Decodes f center-cropped to w x h. Big pictures are sampled down while decoding, so a photo
    // from a phone never needs more memory than about one screen.
    private static Bitmap crop(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.translate((w - src.getWidth() * s) / 2, (h - src.getHeight() * s) / 2);
            c.scale(s, s);
            c.drawBitmap(src, 0, 0, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    // Like crop, for small pictures that must stay crisp (tile logos): decoded at no less than twice
    // the size, then halved in smooth steps instead of skipping pixels, which leaves logos jagged.
    private static Bitmap sharp(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 4 * w && o.outHeight / (o.inSampleSize * 2) >= 4 * h) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            while (s < 0.5f) {
                Bitmap half = Bitmap.createScaledBitmap(src, Math.max(1, src.getWidth() / 2), Math.max(1, src.getHeight() / 2), true);
                if (half != src) src.recycle();
                src = half;
                s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            }
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.translate((w - src.getWidth() * s) / 2, (h - src.getHeight() * s) / 2);
            c.scale(s, s);
            c.drawBitmap(src, 0, 0, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG
                    | android.graphics.Paint.ANTI_ALIAS_FLAG));
            src.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    // The Wallpaper screen: rotation interval chips and a grid of every wallpaper. Moving through the
    // grid previews each one behind the screen; OK on a thumbnail opens its menu right there (Set as
    // wallpaper, Add to / Remove from rotation, Delete). Back puts the saved one back. ✓ marks the
    // current one, ↻ the rotation.
    private Dialog wallScreen;
    private String wallSelected;

    private static final String[] ROTATE_LABELS = {"Off", "5 min", "10 min", "30 min", "1 hour", "4 hours", "12 hours", "24 hours", "1 week"};

    private int rotateChoice() {
        int i = Arrays.asList(Config.ROTATE_CHOICES).indexOf(getSharedPreferences(PREFS, 0).getString("rotate", "off"));
        return Math.max(0, i);
    }

    private java.util.Set<String> rotationSet() {
        return Config.splitSet(getSharedPreferences(PREFS, 0).getString("rotation", null));
    }

    private java.util.Set<String> showcaseSet() {
        return Config.splitSet(getSharedPreferences(PREFS, 0).getString("showcase", null));
    }

    private void showWallpaperScreen(String focus) {
        if (wallScreen != null) wallScreen.dismiss();
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        wallScreen = d;
        final List<String> names = wallpapers();
        wallSelected = focus != null && names.contains(focus) ? focus : wallpaper();

        FrameLayout frame = new FrameLayout(this);
        frame.setContentDescription("wallpaper-screen");
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xD9000000, 0x59000000, 0x59000000, 0xF2000000}));
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(MARGIN), px(56), px(MARGIN), px(44));
        frame.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(px(44), Color.WHITE, semibold);
        title.setText(Strings.t("Wallpaper"));
        col.addView(title);

        // Rotate: Off, 30 min … 1 week.
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(0, px(18), 0, px(24));
        TextView rl = text(px(24), 0xB3FFFFFF, regular);
        rl.setText(Strings.t("Rotate"));
        rl.setPadding(0, 0, px(20), 0);
        chips.addView(rl);
        final List<TextView> chipViews = new ArrayList<>();
        for (int i = 0; i < ROTATE_LABELS.length; i++) {
            final int which = i;
            TextView c = pill(Strings.t(ROTATE_LABELS[i]), px(22));
            c.setOnClickListener(v -> {
                getSharedPreferences(PREFS, 0).edit().putString("rotate", Config.ROTATE_CHOICES[which])
                        .putLong("rotate_at", System.currentTimeMillis()).apply();
                rotateIfDue();
                for (int k = 0; k < chipViews.size(); k++) markPill(chipViews.get(k), k == which, chipViews.get(k).hasFocus());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(12);
            chips.addView(c, lp);
            chipViews.add(c);
        }
        for (int k = 0; k < chipViews.size(); k++) markPill(chipViews.get(k), k == rotateChoice(), false);
        col.addView(chips);

        // The grid, five across, scrolling when there are more rows than fit.
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, px(16), 0, px(16));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        sv.addView(grid);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        final int across = 5, gap = px(28);
        final int tw = (getResources().getDisplayMetrics().widthPixels - 2 * px(MARGIN) - (across - 1) * gap) / across;
        final int th = tw * 9 / 16;
        final List<View> thumbViews = new ArrayList<>();
        final List<TextView> badges = new ArrayList<>();
        LinearLayout row = null;
        View focusView = null;

        final Runnable refresh = () -> {
            java.util.Set<String> rot = rotationSet(), star = showcaseSet();
            String current = wallpaper();
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i), mark = (n.equals(current) ? "\u2713 " : "") + (rot.contains(n) ? "\u21BB " : "")
                        + (star.contains(n) ? "\u2605" : "");
                badges.get(i).setText(mark.trim());
                badges.get(i).setVisibility(mark.trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
        };
        for (int i = 0; i < names.size(); i++) {
            if (i % across == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rp.topMargin = gap;
                grid.addView(row, rp);
            }
            final String n = names.get(i);
            TextView badge = text(px(22), Color.WHITE, semibold);
            View t = wallThumb(n, tw, th, badge, () -> {
                wallSelected = n;
                refresh.run();
                ui.removeCallbacks(pendingPreview);
                previewing = n;
                pendingPreview = () -> preview(n);
                ui.postDelayed(pendingPreview, 150);
            }, anchor -> wallpaperMenu(n, anchor, refresh));
            badges.add(badge);
            thumbViews.add(t);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(tw, -2);
            if (i % across > 0) cp.leftMargin = gap;
            row.addView(t, cp);
            if (n.equals(wallSelected)) focusView = t;
        }
        refresh.run();

        d.setContentView(frame);
        d.setOnDismissListener(di -> {
            ui.removeCallbacks(pendingPreview);
            previewing = null;
            if (wallScreen == d) {
                wallScreen = null;
                scroller.setVisibility(View.VISIBLE);
            }
            showWallpaper(wallpaper());
        });
        scroller.setVisibility(View.INVISIBLE); // only the previewed wallpaper behind the screen
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setDimAmount(0f);
        d.show();
        if (focusView != null) focusView.requestFocus();
    }

    // OK on a thumbnail: a small menu on it. Delete asks first and is only offered for pictures.
    private void wallpaperMenu(final String name, View anchor, final Runnable refresh) {
        final boolean picture = name.startsWith(IMAGE);
        final boolean in = rotationSet().contains(name);
        final boolean star = showcaseSet().contains(name);
        String rotItem = in ? "Remove from rotation" : "Add to rotation", saverItem = star ? "Remove from screensaver" : "Add to screensaver";
        String[] items = picture ? new String[] {"Set as wallpaper", rotItem, saverItem, "Delete"}
                : new String[] {"Set as wallpaper", rotItem, saverItem};
        dropdown(anchor, items, which -> {
            if (which == 0) {
                setWallpaperTo(name);
                refresh.run();
            } else if (which == 1) {
                java.util.Set<String> rot = rotationSet();
                if (!rot.remove(name)) rot.add(name);
                getSharedPreferences(PREFS, 0).edit().putString("rotation", Config.joinSet(rot)).apply();
                rotateIfDue();
                refresh.run();
            } else if (which == 2) {
                java.util.Set<String> chosen = showcaseSet();
                if (!chosen.remove(name)) chosen.add(name);
                getSharedPreferences(PREFS, 0).edit().putString("showcase", Config.joinSet(chosen)).apply();
                refresh.run();
            } else {
                final String label = name.substring(IMAGE.length());
                panel(String.format(Strings.t("Delete %s?"), label), Arrays.asList(Strings.t("Delete"), Strings.t("Cancel")), confirm -> {
                    if (confirm != 0) return;
                    new File(images(), label).delete();
                    java.util.Set<String> rot = rotationSet();
                    rot.remove(name);
                    SharedPreferences.Editor e = getSharedPreferences(PREFS, 0).edit().putString("rotation", Config.joinSet(rot));
                    if (name.equals(wallpaper())) e.putString("wallpaper", WALLPAPER_NAMES[0]);
                    e.apply();
                    showWallpaper(wallpaper());
                    showWallpaperScreen(null);
                });
            }
        });
    }

    private void setWallpaperTo(String name) {
        getSharedPreferences(PREFS, 0).edit().putString("wallpaper", name).apply();
        showWallpaper(name);
    }

    // A rounded text button on glass; focused it turns white with dark text, like the controls.
    private TextView pill(String label, int size) {
        final TextView t = text(size, Color.WHITE, regular);
        t.setText(label);
        t.setSingleLine(true);
        t.setPadding(px(26), px(12), px(26), px(12));
        t.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(40));
        t.setBackground(bg);
        t.setTag(Boolean.FALSE);
        markPill(t, false, false);
        t.setOnFocusChangeListener((v, has) -> {
            markPill(t, Boolean.TRUE.equals(t.getTag()), has);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        return t;
    }

    // chosen: the active choice (a soft white tint); focused: solid white.
    private void markPill(TextView t, boolean chosen, boolean focused) {
        t.setTag(chosen);
        GradientDrawable bg = (GradientDrawable) t.getBackground();
        bg.setColor(focused ? 0xF2FFFFFF : chosen ? 0x52FFFFFF : 0x1FFFFFFF);
        bg.setStroke(chosen && !focused ? Math.max(1, px(2)) : 0, 0xB3FFFFFF);
        t.setTextColor(focused ? 0xFF1C1820 : Color.WHITE);
    }

    // One grid thumbnail: the gradient or picture, a ✓/↻ badge, the name under it when focused.
    interface Anchor { void at(View v); }

    private View wallThumb(final String name, int w, int h, TextView badge, final Runnable onFocus, final Anchor onPick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        final FrameLayout card = new FrameLayout(this);
        card.setContentDescription("thumb:" + name);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF1E1A1C);
        card.setBackground(bg);
        card.setClipToOutline(true);
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (name.startsWith(IMAGE)) {
            Drawable ready = thumb(new File(images(), name.substring(IMAGE.length())), w, h,
                    () -> { Drawable dd = thumb(new File(images(), name.substring(IMAGE.length())), w, h, null); if (dd != null) iv.setImageDrawable(dd); });
            if (ready != null) iv.setImageDrawable(ready);
        } else {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            flatten(background(WALLPAPERS[Math.max(0, Arrays.asList(WALLPAPER_NAMES).indexOf(name))]), b);
            iv.setImageBitmap(b);
        }
        card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable bb = new GradientDrawable();
        bb.setCornerRadius(px(16));
        bb.setColor(0xB3000000);
        badge.setBackground(bb);
        badge.setPadding(px(12), px(2), px(12), px(4));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.setMargins(0, px(10), px(10), 0);
        card.addView(badge, blp);
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(RADIUS));
        ring.setStroke(px(4), Color.TRANSPARENT);
        card.setForeground(ring);
        card.setFocusable(true);
        final TextView label = text(px(20), 0xE6FFFFFF, regular);
        label.setText(name.startsWith(IMAGE) ? name.substring(IMAGE.length()) : name);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, px(14), 0, 0);
        label.setVisibility(View.INVISIBLE);
        card.setOnFocusChangeListener((v, has) -> {
            ring.setStroke(px(4), has ? Color.WHITE : Color.TRANSPARENT);
            label.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px(20) : 0).setDuration(140).start();
            if (has) onFocus.run();
        });
        card.setOnClickListener(v -> onPick.at(v));
        box.addView(card, new LinearLayout.LayoutParams(w, h));
        box.addView(label, new LinearLayout.LayoutParams(w, -2));
        return box;
    }

    // Rotation: while the home is in front, every interval it moves to the next wallpaper marked for
    // rotation. Only the visible home ticks, so nothing runs while an app or the PS5 is on.
    private final Runnable rotateTick = this::rotateIfDue;

    private void rotateIfDue() {
        ui.removeCallbacks(rotateTick);
        SharedPreferences p = getSharedPreferences(PREFS, 0);
        long interval = Config.intervalMillis(p.getString("rotate", null));
        List<String> rotation = new ArrayList<>();
        java.util.Set<String> chosen = Config.splitSet(p.getString("rotation", null));
        for (String n : wallpapers()) if (chosen.contains(n)) rotation.add(n);
        if (interval == 0 || rotation.isEmpty()) return;
        long now = System.currentTimeMillis(), at = p.getLong("rotate_at", 0);
        if (Config.rotationDue(at, now, interval)) {
            String next = Config.nextInRotation(rotation, wallpaper());
            p.edit().putString("wallpaper", next).putLong("rotate_at", now).apply();
            at = now;
            previewing = next;
            preview(next);
        }
        ui.postDelayed(rotateTick, Math.max(1000, at + interval - now));
    }

    // Shows a wallpaper from the list: gradients and decoded pictures at once, others once decoded.
    private void preview(final String name) {
        if (decoded.get(name.startsWith(IMAGE) ? imageKey(name) : name) != null) {
            showWallpaper(name);
            return;
        }
        decoder.execute(() -> {
            wallpaperBitmap(name);
            ui.post(() -> { if (name.equals(previewing)) showWallpaper(name); });
        });
    }

    private String imageKey(String name) {
        File f = new File(images(), name.substring(IMAGE.length()));
        return f.getPath() + "@" + f.lastModified();
    }

    // A picture's thumbnail: from memory, else from the disk cache, else decoded in the background
    // (then saved). Null until it's ready; then ready runs on the main thread.
    private Drawable thumb(final File f, final int w, final int h, final Runnable ready) {
        final String key = f.getName() + "@" + f.lastModified() + "@" + w + "x" + h;
        synchronized (thumbs) {
            if (thumbs.containsKey(key)) return thumbs.get(key);
            thumbs.put(key, null);
        }
        final File cached = new File(getCacheDir(), "thumbs/" + Integer.toHexString(key.hashCode()) + ".png");
        decoder.execute(() -> {
            Bitmap b = cached.exists() ? BitmapFactory.decodeFile(cached.getPath()) : null;
            if (b == null) {
                b = crop(f, w, h);
                cached.getParentFile().mkdirs();
                if (b != null) try (java.io.FileOutputStream out = new java.io.FileOutputStream(cached)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception ignored) {}
            }
            final Drawable d = b == null ? new ColorDrawable(Color.TRANSPARENT) : new BitmapDrawable(getResources(), b);
            synchronized (thumbs) { thumbs.put(key, d); }
            if (ready != null) ui.post(ready);
        });
        return null;
    }

    // A glow colour made solid over the base, three times as strong, so a small dot shows it.
    private static int glow(int glow, int base) {
        float a = Math.min(1f, 3 * (glow >>> 24) / 255f);
        int r = Math.round(Color.red(base) + (Color.red(glow) - Color.red(base)) * a);
        int g = Math.round(Color.green(base) + (Color.green(glow) - Color.green(base)) * a);
        int b = Math.round(Color.blue(base) + (Color.blue(glow) - Color.blue(base)) * a);
        return Color.rgb(r, g, b);
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int m = px(MARGIN), g = px(GAP);
        int w = (getResources().getDisplayMetrics().widthPixels - 2 * m - g * (PER_ROW - 1)) / PER_ROW;
        int h = w * 9 / 16;
        tileW = w;
        tileH = h;

        List<String> apps = appList(pm);
        // Apps fill rows of six. With more rows the grid starts higher, so up to two rows of apps and
        // the bottom row still fit on screen; past that the page scrolls.
        int launchable = apps.size();
        int rows = Math.max(1, (launchable + PER_ROW - 1) / PER_ROW);
        gridTop = Math.max(px(GRID_MIN_TOP), px(ROW1_TOP) - (rows - 1) * px(ROW_STEP));
        int bottomTop = gridTop + rows * px(ROW_STEP);
        bottomRowTop = bottomTop;
        ((FrameLayout.LayoutParams) moveHint.getLayoutParams()).topMargin = gridTop - px(64);
        root.setMinimumHeight(bottomTop + h + px(MARGIN));

        String[] banners = banners().list();
        glassBanners.clear();
        for (String pkg : apps) {
            if (isInput(pkg)) {
                Drawable banner = inputBanner(pkg, banners, w, h);
                int i = rowApps.size();
                rowApps.add(pkg);
                addTile(root, pkg, inputLabel(input(pkg)), banner, banner == null ? LineIcon.HDMI : null, null, gridX(i), gridY(i), w, h);
                continue;
            }
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            Drawable banner = null;
            String custom = Config.bannerFile(pkg, banners);
            if (custom != null) {
                Bitmap b = sharp(new File(banners(), custom), w, h);
                if (b != null) {
                    banner = new BitmapDrawable(getResources(), b);
                    glassBanners.add(pkg);
                }
            }
            if (banner == null) try {
                banner = pm.getActivityBanner(li);
                if (banner == null) banner = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {}
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            // Apps without a TV banner get a plain tile with their normal icon.
            Drawable icon = null;
            if (banner == null) try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            int i = rowApps.size();
            rowApps.add(pkg);
            addTile(root, pkg, label, banner, null, icon, gridX(i), gridY(i), w, h);
        }

        // Bottom row: Play Store, HDMI inputs, Settings and Add app, six at most.
        int col = 0;
        boolean store = !apps.contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null;
        List<TvInputInfo> inputs = new ArrayList<>();
        for (TvInputInfo in : hdmiInputs()) if (!apps.contains(in.getId())) inputs.add(in);
        List<String> lower = new ArrayList<>();
        for (String p : Config.splitSet(getSharedPreferences(PREFS, 0).getString("lower", null)))
            if (!apps.contains(p) && !p.equals(PLAY_STORE) && launchIntent(pm, p) != null) lower.add(p);
        int room = PER_ROW - 2 - (store ? 1 : 0);
        if (lower.size() > room) lower = lower.subList(0, room);
        room -= lower.size();
        if (inputs.size() > room) inputs = inputs.subList(0, room);
        if (store)
            addTile(root, PLAY_STORE, "Play Store", null, LineIcon.PLAY, null, m + col++ * (w + g), bottomTop, w, h);
        for (String p : lower) {
            String label = p;
            Drawable icon = null;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString();
                icon = pm.getApplicationIcon(p);
            } catch (Exception ignored) {}
            addTile(root, p, label, null, null, icon, m + col++ * (w + g), bottomTop, w, h);
        }
        for (TvInputInfo in : inputs) {
            Drawable banner = inputBanner(in.getId(), banners, w, h);
            addTile(root, in.getId(), inputLabel(in), banner, banner == null ? LineIcon.HDMI : null, null,
                    m + col++ * (w + g), bottomTop, w, h);
        }
        addTile(root, SETTINGS, Strings.t("Settings"), null, LineIcon.SETTINGS, null, m + col++ * (w + g), bottomTop, w, h);
        addTile(root, ADD, Strings.t("Apps"), null, LineIcon.APPS, null, m + col * (w + g), bottomTop, w, h);

        // Top right, Apple TV style: one glass chip with the time and the avatar. Added last, so the
        // first app keeps first focus; Up from the apps reaches it. OK opens Control Center.
        addChip();

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    private void addTile(ViewGroup parent, final String key, String label, Drawable banner, int[] lineIcon, Drawable appIcon,
                         int x, int y, int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        if (banner != null) {
            if (glassBanners.contains(key)) {
                bg.setColor(0x13FFFFFF);
                bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            } else {
                bg.setColor(0xFF1E1A1C);
            }
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(fit(banner, w, h));
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // Quiet "glass" tile: icon above label.
            bg.setColor(0x13FFFFFF);
            bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            int size = lineIcon != null ? px(44) : px(64);
            if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
            else if (appIcon != null) iv.setImageDrawable(fit(appIcon, size, size));
            inner.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = text(px(24), 0xE0FFFFFF, regular);
            t.setText(label);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(12);
            inner.addView(t, lp);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setTag(key);

        // Banner tiles show their name underneath when focused.
        final TextView caption = banner == null ? null : text(px(24), Color.WHITE, regular);
        if (caption != null) {
            caption.setText(label);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setVisibility(View.INVISIBLE);
        }

        final boolean glass = banner == null || glassBanners.contains(key);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                reveal(y >= bottomRowTop);
            }
            if (glass) bg.setColor(has ? 0x38FFFFFF : 0x13FFFFFF);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? px(26) : 0)
                    .setDuration(140).withLayer().start();
            if (caption != null) caption.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
        // Apps on the home and HDMI inputs have a menu: hold OK or press Menu.
        final Runnable menu = rowApps.contains(key) ? () -> showEditMenu(key)
                : isInput(key) ? () -> showInputMenu(key) : null;
        final boolean editable = menu != null;
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { menu.run(); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) menu.run();
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) {
                    longPressed = false;
                    okDownKey = key;
                } else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    menu.run();
                }
            } else {
                // Only the key-up of an OK that went down on this tile opens the app, and not a cancelled
                // one (focus left while OK was held). View does the same for a click with isPressed().
                boolean pressed = key.equals(okDownKey) && !ev.isCanceled();
                okDownKey = null;
                if (pressed && !longPressed) open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        if (caption != null) {
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
            cp.topMargin = h + px(22);
            wrap.addView(caption, cp);
        }
        parent.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAllApps();
            return;
        } else if (SETTINGS.equals(key)) {
            String[] items = SETTINGS_ITEMS;
            if (!hiddenInputs().isEmpty()) {
                items = Arrays.copyOf(SETTINGS_ITEMS, SETTINGS_ITEMS.length + 1);
                items[SETTINGS_ITEMS.length] = "Show hidden inputs";
            }
            dropdown(tile(SETTINGS), items, which -> {
                if (which == 0) showWallpaperScreen(null);
                else if (which == 1) openBrightness();
                else if (which == 2) openTvSettings();
                else saveHiddenInputs(new java.util.LinkedHashSet<String>());
            });
            return;
        } else if (isInput(key)) {
            openInput(this, key);
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    static void openInput(Context c, String id) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        TvInputInfo info = tim == null ? null : tim.getTvInputInfo(id);
        if (info != null && !info.isPassthroughInput()) {
            Intent channels = new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelsUriForInput(id),
                    "vnd.android.cursor.dir/channel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            for (String pkg : new String[] {"com.tcl.tv", LIVE_TV[0][0], LIVE_TV[1][0]}) {
                try {
                    c.startActivity(new Intent(channels).setPackage(pkg));
                    return;
                } catch (Exception ignored) {}
            }
            try { c.startActivity(channels); } catch (Exception ignored) {}
            return;
        }
        PackageManager pm = c.getPackageManager();
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(pm) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }


    // The Alerts tile: how many notifications there are, and "new" when one arrived since the list was
    // last opened. Without notification access it just says Alerts.
    private void refreshBell() {
        if (alertsTile == null) return;
        View tile = (View) alertsTile.getParent();
        if (NotifService.live == null) {
            tile.setContentDescription(Strings.t("Alerts"));
            return;
        }
        int count = NotifService.list().size();
        boolean unseen = NotifService.lastPosted() > getSharedPreferences(PREFS, 0).getLong("bell_seen", 0);
        alertsTile.setText(count > 0 ? Strings.t("Alerts") + " · " + count : Strings.t("Alerts"));
        tile.setContentDescription(Strings.t("Notifications") + " " + count + (unseen ? " new" : ""));
    }

    // Glides the page so the bottom row shows (focus is on it) or tucks it back to a peek.
    private void reveal(boolean bottomRow) {
        if (scroller == null) return;
        int max = Math.max(0, root.getHeight() - scroller.getHeight());
        scroller.smoothScrollTo(0, bottomRow ? max : 0);
    }

    // --es open control (the cog from inside another app, through KeyService): show Control Center.
    // --es open wallpapers: the Wallpaper screen.
    private void openFrom(Intent intent) {
        String open = intent == null ? null : intent.getStringExtra("open");
        if (open == null) return;
        intent.removeExtra("open");
        if (open.equals("control")) ui.post(() -> { if (controlCenter == null) showControlCenter(); });
        else if (open.equals("wallpapers")) ui.post(() -> showWallpaperScreen(null));
        else if (open.equals("saver")) ui.post(() -> showSaverSettings(0));
        else if (open.equals("slideshow")) ui.post(() -> showAmbient(true));
    }

    // The chip at the top right: time and avatar on glass.
    private void addChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(px(26), px(8), px(8), px(8));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(40));
        bg.setColor(0x24FFFFFF);
        bg.setStroke(Math.max(1, px(2)), 0x30FFFFFF);
        chip.setBackground(bg);
        npBox = new LinearLayout(this);
        npBox.setGravity(Gravity.CENTER_VERTICAL);
        npBox.setContentDescription("nowplaying");
        npBox.setVisibility(View.GONE);
        npArt = new ImageView(this);
        npBox.addView(npArt, new LinearLayout.LayoutParams(px(44), px(44)));
        npText = text(px(22), 0xE6FFFFFF, regular);
        npText.setMaxWidth(px(360));
        scroll(npText);
        npText.setPadding(px(14), 0, px(10), 0);
        npBox.addView(npText);
        npState = new LineIcon(LineIcon.PLAY);
        ImageView st = new ImageView(this);
        st.setImageDrawable(npState);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(px(24), px(24));
        sp.rightMargin = px(20);
        npBox.addView(st, sp);
        chip.addView(npBox);
        TextClock time = clock("h:mm", "H:mm", px(32), Color.WHITE, semibold);
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.rightMargin = px(14);
        chip.addView(time, tp);
        chip.addView(avatar(px(52)), new LinearLayout.LayoutParams(px(52), px(52)));
        chip.setFocusable(true);
        chip.setTag(CHIP);
        chip.setContentDescription("chip");
        chip.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = CHIP;
                reveal(false);
            }
            bg.setColor(has ? 0x52FFFFFF : 0x24FFFFFF);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).withLayer().start();
        });
        chip.setOnClickListener(v -> showControlCenter());
        // In a wrapper like every tile: buildTiles removes each tile's parent from the page.
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(chip, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = px(ICON_TOP - 6);
        lp.rightMargin = px(MARGIN);
        root.addView(wrap, lp);
        tiles.add(chip);
        refreshNowPlaying();
    }

    private void refreshNowPlaying() {
        android.media.session.MediaController m = NowPlaying.current(this);
        Bitmap art = m == null ? null : NowPlaying.art(m);
        if (npBox != null) {
            npBox.setVisibility(m == null ? View.GONE : View.VISIBLE);
            if (m != null) {
                npText.setText(NowPlaying.line(m));
                npArt.setImageDrawable(art == null ? new LineIcon(LineIcon.SOUND) : rounded(art, px(44), px(10)));
                npState.setOps(NowPlaying.playing(m) ? LineIcon.PLAY : LineIcon.PAUSE);
            }
        }
        if (showcase != null)
            showcase.setNowPlaying(m == null ? null : NowPlaying.line(m), art == null ? null : rounded(art, px(40), px(8)));
    }

    private Drawable rounded(Bitmap b, int size, int radius) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        float s = Math.max((float) size / b.getWidth(), (float) size / b.getHeight());
        android.graphics.Matrix mx = new android.graphics.Matrix();
        mx.setScale(s, s);
        mx.postTranslate((size - b.getWidth() * s) / 2, (size - b.getHeight() * s) / 2);
        sh.setLocalMatrix(mx);
        p.setShader(sh);
        new Canvas(out).drawRoundRect(0, 0, size, size, radius, radius, p);
        return new BitmapDrawable(getResources(), out);
    }

    // The owner's picture in a circle (files/avatar.png or .jpg, from avatar.sh), else their initial
    // on a gradient (--es name "Thales").
    private File avatarFile() {
        for (String n : new String[] {"avatar.png", "avatar.jpg"}) {
            File f = new File(folder(""), n);
            if (f.exists()) return f;
        }
        return null;
    }

    private View avatar(int size) {
        FrameLayout f = new FrameLayout(this);
        File photo = avatarFile();
        Bitmap b = photo == null ? null : sharp(photo, size, size);
        if (b != null) {
            Bitmap round = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setShader(new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
            new Canvas(round).drawCircle(size / 2f, size / 2f, size / 2f, p);
            f.setBackground(new BitmapDrawable(getResources(), round));
            return f;
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFFFF7A59, 0xFFA855F7, 0xFF3B82F6});
        g.setShape(GradientDrawable.OVAL);
        f.setBackground(g);
        String name = getSharedPreferences(PREFS, 0).getString("name", "");
        if (!name.isEmpty()) {
            TextView t = text(size * 45 / 100, Color.WHITE, semibold);
            t.setText(name.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        return f;
    }

    // Control Center: a frosted panel on the right, like Apple TV's. Its glass is the wallpaper blurred
    // once, not live. The cog or Back closes it.
    private int ccFocus = -1;

    private void showControlCenter() {
        if (controlCenter != null) {
            controlCenter.dismiss();
            return;
        }
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        controlCenter = d;
        final int w = px(600), pad = px(36), gap = px(14);
        final int h = getResources().getDisplayMetrics().heightPixels - px(48);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.setContentDescription("control-center");
        box.setClipChildren(false);
        Bitmap glass = frosted(w, h);
        box.setBackground(new LayerDrawable(new Drawable[] {
                glass != null ? new BitmapDrawable(getResources(), glass) : new ColorDrawable(0xFF161113),
                new ColorDrawable(0x66140F1A)}));
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(36));
            }
        });
        box.setClipToOutline(true);

        // You, the full date, the time.
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(avatar(px(72)), new LinearLayout.LayoutParams(px(72), px(72)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(px(18), 0, 0, 0);
        String name = getSharedPreferences(PREFS, 0).getString("name", "");
        if (!name.isEmpty()) {
            TextView n = text(px(28), Color.WHITE, semibold);
            n.setText(name);
            who.addView(n);
        }
        who.addView(clock("EEEE, d MMMM yyyy", "EEEE, d MMMM yyyy", px(22), 0xB3FFFFFF, regular));
        head.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
        TextClock time = clock("h:mm", "H:mm", px(56), Color.WHITE, semibold);
        time.setFontFeatureSettings("tnum");
        head.addView(time);
        box.addView(head);
        final View playPause = nowPlayingCard(box, d);

        TextView label = text(px(18), 0x99FFFFFF, semibold);
        label.setText(Strings.t("Controls").toUpperCase(java.util.Locale.getDefault()));
        label.setLetterSpacing(0.12f);
        label.setPadding(0, px(30), 0, px(12));
        box.addView(label);

        // Wi-Fi, Bluetooth, Sound, Picture / Inputs, Screensaver, Alerts, Wallpaper / Settings, Apps.
        final int tile = (w - 2 * pad - 3 * gap) / 4;
        int net = networkState();
        final boolean autoSaver = getSharedPreferences(PREFS, 0).getBoolean("auto_saver", true);
        int[][] icons = {LineIcon.WIFI, LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE, LineIcon.INPUT,
                LineIcon.MOON, LineIcon.BELL, LineIcon.FRAME, LineIcon.SETTINGS, LineIcon.APPS, LineIcon.HDMI};
        String[] labels = {net == OFFLINE ? Strings.t("Offline") : net == ETHERNET ? "Ethernet" : Strings.t("Wi-Fi"),
                Strings.t("Bluetooth"), Strings.t("Sound"), Strings.t("Picture"), Strings.t("Inputs"),
                Strings.t("Screensaver"), Strings.t("Alerts"), Strings.t("Wallpaper"), Strings.t("Settings"), Strings.t("Apps"),
                Strings.t("Auto saver") + "\n" + Strings.t(autoSaver ? "On" : "Off")};
        Runnable[] actions = {this::openNetworkSettings, () -> openQuick("bluetooth"), () -> openQuick("sound"),
                () -> openQuick("picture"), this::openInputs, () -> showSaverSettings(0), this::showNotifications,
                () -> showWallpaperScreen(null), this::openTvSettings, this::showAllApps, () -> {
                    getSharedPreferences(PREFS, 0).edit().putBoolean("auto_saver", !autoSaver).commit();
                    ccFocus = 10;
                    ui.post(this::showControlCenter);
                }};
        LinearLayout row = null;
        View first = null, focusTile = null;
        for (int i = 0; i < icons.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false); // a focused tile grows past its row; don't shave its corners
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, tile);
                if (i > 0) rp.topMargin = gap;
                box.addView(row, rp);
            }
            final Runnable action = actions[i];
            View t = controlTile(icons[i], labels[i], () -> {
                d.dismiss();
                action.run();
            });
            if (i == 6) alertsTile = (TextView) ((ViewGroup) t).getChildAt(1);
            if (i == ccFocus) focusTile = t;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(tile, tile);
            if (i % 4 > 0) cp.leftMargin = gap;
            row.addView(t, cp);
            if (first == null) first = t;
        }
        refreshBell();

        // The latest notification, then "+N more"; OK opens the list.
        List<android.service.notification.StatusBarNotification> list = NotifService.list();
        if (!list.isEmpty()) {
            TextView latest = text(px(20), 0xD9FFFFFF, regular);
            android.os.Bundle x = list.get(0).getNotification().extras;
            CharSequence title = x == null ? null : x.getCharSequence(android.app.Notification.EXTRA_TITLE);
            latest.setText(labelOf(list.get(0).getPackageName()) + (title != null ? " · " + title : "")
                    + (list.size() > 1 ? "   +" + (list.size() - 1) + " " + Strings.t("more") : ""));
            latest.setSingleLine(true);
            latest.setEllipsize(android.text.TextUtils.TruncateAt.END);
            latest.setPadding(px(20), px(16), px(20), px(16));
            final GradientDrawable lb = new GradientDrawable();
            lb.setCornerRadius(px(20));
            lb.setColor(0x1FFFFFFF);
            latest.setBackground(lb);
            latest.setFocusable(true);
            latest.setOnFocusChangeListener((v, has) -> lb.setColor(has ? 0x52FFFFFF : 0x1FFFFFFF));
            latest.setOnClickListener(v -> {
                d.dismiss();
                showNotifications();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = px(28);
            box.addView(latest, lp);
        }

        d.setContentView(box);
        d.setOnKeyListener((di, code, ev) -> {
            if (code != KeyEvent.KEYCODE_NOTIFICATION) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) d.dismiss();
            return true;
        });
        d.setOnDismissListener(di -> {
            if (controlCenter == d) controlCenter = null;
            alertsTile = null;
        });
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.width = w;
        lp.height = h;
        lp.x = px(24);
        win.setAttributes(lp);
        win.setDimAmount(0.3f);
        d.show();
        ccFocus = -1;
        if (focusTile != null) focusTile.requestFocus();
        else if (playPause != null) playPause.requestFocus();
        else if (first != null) first.requestFocus();
    }

    private View nowPlayingCard(LinearLayout box, Dialog d) {
        if (NowPlaying.current(this) == null) return null;
        final LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setContentDescription("nowplaying-card");
        card.setPadding(px(18), px(18), px(18), px(18));
        card.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0x1FFFFFFF);
        card.setBackground(bg);
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setContentDescription("nowplaying-open");
        item.setPadding(px(8), px(8), px(8), px(8));
        final GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(px(18));
        ib.setColor(Color.TRANSPARENT);
        item.setBackground(ib);
        item.setFocusable(true);
        item.setOnFocusChangeListener((v, has) -> ib.setColor(has ? 0x33FFFFFF : Color.TRANSPARENT));
        item.setOnClickListener(v -> {
            android.media.session.MediaController m = NowPlaying.current(this);
            Intent li = m == null ? null : launchIntent(getPackageManager(), m.getPackageName());
            if (li == null) return;
            d.dismiss();
            try { startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        });
        final ImageView art = new ImageView(this);
        item.addView(art, new LinearLayout.LayoutParams(px(88), px(88)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(18), 0, px(4), 0);
        final TextView title = text(px(24), Color.WHITE, semibold);
        final TextView artist = text(px(20), 0xB3FFFFFF, regular);
        for (TextView t : new TextView[] {title, artist}) {
            scroll(t);
            words.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        final LineIcon state = new LineIcon(LineIcon.PAUSE);
        View prev = mediaButton(new LineIcon(LineIcon.PREVIOUS), "previous",
                () -> { android.media.session.MediaController m = NowPlaying.current(this); if (m != null) m.getTransportControls().skipToPrevious(); });
        View pp = mediaButton(state, "playpause", () -> NowPlaying.playPause(NowPlaying.current(this)));
        View next = mediaButton(new LineIcon(LineIcon.NEXT), "next",
                () -> { android.media.session.MediaController m = NowPlaying.current(this); if (m != null) m.getTransportControls().skipToNext(); });
        for (View b : new View[] {prev, pp, next}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(60), px(60));
            lp.leftMargin = px(8);
            card.addView(b, lp);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = px(24);
        box.addView(card, cp);
        final Runnable update = () -> {
            android.media.session.MediaController m = NowPlaying.current(this);
            card.setVisibility(m == null ? View.GONE : View.VISIBLE);
            if (m == null) return;
            Bitmap a = NowPlaying.art(m);
            art.setImageDrawable(a == null ? new LineIcon(LineIcon.SOUND) : rounded(a, px(88), px(14)));
            title.setText(NowPlaying.title(m));
            String who = NowPlaying.artist(m);
            artist.setText(who == null ? "" : who);
            artist.setVisibility(who == null ? View.GONE : View.VISIBLE);
            state.setOps(NowPlaying.playing(m) ? LineIcon.PAUSE : LineIcon.PLAY);
        };
        update.run();
        NowPlaying.listen(this, update);
        d.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { NowPlaying.unlisten(MainActivity.this, update); }
        });
        return pp;
    }

    private void scroll(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        t.setMarqueeRepeatLimit(-1);
        t.setHorizontalFadingEdgeEnabled(true);
        t.setSelected(true);
    }

    private View mediaButton(final LineIcon icon, String desc, final Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setContentDescription(desc);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x24FFFFFF);
        b.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        b.addView(iv, new FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x24FFFFFF);
            icon.setColorFilter(has ? new android.graphics.PorterDuffColorFilter(0xFF1C1820, android.graphics.PorterDuff.Mode.SRC_IN) : null);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(120).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    // One square control: icon over label, on glass; focused it turns white with dark content, like tvOS.
    private View controlTile(int[] icon, String label, final Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0x24FFFFFF);
        t.setBackground(bg);
        final LineIcon li = new LineIcon(icon);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(li);
        t.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        final TextView tv = text(px(18), 0xE6FFFFFF, regular);
        tv.setText(label);
        tv.setMaxLines(label.indexOf('\n') >= 0 ? 2 : 1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(px(6), px(8), px(6), 0);
        t.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x24FFFFFF);
            li.setColorFilter(has ? new android.graphics.PorterDuffColorFilter(0xFF1C1820, android.graphics.PorterDuff.Mode.SRC_IN) : null);
            tv.setTextColor(has ? 0xFF1C1820 : 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    // The panel's glass: the part of the wallpaper behind it, blurred by shrinking it and growing it back.
    private Bitmap frosted(int w, int h) {
        Bitmap wall = wallpaperBitmap(wallpaper());
        if (wall == null) wall = wallpaperBitmap(WALLPAPER_NAMES[0]);
        if (wall == null) return null;
        int sw = wall.getWidth(), sh = wall.getHeight();
        int x = Math.max(0, sw - w - px(24)), y = Math.max(0, (sh - h) / 2);
        Bitmap part = Bitmap.createBitmap(wall, x, y, Math.min(w, sw - x), Math.min(h, sh - y));
        // Blur a small copy (box blur, three passes ~ gaussian), then grow it back: smooth, done once.
        Bitmap small = Bitmap.createScaledBitmap(part, Math.max(1, part.getWidth() / 8), Math.max(1, part.getHeight() / 8), true);
        int sw2 = small.getWidth(), sh2 = small.getHeight();
        int[] px = new int[sw2 * sh2];
        small.getPixels(px, 0, sw2, 0, 0, sw2, sh2);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, sw2, sh2, 4, true);
            boxBlur(px, sw2, sh2, 4, false);
        }
        Bitmap blurred = Bitmap.createBitmap(px, sw2, sh2, Bitmap.Config.ARGB_8888);
        return Bitmap.createScaledBitmap(blurred, w, h, true);
    }

    // One box-blur pass of radius r over rows (horizontal) or columns, in place.
    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w;
        int[] line = new int[n];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < n; i++) line[i] = px[horizontal ? l * w + i : i * w + l];
            for (int i = 0; i < n; i++) {
                int a = 0, rr = 0, g = 0, b = 0, c = 0;
                for (int k = Math.max(0, i - r); k <= Math.min(n - 1, i + r); k++, c++) {
                    int p = line[k];
                    a += p >>> 24; rr += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF;
                }
                px[horizontal ? l * w + i : i * w + l] = (a / c) << 24 | (rr / c) << 16 | (g / c) << 8 | (b / c);
            }
        }
    }

    // TCL's input picker, else Android's settings.
    private void openInputs() {
        Intent i = launchIntent(getPackageManager(), "com.tcl.suspension");
        if (i == null) i = new Intent(Settings.ACTION_SETTINGS);
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    // The notifications, newest first: app, title and text. OK opens one and clears it if it can be
    // cleared; ongoing ones like AirPlay stay. The last row clears every one that can be cleared.
    private void showNotifications() {
        getSharedPreferences(PREFS, 0).edit().putLong("bell_seen", System.currentTimeMillis()).apply();
        refreshBell();
        final List<android.service.notification.StatusBarNotification> list = NotifService.list();
        List<String> rows = new ArrayList<>();
        for (android.service.notification.StatusBarNotification n : list) {
            Bundle x = n.getNotification().extras;
            StringBuilder row = new StringBuilder(labelOf(n.getPackageName()));
            for (String k : new String[] {android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_TEXT}) {
                CharSequence v = x == null ? null : x.getCharSequence(k);
                if (v != null && v.toString().trim().length() > 0) row.append(" \u2014 ").append(v.toString().trim());
            }
            rows.add(row.toString());
        }
        rows.add(Strings.t(list.isEmpty() ? "No notifications" : "Clear all"));
        panel(Strings.t("Notifications"), rows, which -> {
            NotifService s = NotifService.live;
            if (s == null || list.isEmpty()) return;
            if (which == list.size()) {
                s.cancelAllNotifications();
                return;
            }
            android.service.notification.StatusBarNotification n = list.get(which);
            android.app.PendingIntent open = n.getNotification().contentIntent;
            if (open != null) try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    open.send(this, 0, null, null, null, null, o.toBundle());
                } else {
                    open.send();
                }
            } catch (Exception ignored) {}
            if (n.isClearable()) s.cancelNotification(n.getKey());
        });
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // One of TCL's own panels (picture, SoundPreset, info). TCL ships the service in com.tcl.settings on
    // some TVs and inside com.android.tv.settings on others, so it's found by its action. Null if absent.
    private Intent tclWindow(String type) {
        List<ResolveInfo> found = getPackageManager().queryIntentServices(new Intent(TCL_SHOW_WINDOW), 0);
        if (found.isEmpty()) return null;
        android.content.pm.ServiceInfo s = found.get(0).serviceInfo;
        return new Intent(TCL_SHOW_WINDOW).setClassName(s.packageName, s.name).putExtra("Type", type);
    }

    // Info on the home shows TCL's signal panel (resolution, HDR). Apps in front get the key as usual.
    @Override
    public boolean onKeyUp(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_NOTIFICATION) { // the cog on this TV's remote
            showControlCenter();
            return true;
        }
        if (code == KeyEvent.KEYCODE_INFO) {
            Intent info = tclWindow("info");
            if (info != null) try { startService(info); return true; } catch (Exception ignored) {}
        }
        return super.onKeyUp(code, event);
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        Intent tcl = tclType == null ? null : tclWindow(tclType);
        if (tcl != null) all.add(tcl);
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins. False if none did.
    private boolean openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // The TV's own picture screen, where brightness lives: the Picture shortcut's screens first (TCL's
    // panel, then Android's Display page), then Android TV's Display & Sound page, else TV settings.
    // Only system screens are started, so it needs no permission and changes nothing by itself.
    private void openBrightness() {
        if (openQuick("picture")) return;
        Intent i = new Intent().setClassName(TV_DISPLAY_SOUND[0], TV_DISPLAY_SOUND[1]);
        if (i.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
        openTvSettings();
    }

    private void openTvSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) {}
    }

    interface Pick { void at(int which); }
    interface Icon { Drawable at(int which) throws Exception; }

    private void panel(String title, List<String> labels, Pick pick) {
        panel(title, labels, null, 0, -1, null, pick, null);
    }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. icon (or null) puts an icon of that size by each name.
    // With preview, the list starts on selected and moving through it calls preview; Back calls it
    // with selected again, to put things back. With hold, holding OK (or Menu) on a row calls it.
    private BaseAdapter panel(String title, final List<String> labels, final Icon icon, final int size,
                       final int selected, final Pick preview, final Pick pick, final Pick hold) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final Drawable[] icons = new Drawable[labels.size()];
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                // An icon that isn't ready yet (null) is asked for again on the next redraw.
                Drawable shown = null;
                if (icon != null && icons[pos] == null) {
                    try { icons[pos] = icon.at(pos); }
                    catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                    if (icons[pos] != null) icons[pos].setBounds(0, 0, size, size);
                }
                if (icon != null) {
                    shown = icons[pos];
                    if (shown == null) {
                        shown = new ColorDrawable(Color.TRANSPARENT);
                        shown.setBounds(0, 0, size, size);
                    }
                }
                t.setCompoundDrawablesRelative(shown, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        if (hold != null) {
            list.setOnItemLongClickListener((parent, v, pos, id) -> {
                d.dismiss();
                hold.at(pos);
                return true;
            });
            list.setOnKeyListener((v, code, ev) -> {
                if (code != KeyEvent.KEYCODE_MENU) return false;
                int pos = list.getSelectedItemPosition();
                if (ev.getAction() == KeyEvent.ACTION_UP && pos >= 0) {
                    d.dismiss();
                    hold.at(pos);
                }
                return true;
            });
        }
        if (preview != null) {
            list.setSelection(selected);
            list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    preview.at(pos);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
            d.setOnCancelListener(x -> preview.at(selected));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(preview != null ? 0xFF161113 : 0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(preview != null ? 0f : 0.5f); // a preview needs the home screen undimmed
        d.show();
        return adapter;
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        String[] items = isInput(key) ? Arrays.copyOf(EDIT_ITEMS, 3) : user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM;
        dropdown(tile(key), items, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app by name, label -> package, leaving out the apps on the home if skipRow.
    private TreeMap<String, String> allApps(boolean skipRow) {
        PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if ((skipRow && rowApps.contains(p)) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        return byLabel;
    }

    // The Apps tile: every app, to open without adding it to the home. Hold OK (or Menu) on one to
    // add it to the home, take it off, or see its info.
    private void showAllApps() {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = allApps(false);
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        panel(Strings.t("Apps"), new ArrayList<>(byLabel.keySet()), pos -> fit(pm.getApplicationIcon(pkgs.get(pos)), px(48), px(48)),
                px(48), -1, null, which -> open(pkgs.get(which)), which -> {
            final String pkg = pkgs.get(which);
            final boolean onHome = rowApps.contains(pkg);
            panel(labelOf(pkg), java.util.Arrays.asList(Strings.t(onHome ? "Remove from home" : "Add to home"),
                    Strings.t("App info")), choice -> {
                if (choice == 1) {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))); }
                    catch (Exception ignored) {}
                    return;
                }
                if (onHome) rowApps.remove(pkg);
                else rowApps.add(pkg);
                saveRow();
                focusedKey = onHome ? ADD : pkg;
                buildTiles();
            });
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick takes the place of the app
    // at replaceIndex, or is added at the end (-1).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = allApps(true);
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pos -> fit(pm.getApplicationIcon(pkgs.get(pos)), px(48), px(48)),
                px(48), -1, null, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        }, null);
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where app number i sits in the grid.
    private int gridX(int i) {
        return px(MARGIN) + (i % PER_ROW) * (tileW + px(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / PER_ROW) * px(ROW_STEP);
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -PER_ROW : code == KeyEvent.KEYCODE_DPAD_DOWN ? PER_ROW : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // Scroll along with it once it has moved.
                final View card = tile(key);
                card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private Drawable inputBanner(String id, String[] banners, int w, int h) {
        String custom = Config.bannerFile(Config.fileKey(id), banners);
        Bitmap b = custom == null ? null : sharp(new File(banners(), custom), w, h);
        if (b == null) return null;
        glassBanners.add(id);
        return new BitmapDrawable(getResources(), b);
    }

    private TvInputInfo input(String id) {
        for (TvInputInfo in : hdmiInputs()) if (in.getId().equals(id)) return in;
        return null;
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // HDMI inputs hidden from the home with hold OK > Hide.
    private java.util.Set<String> hiddenInputs() {
        return Config.splitSet(getSharedPreferences(PREFS, 0).getString("hidden_inputs", null));
    }

    private void saveHiddenInputs(java.util.Set<String> ids) {
        getSharedPreferences(PREFS, 0).edit().putString("hidden_inputs", Config.joinSet(ids)).apply();
        buildTiles();
    }

    // Hold OK on an HDMI tile: hide it, for ports with nothing plugged in. Settings > Show hidden inputs
    // brings them all back.
    private void showInputMenu(final String id) {
        dropdown(tile(id), new String[] {"Hide"}, which -> {
            java.util.Set<String> ids = hiddenInputs();
            ids.add(id);
            focusedKey = SETTINGS;
            saveHiddenInputs(ids);
        });
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        java.util.Set<String> hidden = hiddenInputs();
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)
                    && !hidden.contains(in.getId())) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return withoutDeviceType(custom.toString());
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? withoutDeviceType(label.toString()) : "HDMI";
    }

    // Philips stores a name as "name&DEVICE_TYPE" (like "Computer&COMPUTER"), so show the name only.
    // Only on Philips TVs: elsewhere a name like "AUDIO&VIDEO" is the name itself.
    private static String withoutDeviceType(String label) {
        if (!"Philips".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return label;
        int amp = label.lastIndexOf('&');
        return amp > 0 && label.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}") ? label.substring(0, amp).trim() : label;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
