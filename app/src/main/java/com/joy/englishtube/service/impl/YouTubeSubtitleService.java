package com.joy.englishtube.service.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.SubtitleService;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 3-tier fallback fetcher per SRS R-01:
 *   1. timedtext?lang=en&v=ID
 *   2. timedtext?lang=en&kind=asr&v=ID  (auto-generated)
 *   3. Parse the watch HTML page for {@code playerCaptionsTracklistRenderer},
 *      then GET the resolved baseUrl.
 *
 * Throws {@link SubtitleUnavailableException} only if step 3 confirms there
 * is no English caption track at all. Throws {@link FetchFailedException} if
 * a track exists but every download attempt produces empty/invalid XML — the
 * caller (PlayerActivity) then prompts the user to upload an SRT (SRS §1.5).
 */
public class YouTubeSubtitleService implements SubtitleService {

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

        // Tier 1: manual EN
        List<SubtitleLine> lines = tryFetch(timedTextUrl(videoId, false));
        if (!lines.isEmpty()) return lines;

        // Tier 2: auto EN
        lines = tryFetch(timedTextUrl(videoId, true));
        if (!lines.isEmpty()) return lines;

        // Tier 3: HTML fallback to discover signed baseUrl
        String html = tryFetchHtml(watchUrl(videoId));
        if (html == null) {
            throw new FetchFailedException(
                    "Could not load watch page for " + videoId, null);
        }
        CaptionsTrackResolver.Track track = CaptionsTrackResolver.findEnglishTrack(html);
        if (track == null) {
            throw new SubtitleUnavailableException(
                    "No English caption track for " + videoId);
        }
        lines = tryFetch(track.baseUrl);
        if (!lines.isEmpty()) return lines;

        throw new FetchFailedException(
                "Track " + track.languageCode + "/" + track.kind
                        + " exists but body could not be downloaded for " + videoId,
                null);
    }

    private List<SubtitleLine> tryFetch(String url) {
        String xml = tryFetchHtml(url);
        if (xml == null) return java.util.Collections.emptyList();
        return TimedTextParser.parse(xml);
    }

    @Nullable
    private String tryFetchHtml(String url) {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            ResponseBody body = resp.body();
            if (body == null) return null;
            String s = body.string();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
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
