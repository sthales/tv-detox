package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

// Remote buttons that open an app, set with --es keys "4020:com.stremio.one". Other keys pass through.
public class KeyService extends AccessibilityService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        String pkg = Config.parseKeys(getSharedPreferences(MainActivity.PREFS, 0).getString("keys", null))
                .get(event.getKeyCode());
        if (pkg == null) return false;
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
