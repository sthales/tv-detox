package com.ansub.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

final class Ambience {
    private static final long WAIT = 5000, FADE_IN = 3000, FADE_OUT = 2000;

    private final Context c;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable begin = this::fadeIn;
    private final Runnable check = this::othersChanged;
    private MediaPlayer player;
    private ValueAnimator fader;
    private float level;
    private boolean on;

    Ambience(Context c) {
        this.c = c.getApplicationContext();
    }

    static File dir(Context c) {
        return MainActivity.folder(c, "sounds");
    }

    static String[] names(Context c) {
        String[] files = dir(c).list((d, n) -> Config.isSound(n));
        if (files == null) return new String[0];
        java.util.Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
        return files;
    }

    static String label(String file) {
        String n = file.substring(0, file.lastIndexOf('.') > 0 ? file.lastIndexOf('.') : file.length()).replace('-', ' ').replace('_', ' ');
        return n.isEmpty() ? file : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    void start() {
        if (on) return;
        on = true;
        NowPlaying.listen(c, check);
        ui.postDelayed(begin, WAIT);
    }

    void stop() {
        if (!on) return;
        on = false;
        NowPlaying.unlisten(c, check);
        ui.removeCallbacks(begin);
        fadeTo(0f, FADE_OUT, this::release);
    }

    private void othersChanged() {
        if (!on) return;
        ui.removeCallbacks(begin);
        if (NowPlaying.playing(NowPlaying.current(c))) fadeTo(0f, FADE_OUT, this::release);
        else if (player == null) ui.postDelayed(begin, WAIT);
    }

    private void fadeIn() {
        if (!on || NowPlaying.playing(NowPlaying.current(c))) return;
        android.content.SharedPreferences p = c.getSharedPreferences(MainActivity.PREFS, 0);
        String name = p.getString("saver_sound", "");
        File f = new File(dir(c), name);
        if (name.isEmpty() || !f.isFile()) return;
        if (player == null) try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(f.getPath());
            player.setLooping(true);
            player.prepare();
            level = 0f;
            player.setVolume(0f, 0f);
            player.start();
        } catch (Exception e) {
            release();
            return;
        }
        fadeTo(p.getInt("saver_volume", 20) / 100f, FADE_IN, null);
    }

    private void fadeTo(float target, long ms, final Runnable done) {
        if (fader != null) fader.cancel();
        if (player == null) {
            if (done != null) done.run();
            return;
        }
        fader = ValueAnimator.ofFloat(level, target);
        fader.setDuration(ms);
        fader.addUpdateListener(a -> {
            level = (float) a.getAnimatedValue();
            if (player != null) player.setVolume(level, level);
        });
        if (done != null) fader.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) { if (!cancelled) done.run(); }
        });
        fader.start();
    }

    private void release() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        level = 0f;
    }
}
