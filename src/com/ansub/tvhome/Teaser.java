package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Teaser extends FrameLayout {
    private static final ExecutorService loader = Executors.newSingleThreadExecutor();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float scale;
    private final ImageView art, logo;
    private final TextView title, line;
    private String loadedId;
    private boolean wanted, ready;

    Teaser(Context c, Typeface regular, Typeface semibold) {
        super(c);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        setAlpha(0f);
        setVisibility(View.INVISIBLE);
        setContentDescription("stremio-teaser");
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(art, new LayoutParams(-1, -1));
        View side = new View(c);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xCC000000, 0x55000000, 0x00000000}));
        addView(side, new LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0x22000000, 0xDD000000}));
        addView(bottom, new LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        addView(logo, at(96, 330, 520, 130));
        title = text(c, 56, semibold);
        title.setSingleLine(true);
        addView(title, at(96, 360, 1100, -2));
        line = text(c, 28, regular);
        addView(line, at(96, 480, 1200, -2));
    }

    void show(boolean on) {
        wanted = on;
        if (on) prepare();
        if (on && !ready) return;
        if (on) setVisibility(View.VISIBLE);
        animate().cancel();
        animate().alpha(on ? 1f : 0f).setDuration(280).withEndAction(() -> {
            if (!wanted) setVisibility(View.INVISIBLE);
        }).start();
    }

    private void prepare() {
        List<Stremio.Item> watching = Stremio.continueWatching(getContext());
        if (watching.isEmpty()) {
            ready = false;
            return;
        }
        final Stremio.Item m = watching.get(0);
        if (m.id.equals(loadedId)) return;
        loadedId = m.id;
        ready = false;
        title.setText(m.name);
        title.setVisibility(View.VISIBLE);
        logo.setImageDrawable(null);
        line.setText((m.under == null ? "" : m.under + "  ·  ") + "▲  " + Strings.t("Continue watching"));
        final Context c = getContext().getApplicationContext();
        final int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        loader.execute(() -> {
            final Bitmap bg = Stremio.image(c, m.background != null ? m.background : m.poster, w, h, false);
            final Bitmap lg = Stremio.image(c, m.logo, px(520), px(130), true);
            ui.post(() -> {
                if (!m.id.equals(loadedId) || bg == null) return;
                art.setImageBitmap(bg);
                if (lg != null) {
                    logo.setImageBitmap(lg);
                    title.setVisibility(View.INVISIBLE);
                }
                ready = true;
                if (wanted) show(true);
            });
        });
    }

    private TextView text(Context c, int size, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(px(8), 0, px(2), 0x99000000);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private LayoutParams at(int x, int y, int w, int h) {
        LayoutParams p = new LayoutParams(w < 0 ? w : px(w), h < 0 ? h : px(h));
        p.leftMargin = px(x);
        p.topMargin = px(y);
        return p;
    }

    private int px(int v) { return Math.round(v * scale); }
}
