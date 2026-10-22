package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

// Remote buttons that open an app, set with --es keys "4020:com.stremio.one". Other keys pass through.
public class KeyService extends AccessibilityService {
    static volatile KeyService live; // set while Android has the service bound

    private final java.util.Map<String, Integer> inputStates = new java.util.HashMap<>();
    private String foreground;
    private android.media.tv.TvInputManager.TvInputCallback inputs;

    @Override protected void onServiceConnected() {
        live = this;
        final android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return;
        for (android.media.tv.TvInputInfo in : tim.getTvInputList()) inputStates.put(in.getId(), tim.getInputState(in.getId()));
        inputs = new android.media.tv.TvInputManager.TvInputCallback() {
            @Override public void onInputStateChanged(String id, int state) {
                Integer before = inputStates.put(id, state);
                boolean on = getSharedPreferences(MainActivity.PREFS, 0).getBoolean("auto_saver", true);
                if (before == null || !Config.slideshowOnSourceLost(before, state, foreground, on)) return;
                try {
                    startActivity(new Intent(KeyService.this, MainActivity.class).putExtra("open", "slideshow")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                } catch (Exception ignored) {}
            }
        };
        tim.registerCallback(inputs, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    @Override public void onDestroy() {
        live = null;
        android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null && inputs != null) tim.unregisterCallback(inputs);
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (Config.fromController(event.getSource())) return false;
        String pkg;
        try {
            pkg = Config.parseKeys(getSharedPreferences(MainActivity.PREFS, 0).getString("keys", null))
                    .get(event.getKeyCode());
        } catch (IllegalStateException locked) { // storage not unlocked yet, early in boot
            return false;
        }
        if (pkg == null) return false;
        if (pkg.indexOf('/') > 0) {
            if (event.getAction() == KeyEvent.ACTION_UP) MainActivity.openInput(this, pkg);
            return true;
        }
        Intent launch = pkg.startsWith("@") // a home action, e.g. "@control" opens Control Center
                ? new Intent(this, MainActivity.class).putExtra("open", pkg.substring(1))
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                : getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null && !pkg.startsWith("@")) launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null)
            foreground = event.getPackageName().toString();
    }
    @Override public void onInterrupt() {}
}
