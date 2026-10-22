package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

// Remote buttons that open an app, set with --es keys "4020:com.stremio.one". Other keys pass through.
public class KeyService extends AccessibilityService {
    static volatile KeyService live; // set while Android has the service bound

    @Override protected void onServiceConnected() { live = this; }
    @Override public void onDestroy() { live = null; super.onDestroy(); }

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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
