package com.ansub.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Shelf extends FrameLayout {
    private static final int POSTER_W = 200, POSTER_H = 296, GAP = 32, ROW1 = 380, ROW2 = 820, LABEL = 52;
    private static final ExecutorService loader = Executors.newSingleThreadExecutor();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float scale;
    private final Typeface regular, semibold;
    private final Runnable close;
    private final ImageView[] heroes = new ImageView[2];
    private final ImageView logo;
    private final TextView title, meta, description;
    private final FrameLayout rows;
    private final List<View> firstRow = new ArrayList<>(), lastRow = new ArrayList<>();
    private View rowOne;
    private int heroFront, gen;
    private Stremio.Item shown;
    private boolean down;

    Shelf(Context c, List<Stremio.Item> watching, List<Stremio.Item> popular, Typeface regular, Typeface semibold, Runnable close) {
        super(c);
        this.regular = regular;
        this.semibold = semibold;
        this.close = close;
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        setBackgroundColor(Color.BLACK);
        setContentDescription("stremio-shelf");
        for (int i = 0; i < 2; i++) {
            heroes[i] = new ImageView(c);
            heroes[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroes[i].setAlpha(0f);
            addView(heroes[i], new LayoutParams(-1, -1));
        }
        View side = new View(c);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xEE000000, 0x77000000, 0x00000000}));
        addView(side, new LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0x33000000, 0xEE000000}));
        addView(bottom, new LayoutParams(-1, -1));

        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        style(clock, 40, Color.WHITE, semibold);
        LayoutParams cp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.setMargins(0, px(60), px(96), 0);
        addView(clock, cp);

        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        addView(logo, at(96, 70, 480, 120));
        title = style(new TextView(c), 64, Color.WHITE, semibold);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        addView(title, at(96, 80, 1100, -2));
        meta = style(new TextView(c), 26, 0xEEFFFFFF, regular);
        addView(meta, at(96, 205, 1200, -2));
        description = style(new TextView(c), 24, 0xD9FFFFFF, regular);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setLineSpacing(px(6), 1f);
        addView(description, at(96, 252, 900, -2));

        rows = new FrameLayout(c);
        rows.setClipChildren(false);
        addView(rows, new LayoutParams(-1, -1));
        boolean both = !watching.isEmpty() && !popular.isEmpty();
        if (!watching.isEmpty()) rowOne = row(Strings.t("Continue watching"), watching, ROW1, firstRow);
        if (!popular.isEmpty()) {
            View r = row(Strings.t("Popular on Stremio"), popular, both ? ROW2 : ROW1, both ? lastRow : firstRow);
            if (!both) rowOne = r;
        }
        if (!both) lastRow.addAll(firstRow);
        if (rowOne == null) {
            TextView empty = style(new TextView(c), 28, 0xCCFFFFFF, regular);
            empty.setText(Strings.t("Loading…"));
            addView(empty, at(96, ROW1, -2, -2));
        }
    }

    boolean focusFirst() {
        if (firstRow.isEmpty()) return false;
        firstRow.get(0).requestFocus();
        return true;
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean press = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!press) close.run();
            return true;
        }
        View f = findFocus();
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && (f == null || lastRow.contains(f))) {
            if (press) close.run();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP && (f == null || firstRow.contains(f))) return true;
        return false;
    }

    private View row(String label, List<Stremio.Item> items, int top, List<View> posters) {
        Context c = getContext();
        LinearLayout block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClipChildren(false);
        TextView l = style(new TextView(c), 26, Color.WHITE, semibold);
        l.setText(label);
        l.setPadding(px(96), 0, 0, 0);
        block.addView(l, new LinearLayout.LayoutParams(-2, px(LABEL)));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(96 - GAP / 2), px(20), px(96), 0);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout line = new LinearLayout(c);
        line.setClipChildren(false);
        for (Stremio.Item m : items) line.addView(poster(m, posters), new LinearLayout.LayoutParams(px(POSTER_W + GAP), -2));
        scroll.addView(line);
        block.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        rows.addView(block, at(0, top - 20, -1, -2));
        return block;
    }

    private View poster(final Stremio.Item m, List<View> posters) {
        Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        final FrameLayout card = new FrameLayout(c);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(14));
        bg.setColor(0x22FFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setContentDescription("shelf:" + m.id);
        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new LayoutParams(-1, -1));
        TextView name = style(new TextView(c), 22, 0xCCFFFFFF, regular);
        name.setText(m.name);
        name.setGravity(Gravity.CENTER);
        name.setPadding(px(12), 0, px(12), 0);
        card.addView(name, new LayoutParams(-1, -1));
        col.addView(card, new LinearLayout.LayoutParams(px(POSTER_W), px(POSTER_H)));

        final LinearLayout under = new LinearLayout(c);
        under.setOrientation(LinearLayout.VERTICAL);
        under.setVisibility(View.INVISIBLE);
        if (m.progress >= 0) {
            FrameLayout track = new FrameLayout(c);
            GradientDrawable t = new GradientDrawable();
            t.setCornerRadius(px(3));
            t.setColor(0x44FFFFFF);
            track.setBackground(t);
            View fill = new View(c);
            GradientDrawable fd = new GradientDrawable();
            fd.setCornerRadius(px(3));
            fd.setColor(Color.WHITE);
            fill.setBackground(fd);
            track.addView(fill, new LayoutParams(Math.round(px(POSTER_W - 12) * m.progress), -1));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(px(POSTER_W - 12), px(6));
            tp.gravity = Gravity.CENTER_HORIZONTAL;
            under.addView(track, tp);
        }
        TextView sub = style(new TextView(c), 20, 0xDDFFFFFF, regular);
        sub.setGravity(Gravity.CENTER);
        sub.setText(m.under == null ? "" : m.under);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = px(8);
        under.addView(sub, sp);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(px(POSTER_W), -2);
        up.topMargin = px(POSTER_H / 20 + 14);
        col.addView(under, up);

        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? px(20) : 0).setDuration(140).start();
            bg.setStroke(has ? px(4) : 0, Color.WHITE);
            under.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            if (has) focused(m, lastRow.contains(v) && !firstRow.contains(v));
        });
        card.setOnClickListener(v -> open(m));
        posters.add(card);
        load(m.poster, px(POSTER_W), px(POSTER_H), false, b -> {
            img.setImageBitmap(b);
            name.setVisibility(View.GONE);
        });
        return col;
    }

    private void focused(Stremio.Item m, boolean second) {
        if (second != down) {
            down = second;
            rows.animate().translationY(second ? -px(ROW2 - ROW1) : 0).setDuration(220).start();
            rowOne.animate().alpha(second ? 0f : 1f).setDuration(220).start();
        }
        if (m == shown) return;
        shown = m;
        SpannableStringBuilder s = new SpannableStringBuilder();
        if (m.rating != null && !m.rating.isEmpty()) {
            s.append(" IMDb ").append(m.rating).append(" ");
            s.setSpan(new BackgroundColorSpan(0xFFF5C518), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(Color.BLACK), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append("  ");
        }
        List<String> parts = new ArrayList<>();
        if (m.year != null && !m.year.isEmpty()) parts.add(m.year);
        if (m.genres != null && !m.genres.isEmpty()) parts.add(m.genres);
        s.append(TextUtils.join(" · ", parts));
        if (m.episode != null) {
            int from = s.length();
            s.append(parts.isEmpty() ? "" : " · ").append(m.episode);
            s.setSpan(new StyleSpan(Typeface.BOLD), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        meta.setText(s);
        description.setText(m.description == null ? "" : m.description);
        title.setText(m.name);
        title.setVisibility(View.VISIBLE);
        logo.setImageDrawable(null);
        final int g = ++gen;
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        load(m.logo, px(480), px(120), true, b -> {
            if (g != gen) return;
            logo.setImageBitmap(b);
            title.setVisibility(View.INVISIBLE);
        });
        load(m.background != null ? m.background : m.poster, w, h, false, b -> {
            if (g != gen) return;
            ImageView next = heroes[1 - heroFront], old = heroes[heroFront];
            next.setImageBitmap(b);
            next.animate().alpha(1f).setDuration(300).start();
            old.animate().alpha(0f).setDuration(300).start();
            heroFront = 1 - heroFront;
        });
    }

    private void open(Stremio.Item m) {
        Context c = getContext();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(m.link)).setPackage(Stremio.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(Stremio.PACKAGE);
            if (launch != null) c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        close.run();
    }

    private interface Loaded { void run(Bitmap b); }

    private void load(final String url, final int w, final int h, final boolean fit, final Loaded done) {
        if (url == null) return;
        final Context c = getContext().getApplicationContext();
        loader.execute(() -> {
            final Bitmap b = Stremio.image(c, url, w, h, fit);
            if (b != null) ui.post(() -> { if (isAttachedToWindow()) done.run(b); });
        });
    }

    private <T extends TextView> T style(T t, int size, int color, Typeface tf) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
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
