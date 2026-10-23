package com.example.streamshield;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://m.youtube.com/";
    private FrameLayout root;
    private WebView web;
    private LinearLayout toolbar;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private float swipeStartX, swipeStartY;
    private boolean swipeStartedInVideoArea;
    private boolean pipRequested;
    private boolean wasPlaying;
    private boolean pipActive;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        root = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.BLACK);
        toolbar = new LinearLayout(this);
        toolbar.setBackgroundColor(Color.DKGRAY);
        Button back = new Button(this);
        back.setText("‹");
        toolbar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHint("Search YouTube");
        toolbar.addView(search, new LinearLayout.LayoutParams(0, -2, 5));
        Button go = new Button(this);
        go.setText("Go");
        toolbar.addView(go, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(toolbar);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullscreenView != null) { cb.onCustomViewHidden(); return; }
                fullscreenView = view;
                fullscreenCallback = cb;
                toolbar.setVisibility(View.GONE);
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (fullscreenView == null) return;
                root.removeView(fullscreenView);
                fullscreenView = null;
                toolbar.setVisibility(View.VISIBLE);
                web.setVisibility(View.VISIBLE);
                if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
            }
        });
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        go.setOnClickListener(v -> {
            String q = search.getText().toString().trim();
            if (q.isEmpty()) return;
            if (q.startsWith("https://")) web.loadUrl(q);
            else web.loadUrl("https://m.youtube.com/results?search_query=" + android.net.Uri.encode(q));
        });
        if (state == null) web.loadUrl(HOME);
        else web.restoreState(state);
    }

    private int dp(int size) {
        return (int)(size * getResources().getDisplayMetrics().density + 0.5f);
    }

    // Swipe down from the video area to request Android system Picture-in-Picture.
    // Restrict the gesture to the upper video region to preserve normal page scrolling.
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!pipActive && fullscreenView == null) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getRawX();
                swipeStartY = event.getRawY();
                float yInWeb = event.getRawY() - getWebTopOnScreen();
                swipeStartedInVideoArea = yInWeb >= 0 && yInWeb < Math.min(web.getHeight() * 0.48f, dp(340));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && swipeStartedInVideoArea) {
                float dy = event.getRawY() - swipeStartY;
                float dx = Math.abs(event.getRawX() - swipeStartX);
                swipeStartedInVideoArea = false;
                if (dy > dp(125) && dx < dp(100)) {
                    requestPip();
                    return true;
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swipeStartedInVideoArea = false;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private int getWebTopOnScreen() {
        int[] location = new int[2];
        web.getLocationOnScreen(location);
        return location[1];
    }

    private void requestPip() {
        if (Build.VERSION.SDK_INT < 26 ||
            !getPackageManager().hasSystemFeature("android.software.picture_in_picture")) {
            Toast.makeText(this, "Picture-in-Picture is not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }
        web.evaluateJavascript("(function(){var v=document.querySelector('video');return v?(v.paused?'paused':'playing'):'none';})()", result -> {
            if ("\"none\"".equals(result) || "null".equals(result)) {
                Toast.makeText(this, "Start a video before swiping down", Toast.LENGTH_SHORT).show();
                return;
            }
            wasPlaying = "\"playing\"".equals(result);
            pipRequested = true;
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
                if (!enterPictureInPictureMode(params)) {
                    pipRequested = false;
                    Toast.makeText(this, "Picture-in-Picture was declined by Android", Toast.LENGTH_LONG).show();
                }
            } catch (Exception ex) {
                pipRequested = false;
                Toast.makeText(this, "Enable Picture-in-Picture for StreamShield in Android settings", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public void onPictureInPictureModeChanged(boolean enabled, Configuration config) {
        super.onPictureInPictureModeChanged(enabled, config);
        pipActive = enabled;
        toolbar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        if (enabled && wasPlaying) {
            web.postDelayed(() -> {
                if (pipActive) web.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}})()", null);
            }, 350);
        }
        if (!enabled) pipRequested = false;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
