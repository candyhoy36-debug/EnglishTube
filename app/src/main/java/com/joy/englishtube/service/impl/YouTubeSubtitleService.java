package com.joy.englishtube.service.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.SubtitleService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Multi-tier subtitle fetcher (SRS R-01).
 *
 * <p>YouTube has progressively tightened the legacy {@code /api/timedtext}
 * endpoint and adds {@code &fmt=srv3} or {@code &pot=...} requirements to the
 * "fresh" baseUrls inside the watch page. To cope, we try several entry
 * points in order of reliability and, for every captionTrack we resolve, we
 * attempt the download with several format hints because the legacy XML
 * format the parser understands is no longer the default.</p>
 *
 * <ol>
 *   <li><b>Innertube</b> — POST to {@code /youtubei/v1/player} with a fake
 *       WEB client context. This returns the same
 *       {@code playerCaptionsTracklistRenderer} block the watch HTML carries,
 *       but with freshly-signed baseUrls that are far more likely to download.</li>
 *   <li><b>Watch-page HTML scrape</b> — same idea but parses the inline
 *       {@code ytInitialPlayerResponse}.</li>
 *   <li><b>Direct legacy URL</b> — last-resort {@code /api/timedtext?lang=en}
 *       with explicit {@code fmt=srv1}.</li>
 * </ol>
 *
 * Throws {@link SubtitleUnavailableException} only if every resolver agrees
 * the video has no English caption track. Throws {@link FetchFailedException}
 * if a track exists but every download attempt produces empty XML — caller
 * (PlayerActivity) then prompts the user to upload an SRT (SRS §1.5).
 */
public class YouTubeSubtitleService implements SubtitleService {

    private static final String TAG = "SubtitleService";

    /** Public web Innertube key — same one the youtube.com page uses. */
    private static final String INNERTUBE_KEY =
            "AIzaSyAO_FGJTwqd-7VmZsKNRoxrqUiL0VfKvVM";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final String INNERTUBE_CLIENT_VERSION = "2.20240814.00.00";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;

    public YouTubeSubtitleService(@NonNull OkHttpClient client) {
        this.client = client;
    }

    @Override
    @NonNull
    public List<SubtitleLine> fetch(String videoId)
            throws SubtitleUnavailableException, FetchFailedException {

        boolean trackSeen = false;
        Log.d(TAG, "fetch start videoId=" + videoId);

        // ---- Tier 1: Innertube --------------------------------------------
        String innertubeJson = innertubePlayer(videoId);
        Log.d(TAG, "tier1 innertube payload "
                + (innertubeJson == null ? "null" : innertubeJson.length() + " chars"));
        if (innertubeJson != null) {
            CaptionsTrackResolver.Track track =
                    CaptionsTrackResolver.findEnglishTrack(innertubeJson);
            Log.d(TAG, "tier1 resolved track=" + describe(track));
            if (track != null) {
                trackSeen = true;
                List<SubtitleLine> lines = downloadCaptionTrack(track.baseUrl);
                Log.d(TAG, "tier1 download lines=" + lines.size());
                if (!lines.isEmpty()) return lines;
            }
        }

        // ---- Tier 2: HTML scrape ------------------------------------------
        String html = simpleGet(watchUrl(videoId));
        Log.d(TAG, "tier2 html payload "
                + (html == null ? "null" : html.length() + " chars"));
        if (html != null) {
            CaptionsTrackResolver.Track track =
                    CaptionsTrackResolver.findEnglishTrack(html);
            Log.d(TAG, "tier2 resolved track=" + describe(track));
            if (track != null) {
                trackSeen = true;
                List<SubtitleLine> lines = downloadCaptionTrack(track.baseUrl);
                Log.d(TAG, "tier2 download lines=" + lines.size());
                if (!lines.isEmpty()) return lines;
            }
        }

        // ---- Tier 3: legacy direct URL ------------------------------------
        for (String url : new String[] {
                legacyTimedText(videoId, false),
                legacyTimedText(videoId, true) }) {
            List<SubtitleLine> lines = downloadCaptionTrack(url);
            Log.d(TAG, "tier3 " + url + " → lines=" + lines.size());
            if (!lines.isEmpty()) {
                trackSeen = true;
                return lines;
            }
        }

        if (trackSeen) {
            Log.w(TAG, "all tiers exhausted, track seen but body empty for " + videoId);
            throw new FetchFailedException(
                    "Caption track exists but body could not be downloaded for "
                            + videoId, null);
        }
        Log.w(TAG, "no English caption track for " + videoId);
        throw new SubtitleUnavailableException(
                "No English caption track for " + videoId);
    }

    private static String describe(@Nullable CaptionsTrackResolver.Track t) {
        if (t == null) return "null";
        return "{lang=" + t.languageCode + ", kind=" + t.kind
                + ", url=" + (t.baseUrl == null ? "null"
                        : t.baseUrl.substring(0, Math.min(120, t.baseUrl.length())))
                + "…}";
    }

    // -- Innertube ------------------------------------------------------------

    @Nullable
    private String innertubePlayer(String videoId) {
        String body = "{"
                + "\"context\":{"
                +   "\"client\":{"
                +     "\"clientName\":\"WEB\","
                +     "\"clientVersion\":\"" + INNERTUBE_CLIENT_VERSION + "\","
                +     "\"hl\":\"en\","
                +     "\"gl\":\"US\""
                +   "}"
                + "},"
                + "\"videoId\":\"" + videoId + "\""
                + "}";

        Request req = new Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key="
                        + INNERTUBE_KEY + "&prettyPrint=false")
                .post(RequestBody.create(body, JSON))
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", INNERTUBE_CLIENT_VERSION)
                .header("Content-Type", "application/json")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.w(TAG, "innertube HTTP " + resp.code());
                return null;
            }
            ResponseBody rb = resp.body();
            if (rb == null) return null;
            String s = rb.string();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            Log.w(TAG, "innertube IO", e);
            return null;
        }
    }

    // -- Caption track download ----------------------------------------------

    /**
     * Downloads & parses a captions URL. YouTube has been making
     * {@code fmt=srv3} the default, which produces a different XML layout
     * than {@link TimedTextParser} expects, so we always try forcing
     * {@code fmt=srv1} (legacy {@code <text>} format) first when the URL
     * doesn't explicitly request a format yet.
     */
    @NonNull
    private List<SubtitleLine> downloadCaptionTrack(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return Collections.emptyList();

        // YouTube sometimes serves caption baseUrls as schemeless or even
        // host-relative paths ("/api/timedtext?..."), which OkHttp rejects.
        String baseUrl = absolutize(rawUrl);

        try {
            // Always try the legacy format we can parse.
            String forced = forceFmt(baseUrl, "srv1");
            List<SubtitleLine> lines = parse(simpleGet(forced));
            if (!lines.isEmpty()) return lines;

            // Some baseUrls reject the override; fall back to whatever the URL
            // originally requested.
            if (!forced.equals(baseUrl)) {
                lines = parse(simpleGet(baseUrl));
                if (!lines.isEmpty()) return lines;
            }

            // Last resort: try stripping the format hint entirely.
            String stripped = stripParam(baseUrl, "fmt");
            if (!stripped.equals(baseUrl) && !stripped.equals(forced)) {
                lines = parse(simpleGet(stripped));
                if (!lines.isEmpty()) return lines;
            }
            return Collections.emptyList();
        } catch (IllegalArgumentException badUrl) {
            // OkHttp throws this for malformed URLs — swallow so the next
            // tier of the resolver can still run.
            Log.w(TAG, "download bad URL: " + baseUrl, badUrl);
            return Collections.emptyList();
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

    /** Replaces {@code &fmt=...} (or appends one) with the supplied value. */
    private static String forceFmt(String url, String fmt) {
        String stripped = stripParam(url, "fmt");
        char joiner = stripped.indexOf('?') < 0 ? '?' : '&';
        return stripped + joiner + "fmt=" + fmt;
    }

    private static String stripParam(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) return url;
        StringBuilder out = new StringBuilder(url.substring(0, q + 1));
        boolean first = true;
        for (String pair : url.substring(q + 1).split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (k.equals(name)) continue;
            if (!first) out.append('&');
            out.append(pair);
            first = false;
        }
        // Drop trailing '?' if no params survived.
        if (out.charAt(out.length() - 1) == '?') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static List<SubtitleLine> parse(@Nullable String xml) {
        if (xml == null) return Collections.emptyList();
        return TimedTextParser.parse(xml);
    }

    // -- HTTP helper ----------------------------------------------------------

    @Nullable
    private String simpleGet(String url) {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
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

    private static String legacyTimedText(String videoId, boolean asr) {
        StringBuilder sb = new StringBuilder("https://www.youtube.com/api/timedtext?lang=en");
        if (asr) sb.append("&kind=asr");
        sb.append("&v=").append(videoId).append("&fmt=srv1");
        return sb.toString();
    }

    private static String watchUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
