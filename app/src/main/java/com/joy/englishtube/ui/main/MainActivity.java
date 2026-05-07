package com.joy.englishtube.ui.main;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.joy.englishtube.R;
import com.joy.englishtube.ui.bookmark.BookmarkActivity;
import com.joy.englishtube.ui.history.HistoryActivity;
import com.joy.englishtube.ui.player.PlayerActivity;
import com.joy.englishtube.ui.settings.SettingsActivity;
import com.joy.englishtube.util.VideoIdExtractor;

/**
 * Sprint 1: WebView host that loads m.youtube.com.
 *
 * Top bar (toolbar) — overflow menu: History / Bookmarks / Settings.
 * Nav row — Back / Forward / Reload / Home.
 * Body  — WebView. Any URL matching {@code (m|www).youtube.com/watch?v=ID}
 *         is intercepted and routed to {@link PlayerActivity} instead of
 *         being loaded by the WebView.
 *
 * WebView state is preserved across configuration changes via
 * {@link WebView#saveState(Bundle)} / {@link WebView#restoreState(Bundle)}.
 */
public class MainActivity extends AppCompatActivity {

    private static final String YOUTUBE_HOME = "https://m.youtube.com/";
    private static final String STATE_WEBVIEW = "state_webview";

    private WebView webView;
    private ProgressBar progressBar;
    private ImageButton btnBack;
    private ImageButton btnForward;

    /**
     * Tracks the most recently routed videoId so we don't double-fire when
     * both {@code shouldOverrideUrlLoading} and {@code doUpdateVisitedHistory}
     * see the same URL during a single navigation. Cleared in {@code onResume}
     * (when the user comes back from PlayerActivity) and whenever the WebView
     * navigates to a non-watch URL.
     */
    @Nullable
    private String lastInterceptedVideoId;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        ImageButton btnReload = findViewById(R.id.btn_reload);
        ImageButton btnHome = findViewById(R.id.btn_home);

        configureWebView();

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        btnReload.setOnClickListener(v -> webView.reload());
        btnHome.setOnClickListener(v -> webView.loadUrl(YOUTUBE_HOME));

        if (savedInstanceState != null) {
            Bundle webState = savedInstanceState.getBundle(STATE_WEBVIEW);
            if (webState != null) {
                webView.restoreState(webState);
            } else {
                webView.loadUrl(YOUTUBE_HOME);
            }
        } else {
            webView.loadUrl(YOUTUBE_HOME);
        }

        // Modern back-press handling: defer to WebView history first, then default behaviour.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        updateNavButtons();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return interceptOrLoad(view, request.getUrl().toString(), false);
            }

            // Legacy overload for older WebViews; both delegate to the same logic.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return interceptOrLoad(view, url, false);
            }

            /*
             * YouTube mobile web uses History API (pushState) for in-app
             * navigation, which means clicking a video DOES NOT trigger
             * shouldOverrideUrlLoading. doUpdateVisitedHistory is fired even
             * for pushState transitions, so we hook it here too.
             */
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                interceptOrLoad(view, url, true);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                updateNavButtons();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
    }

    /**
     * Routes watch URLs to PlayerActivity from either path (hard nav via
     * shouldOverrideUrlLoading, or soft nav via doUpdateVisitedHistory).
     *
     * @param fromHistoryUpdate true if this was triggered by a pushState
     *     transition. In that case the WebView has already changed URL state,
     *     so we pop the entry to keep the WebView parked on the listing page.
     * @return true if the URL was intercepted (only meaningful for
     *         shouldOverrideUrlLoading; doUpdateVisitedHistory ignores the
     *         return value).
     */
    private boolean interceptOrLoad(WebView view, @Nullable String url, boolean fromHistoryUpdate) {
        String videoId = VideoIdExtractor.extractStandardWatch(url);
        if (videoId == null) {
            // Reset whenever the user moves to a non-watch URL so the next
            // visit to the same video opens PlayerActivity again.
            lastInterceptedVideoId = null;
            return false;
        }
        if (videoId.equals(lastInterceptedVideoId)) return true;
        lastInterceptedVideoId = videoId;

        if (fromHistoryUpdate) {
            // Pop the watch URL out of WebView history so the user lands back
            // on the listing/search page when they exit PlayerActivity.
            view.stopLoading();
            if (view.canGoBack()) view.goBack();
        }
        startActivity(PlayerActivity.intent(this, videoId));
        return true;
    }

    private void updateNavButtons() {
        btnBack.setEnabled(webView.canGoBack());
        btnBack.setAlpha(webView.canGoBack() ? 1f : 0.4f);
        btnForward.setEnabled(webView.canGoForward());
        btnForward.setAlpha(webView.canGoForward() ? 1f : 0.4f);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_paste_url) {
            showPasteUrlDialog();
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_bookmarks) {
            startActivity(new Intent(this, BookmarkActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Opens a dialog where the user can paste a YouTube URL and tap Phát to
     * jump straight into PlayerActivity. The EditText is pre-filled with
     * clipboard contents when the clipboard already holds a recognisable
     * YouTube URL — otherwise the user pastes manually.
     */
    private void showPasteUrlDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.paste_url_dialog_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);

        String clipUrl = readYoutubeUrlFromClipboard();
        if (clipUrl != null) {
            input.setText(clipUrl);
            input.setSelection(clipUrl.length());
        }

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 20);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.paste_url_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.paste_url_play, null) // overridden below
                .setNegativeButton(R.string.paste_url_cancel, (d, w) -> d.dismiss())
                .create();
        // Override Phát click so we can validate without auto-dismissing on
        // failure (the default positive-button listener always dismisses).
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    String videoId = VideoIdExtractor.extract(raw);
                    if (videoId == null) {
                        input.setError(getString(R.string.paste_url_invalid));
                        Toast.makeText(this, R.string.paste_url_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    startActivity(PlayerActivity.intent(this, videoId));
                }));
        dialog.show();
    }

    @Nullable
    private String readYoutubeUrlFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null) return null;
        String s = text.toString().trim();
        return VideoIdExtractor.extract(s) != null ? s : null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle webState = new Bundle();
        webView.saveState(webState);
        outState.putBundle(STATE_WEBVIEW, webState);
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // Allow the same video to re-route on next click after returning from
        // PlayerActivity (otherwise we'd ignore the navigation as a duplicate).
        lastInterceptedVideoId = null;
        updateNavButtons();
    }

    @Override
    protected void onDestroy() {
        // Detach the WebView from the layout before destroy() to avoid leaks.
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(new WebViewClient());
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
