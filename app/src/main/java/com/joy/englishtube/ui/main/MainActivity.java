package com.joy.englishtube.ui.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
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
                return interceptOrLoad(request.getUrl().toString());
            }

            // Legacy overload for older WebViews; both delegate to the same logic.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return interceptOrLoad(url);
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
     * @return true if the URL was intercepted (PlayerActivity opened); the WebView
     *         should NOT load it. False to let the WebView handle the URL itself.
     */
    private boolean interceptOrLoad(@Nullable String url) {
        String videoId = VideoIdExtractor.extractStandardWatch(url);
        if (videoId != null) {
            startActivity(PlayerActivity.intent(this, videoId));
            return true;
        }
        return false;
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
        if (id == R.id.action_history) {
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
