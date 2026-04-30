package com.joy.englishtube.ui.player;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.joy.englishtube.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Sprint 4 Dictionary BottomSheet.
 *
 * Shown when the user long-presses a word in a subtitle row. Loads a
 * Google Search results page for {@code define <word>} inside an embedded
 * WebView so the user can read a definition + usage examples without
 * leaving the player. The host activity handles auto-pausing the video
 * via {@link OnDismissListener}.
 *
 * Why Google Search instead of a real dictionary API: matches the SRS
 * ("tra từ qua Google Search") and avoids needing an API key for the
 * MVP; Sprint 6 Settings can later swap the lookup engine.
 */
public class DictionaryBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DictionaryBottomSheet";
    private static final String ARG_WORD = "word";

    @NonNull
    public static DictionaryBottomSheet newInstance(@NonNull String word) {
        DictionaryBottomSheet f = new DictionaryBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_WORD, word);
        f.setArguments(args);
        return f;
    }

    /** Optional listener — host activity uses it to pause/resume video. */
    public interface OnDismissListener {
        void onDictionaryDismissed();
    }

    @Nullable
    private OnDismissListener dismissListener;
    @Nullable
    private WebView webView;

    public void setOnDismissListener(@Nullable OnDismissListener l) {
        this.dismissListener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_dictionary, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String word = requireArguments().getString(ARG_WORD, "");

        TextView tvWord = view.findViewById(R.id.tv_dictionary_word);
        ImageButton btnSave = view.findViewById(R.id.btn_dictionary_save);
        ImageButton btnExternal = view.findViewById(R.id.btn_dictionary_open_external);
        ProgressBar progress = view.findViewById(R.id.dictionary_progress);
        webView = view.findViewById(R.id.webview_dictionary);

        tvWord.setText(word);

        // Save-word stub. Persisting the looked-up word into the user's
        // vocab list is planned for a later sprint (likely alongside the
        // history/bookmark store), so for now we just acknowledge the tap.
        btnSave.setOnClickListener(v ->
                Toast.makeText(v.getContext(),
                        R.string.dictionary_save_not_yet,
                        Toast.LENGTH_SHORT).show());

        String url = buildLookupUrl(word);
        btnExternal.setOnClickListener(v -> openExternal(url));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(newProgress);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String href) {
                // Keep navigation inside the BottomSheet's WebView for the
                // Google results page; let the system handle anything else
                // (e.g. dictionary site links the user actually clicks
                // intentionally — they get full Chrome).
                return false;
            }
        });

        webView.loadUrl(url);
    }

    @NonNull
    private static String buildLookupUrl(@NonNull String word) {
        String encoded;
        try {
            encoded = URLEncoder.encode("define " + word, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is mandatory on every JVM; this branch is unreachable
            // but the API forces us to handle it.
            encoded = word;
        }
        return "https://www.google.com/search?q=" + encoded;
    }

    private void openExternal(@NonNull String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "open external failed", e);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) return;
        FrameLayout sheet = ((BottomSheetDialog) dialog).findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        // Make the sheet take up most of the screen so the embedded
        // WebView is actually readable. Without this the sheet opens at
        // its peek height (just the header), which is what the user
        // reported in review.
        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        int target = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.92f);
        lp.height = target;
        sheet.setLayoutParams(lp);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setPeekHeight(target, false);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) dismissListener.onDictionaryDismissed();
    }
}
