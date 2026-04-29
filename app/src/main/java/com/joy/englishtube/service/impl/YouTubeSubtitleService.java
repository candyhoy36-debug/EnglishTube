package com.joy.englishtube.service.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.SubtitleService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 3-tier fallback fetcher per SRS R-01:
 * <ol>
 *   <li>{@code timedtext?lang=en&v=ID} (manual EN)</li>
 *   <li>{@code timedtext?lang=en&kind=asr&v=ID} (auto-generated EN)</li>
 *   <li>Parse the watch HTML page for {@code playerCaptionsTracklistRenderer},
 *       then GET the resolved baseUrl. The baseUrl returned by YouTube is
 *       sometimes host-relative ({@code /api/timedtext?...}) which OkHttp
 *       refuses to parse, so we run it through {@link #absolutize(String)}
 *       first.</li>
 * </ol>
 *
 * Throws {@link SubtitleUnavailableException} only when step 3 confirms there
 * is no English caption track at all. Throws {@link FetchFailedException} if
 * a track exists but every download attempt produces empty/unparseable body —
 * the caller (PlayerActivity) then prompts the user to upload an SRT
 * (SRS §1.5).
 *
 * <p>{@link TimedTextParser} understands both the legacy {@code <text>} format
 * ({@code fmt=srv1}) and the current {@code <p>} format ({@code fmt=srv3}),
 * so we never explicitly request a format — whatever YouTube serves will be
 * parsed.</p>
 */
public class YouTubeSubtitleService implements SubtitleService {

    private static final String TAG = "SubtitleService";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private final OkHttpClient client;

    public YouTubeSubtitleService(@NonNull OkHttpClient client) {
        this.client = client;
    }

    @Override
    @NonNull
    public List<SubtitleLine> fetch(String videoId)
            throws SubtitleUnavailableException, FetchFailedException {

        Log.d(TAG, "fetch start videoId=" + videoId);

        // Tier 1: manual EN
        List<SubtitleLine> lines = tryFetch(timedTextUrl(videoId, false));
        Log.d(TAG, "tier1 lines=" + lines.size());
        if (!lines.isEmpty()) return lines;

        // Tier 2: auto EN
        lines = tryFetch(timedTextUrl(videoId, true));
        Log.d(TAG, "tier2 lines=" + lines.size());
        if (!lines.isEmpty()) return lines;

        // Tier 3: HTML fallback to discover signed baseUrl
        String html = simpleGet(watchUrl(videoId));
        Log.d(TAG, "tier3 html payload "
                + (html == null ? "null" : html.length() + " chars"));
        if (html == null) {
            throw new FetchFailedException(
                    "Could not load watch page for " + videoId, null);
        }
        CaptionsTrackResolver.Track track =
                CaptionsTrackResolver.findEnglishTrack(html);
        Log.d(TAG, "tier3 resolved track=" + describe(track));
        if (track == null) {
            throw new SubtitleUnavailableException(
                    "No English caption track for " + videoId);
        }
        lines = tryFetch(absolutize(track.baseUrl));
        Log.d(TAG, "tier3 download lines=" + lines.size());
        if (!lines.isEmpty()) return lines;

        throw new FetchFailedException(
                "Track " + track.languageCode + "/" + track.kind
                        + " exists but body could not be downloaded for "
                        + videoId, null);
    }

    @NonNull
    private List<SubtitleLine> tryFetch(@Nullable String url) {
        if (url == null || url.isEmpty()) return Collections.emptyList();
        String body;
        try {
            body = simpleGet(url);
        } catch (IllegalArgumentException badUrl) {
            // OkHttp throws this for malformed URLs — swallow so the next
            // tier of the resolver can still run.
            Log.w(TAG, "tryFetch bad URL: " + url, badUrl);
            return Collections.emptyList();
        }
        if (body == null) return Collections.emptyList();
        List<SubtitleLine> lines = TimedTextParser.parse(body);
        if (lines.isEmpty()) {
            int len = body.length();
            String sample = body.substring(0, Math.min(200, len))
                    .replaceAll("\\s+", " ");
            Log.w(TAG, "tryFetch unparsed body=" + len + "B sample=\"" + sample + "\"");
        }
        return lines;
    }

    @Nullable
    private String simpleGet(String url) {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.w(TAG, "GET " + url + " HTTP " + resp.code());
                return null;
            }
            ResponseBody body = resp.body();
            if (body == null) return null;
            String s = body.string();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            Log.w(TAG, "GET " + url + " IO", e);
            return null;
        }
    }

    /**
     * Promotes schemeless / host-relative caption URLs to absolute youtube.com
     * URLs so OkHttp can parse them. Returns the input unchanged if it already
     * has a scheme.
     */
    static String absolutize(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://www.youtube.com" + url;
        return url;
    }

    private static String describe(@Nullable CaptionsTrackResolver.Track t) {
        if (t == null) return "null";
        return "{lang=" + t.languageCode + ", kind=" + t.kind
                + ", url=" + (t.baseUrl == null ? "null"
                        : t.baseUrl.substring(0, Math.min(120, t.baseUrl.length())))
                + "…}";
    }

    private static String timedTextUrl(String videoId, boolean asr) {
        StringBuilder sb = new StringBuilder("https://www.youtube.com/api/timedtext?lang=en");
        if (asr) sb.append("&kind=asr");
        sb.append("&v=").append(videoId);
        return sb.toString();
    }

    private static String watchUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
