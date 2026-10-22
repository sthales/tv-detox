package com.ansub.tvhome;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

public class Dream extends DreamService {
    private Showcase show;
    private final Runnable playing = this::refresh;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        MainActivity.loadFonts();
        show = new Showcase(this, MainActivity.slideSource(this), MainActivity.regular, MainActivity.semibold);
        setContentView(show);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        OnlinePictures.refresh(this);
        show.start();
        NowPlaying.listen(this, playing);
        refresh();
    }

    private void refresh() {
        android.media.session.MediaController m = NowPlaying.current(this);
        show.setNowPlaying(m == null ? null : NowPlaying.line(m), null);
    }

    @Override
    public void onDreamingStopped() {
        NowPlaying.unlisten(this, playing);
        show.stop();
        super.onDreamingStopped();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (show != null && show.key(e)) return true;
        if (e.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }
}
