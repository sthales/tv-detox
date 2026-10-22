package com.ansub.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

final class NowPlaying {
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final List<MediaController> watched = new ArrayList<>();
    private static MediaSessionManager.OnActiveSessionsChangedListener sessions;
    private static final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { changed(); }
        @Override public void onMetadataChanged(MediaMetadata m) { changed(); }
        @Override public void onSessionDestroyed() { changed(); }
    };

    private NowPlaying() {}

    static MediaController current(Context c) {
        MediaController paused = null;
        for (MediaController m : controllers(c)) {
            PlaybackState s = m.getPlaybackState();
            if (s == null || m.getMetadata() == null) continue;
            if (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING) return m;
            if (s.getState() == PlaybackState.STATE_PAUSED && paused == null) paused = m;
        }
        return paused;
    }

    static boolean playing(MediaController m) {
        PlaybackState s = m == null ? null : m.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    static String title(MediaController m) {
        MediaMetadata d = m.getMetadata();
        String t = text(d, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        return t != null ? t : "";
    }

    static String artist(MediaController m) {
        return text(m.getMetadata(), MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
    }

    static String line(MediaController m) {
        String a = artist(m);
        return a == null || a.isEmpty() ? title(m) : title(m) + " · " + a;
    }

    static Bitmap art(MediaController m) {
        MediaMetadata d = m.getMetadata();
        if (d == null) return null;
        for (String k : new String[] {MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = d.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }

    static void playPause(MediaController m) {
        if (m == null) return;
        if (playing(m)) m.getTransportControls().pause();
        else m.getTransportControls().play();
    }

    static void listen(Context ctx, Runnable onChange) {
        final Context c = ctx.getApplicationContext();
        listeners.add(onChange);
        if (sessions != null) return;
        sessions = list -> rewatch(list);
        try {
            manager(c).addOnActiveSessionsChangedListener(sessions, component(c), ui);
            rewatch(controllers(c));
        } catch (Exception e) {
            sessions = null;
        }
    }

    static void unlisten(Context ctx, Runnable onChange) {
        listeners.remove(onChange);
        if (!listeners.isEmpty() || sessions == null) return;
        manager(ctx).removeOnActiveSessionsChangedListener(sessions);
        sessions = null;
        for (MediaController m : watched) m.unregisterCallback(callback);
        watched.clear();
    }

    private static void rewatch(List<MediaController> list) {
        for (MediaController m : watched) m.unregisterCallback(callback);
        watched.clear();
        if (list != null) for (MediaController m : list) {
            m.registerCallback(callback, ui);
            watched.add(m);
        }
        changed();
    }

    private static void changed() {
        ui.removeCallbacks(notify);
        ui.postDelayed(notify, 150);
    }

    private static final Runnable notify = () -> {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    };

    private static List<MediaController> controllers(Context c) {
        try {
            return manager(c).getActiveSessions(component(c));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static MediaSessionManager manager(Context c) {
        return (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    private static ComponentName component(Context c) {
        return new ComponentName(c, NotifService.class);
    }

    private static String text(MediaMetadata d, String... keys) {
        if (d == null) return null;
        for (String k : keys) {
            CharSequence s = d.getText(k);
            if (s != null && s.length() > 0) return s.toString();
        }
        return null;
    }
}
