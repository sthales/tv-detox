package com.ansub.tvhome;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Feeds the bell. Needs notification access, which extras.sh grants with cmd notification allow_listener.
public class NotifService extends NotificationListenerService {
    static volatile NotifService live;
    static Runnable onChange; // set by the home screen, run on the main thread
    private static volatile long lastPosted;

    @Override public void onListenerConnected() { live = this; changed(); }
    @Override public void onListenerDisconnected() { live = null; changed(); }

    // Ongoing ones (AirPlay, debugging) are posted again whenever they update, so only others light the dot.
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.isOngoing()) lastPosted = System.currentTimeMillis();
        changed();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) { changed(); }

    static long lastPosted() { return lastPosted; }

    // Newest first. Group summaries repeat their children, so they're left out.
    static List<StatusBarNotification> list() {
        List<StatusBarNotification> out = new ArrayList<>();
        NotifService s = live;
        if (s == null) return out;
        try {
            for (StatusBarNotification n : s.getActiveNotifications())
                if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) out.add(n);
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    private static void changed() {
        Runnable r = onChange;
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }
}
