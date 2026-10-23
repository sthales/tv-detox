package com.ansub.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// The screensaver view: pictures cross-fading with a slow zoom-and-pan, a clock, what's playing and,
// for online pictures, a caption. Shown by the Dream and by Back on the home. Only two pictures are
// ever decoded: the one showing and the one fading in.
final class Showcase extends FrameLayout {
    static final class Slide {
        final String key, caption;
        final boolean fit; // whole picture on black (NASA's Earth), instead of filling the screen
        Slide(String key, String caption, boolean fit) {
            this.key = key;
            this.caption = caption;
            this.fit = fit;
        }
    }

    interface Source {
        List<Slide> slides();
        Bitmap load(Slide s, int w, int h); // called off the main thread
        long interval();
        boolean drift();
        boolean slide();
        boolean sharp4k();
    }

    private static final float[] STILL = {1f, 1f, 0f, 0f, 0f, 0f};

    private static final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private static final long FADE = 1500;
    private static final float HEADROOM = 1.15f; // decoded a bit larger than the screen for the drift

    private final Source source;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView[] views = new ImageView[2];
    private final ValueAnimator[] drifts = new ValueAnimator[2];
    private final TextView caption, playing;
    private final float scale;
    private List<Slide> slides = new ArrayList<>();
    private int index = -1, front = 0, gen;
    private final Runnable advance = () -> go(1, false);
    private boolean running;
    private android.view.SurfaceView surface;
    private Bitmap cur, nxt;
    private float[] curD = STILL, nxtD = STILL;
    private boolean curFit, nxtFit;
    private float curT, nxtT, curA = 1f, nxtA = 1f;
    private ValueAnimator fader, mover;
    private final Ambience ambience;

    Showcase(Context c, Source source, android.graphics.Typeface regular, android.graphics.Typeface semibold) {
        super(c);
        this.source = source;
        ambience = new Ambience(c);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        setBackgroundColor(Color.BLACK);
        setContentDescription("ambient");
        for (int i = 0; i < 2; i++) {
            views[i] = new ImageView(c);
            views[i].setScaleType(ImageView.ScaleType.MATRIX);
            views[i].setAlpha(0f);
            addView(views[i], new LayoutParams(-1, -1));
        }
        if (source.sharp4k()) {
            surface = new android.view.SurfaceView(c);
            android.view.Display.Mode mode = ((android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getMode();
            surface.getHolder().setFixedSize(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            surface.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override public void surfaceCreated(android.view.SurfaceHolder h) { render(); }
                @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int hh) { render(); }
                @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {}
            });
            addView(surface, new LayoutParams(-1, -1));
            setBackgroundColor(Color.TRANSPARENT);
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.TRANSPARENT, Color.TRANSPARENT, 0x8C000000}));
        shade.setTranslationZ(2f);
        addView(shade, new LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        TextClock date = clock(c, "EEEE, d MMMM", 30, 0xD9FFFFFF, regular);
        TextClock time = clock(c, "H:mm", 150, Color.WHITE, semibold);
        time.setFormat12Hour("h:mm");
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        info.addView(date);
        info.addView(time);
        playing = text(c, 26, 0xD9FFFFFF, regular);
        playing.setVisibility(GONE);
        playing.setCompoundDrawablePadding(px(14));
        playing.setPadding(0, px(14), 0, 0);
        info.addView(playing);
        LayoutParams ip = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ip.setMargins(px(96), 0, 0, px(84));
        info.setTranslationZ(2f);
        addView(info, ip);

        caption = text(c, 22, 0xCCFFFFFF, regular);
        LayoutParams cp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cp.setMargins(0, 0, px(96), px(92));
        caption.setTranslationZ(2f);
        addView(caption, cp);
    }

    void start() {
        if (running) return;
        running = true;
        slides = new ArrayList<>(source.slides());
        Collections.shuffle(slides);
        go(1, false);
        ambience.start();
    }

    boolean key(android.view.KeyEvent e) {
        int step;
        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                step = -1;
                break;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                step = 1;
                break;
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
            case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
            case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:
            case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                ((android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE)).dispatchMediaKeyEvent(e);
                return true;
            default:
                return false;
        }
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) go(step, true);
        return true;
    }

    void stop() {
        ambience.stop();
        running = false;
        ui.removeCallbacksAndMessages(null);
        for (int i = 0; i < 2; i++) {
            if (drifts[i] != null) drifts[i].cancel();
            views[i].animate().cancel();
            views[i].setImageDrawable(null);
        }
        if (fader != null) fader.cancel();
        if (mover != null) mover.cancel();
        cur = nxt = null;
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    // What's playing, under the clock; null hides it.
    void setNowPlaying(CharSequence text, android.graphics.drawable.Drawable art) {
        if (text == null) {
            playing.setVisibility(GONE);
            return;
        }
        playing.setText(text);
        if (art != null) art.setBounds(0, 0, px(40), px(40));
        playing.setCompoundDrawablesRelative(art, null, null, null);
        playing.setVisibility(VISIBLE);
    }

    private void go(int step, final boolean manual) {
        if (!running || slides.isEmpty()) return;
        ui.removeCallbacks(advance);
        final int g = ++gen;
        index = ((index + step) % slides.size() + slides.size()) % slides.size();
        final Slide s = slides.get(index);
        final int seed = index;
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        if (surface != null) {
            android.view.Display.Mode mode = ((android.view.WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getMode();
            sw = mode.getPhysicalWidth();
            sh = mode.getPhysicalHeight();
        }
        float room = source.drift() || source.slide() ? HEADROOM : 1f;
        final int w = Math.round(sw * room), h = Math.round(sh * room);
        decoder.execute(() -> {
            final Bitmap b = source.load(s, w, h);
            ui.post(() -> {
                if (!running || g != gen) return;
                boolean dip = source.slide() && !manual;
                if (b != null) show(s, b, seed, dip);
                ui.postDelayed(advance, b != null ? Math.max(FADE * 2, source.interval()) + (dip ? Config.DIP : 0) : 1000);
            });
        });
    }

    private float[] motion(int seed, long life) {
        return source.slide() ? Config.slide(life) : source.drift() ? Config.drift(seed) : STILL;
    }

    private long life(boolean dip) {
        return source.interval() + (dip ? Config.DIP + Config.DIP_OUT : FADE * 2);
    }

    // The new picture fades in over the old one, drifting from the moment it appears.
    private void show(Slide s, Bitmap b, int seed, boolean dip) {
        caption.setText(s.caption == null ? "" : s.caption);
        if (surface != null) {
            show4k(s, b, seed, dip);
            return;
        }
        final int back = 1 - front;
        final ImageView v = views[back];
        v.setImageBitmap(b);
        v.setContentDescription("slide:" + s.key);
        // Layered by elevation, not by reordering children: the new picture over the old one, and the
        // shade, clock and caption (Z = 2) always on top.
        v.setTranslationZ(1f);
        views[front].setTranslationZ(0f);
        final float[] d = motion(seed, life(dip));
        final boolean fit = s.fit;
        if (drifts[back] != null) drifts[back].cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(life(dip));
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.addUpdateListener(an -> place(v, b, d, (float) an.getAnimatedValue(), fit));
        place(v, b, d, 0f, fit);
        a.start();
        drifts[back] = a;
        v.animate().cancel();
        v.setAlpha(0f);
        final ImageView old = views[front];
        old.animate().cancel();
        if (dip) old.animate().alpha(0f).setStartDelay(0).setDuration(Config.DIP_OUT).start();
        v.animate().alpha(1f).setStartDelay(dip ? Config.DIP_OUT + Config.DIP_BLACK : 0)
                .setDuration(dip ? Config.DIP_IN : FADE).withEndAction(() -> {
            old.setAlpha(0f);
            old.setImageDrawable(null);
        }).start();
        front = back;
    }

    private void show4k(Slide s, Bitmap b, int seed, final boolean dip) {
        if (fader != null) fader.cancel();
        nxt = b;
        nxtD = motion(seed, life(dip));
        nxtFit = s.fit;
        nxtT = 0f;
        nxtA = 0f;
        surface.setContentDescription("slide:" + s.key);
        fader = ValueAnimator.ofFloat(0f, 1f);
        fader.setDuration(dip ? Config.DIP : cur == null ? 1 : FADE);
        fader.addUpdateListener(an -> {
            if (dip) {
                curA = Config.dipOut(an.getCurrentPlayTime());
                nxtA = Config.dipIn(an.getCurrentPlayTime());
            } else nxtA = (float) an.getAnimatedValue();
            render();
        });
        fader.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                if (nxt == null) return;
                cur = nxt;
                curD = nxtD;
                curFit = nxtFit;
                curT = nxtT;
                nxt = null;
                curA = 1f;
                nxtA = 1f;
                render();
            }
        });
        if (mover != null) mover.cancel();
        if (source.drift() || source.slide()) {
            mover = ValueAnimator.ofFloat(0f, 1f);
            mover.setDuration(life(dip));
            mover.setInterpolator(new android.view.animation.LinearInterpolator());
            mover.addUpdateListener(an -> {
                float t = (float) an.getAnimatedValue();
                if (nxt != null) nxtT = t; else curT = t;
                if (fader == null || !fader.isRunning()) render();
            });
            mover.start();
        }
        fader.start();
    }

    private void render() {
        if (surface == null || !surface.getHolder().getSurface().isValid()) return;
        android.graphics.Canvas c = null;
        try {
            c = surface.getHolder().lockHardwareCanvas();
            c.drawColor(Color.BLACK);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            if (cur != null) {
                p.setAlpha(Math.round(curA * 255));
                c.drawBitmap(cur, matrix(cur, curD, curT, curFit, c.getWidth(), c.getHeight()), p);
            }
            if (nxt != null) {
                p.setAlpha(Math.round(nxtA * 255));
                c.drawBitmap(nxt, matrix(nxt, nxtD, nxtT, nxtFit, c.getWidth(), c.getHeight()), p);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { surface.getHolder().unlockCanvasAndPost(c); } catch (Exception ignored) {}
        }
    }

    private static Matrix matrix(Bitmap b, float[] d, float t, boolean fit, float vw, float vh) {
        float base = fit ? Math.min(vw / b.getWidth(), vh / b.getHeight()) : Math.max(vw / b.getWidth(), vh / b.getHeight());
        float z = d[0] + (d[1] - d[0]) * t;
        float dx = (d[2] + (d[4] - d[2]) * t) * vw, dy = (d[3] + (d[5] - d[3]) * t) * vh;
        float s = base * z;
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((vw - b.getWidth() * s) / 2 + dx, (vh - b.getHeight() * s) / 2 + dy);
        return m;
    }

    // The picture's matrix at drift time t: cover (or fit) the screen, then zoom and pan by Config.drift.
    private void place(ImageView v, Bitmap b, float[] d, float t, boolean fit) {
        float vw = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        float vh = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        v.setImageMatrix(matrix(b, d, t, fit, vw, vh));
    }

    private int px(int v) { return Math.round(v * scale); }

    private TextView text(Context c, int size, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setShadowLayer(px(8), 0, px(2), 0x99000000);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private TextClock clock(Context c, String pattern, int size, int color, android.graphics.Typeface tf) {
        TextClock t = new TextClock(c);
        t.setFormat12Hour(pattern);
        t.setFormat24Hour(pattern);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setShadowLayer(px(10), 0, px(2), 0x99000000);
        if (tf != null) t.setTypeface(tf);
        return t;
    }
}
