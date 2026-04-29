package com.joy.englishtube.service.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.SubtitleService;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Multi-tier subtitle fetcher (SRS R-01).
 *
 * <ol>
 *   <li><b>NewPipeExtractor</b> — primary path. Wraps the same library
 *       NewPipe uses to scrape YouTube; it solves signature ciphers and
 *       PoToken handshakes that we'd otherwise have to maintain ourselves.
 *       Subtitles come back as a list of {@link SubtitlesStream}; we pick
 *       the best English VTT track and parse it with {@link VttParser}.</li>
 *   <li>{@code timedtext?lang=en&v=ID} (manual EN, legacy fallback)</li>
 *   <li>{@code timedtext?lang=en&kind=asr&v=ID} (auto-generated EN, legacy
 *       fallback)</li>
 *   <li>Watch-page HTML scrape — parses
 *       {@code playerCaptionsTracklistRenderer} from the inline
 *       {@code ytInitialPlayerResponse} blob and downloads the discovered
 *       baseUrl. The baseUrl is sometimes host-relative
 *       ({@code /api/timedtext?...}), so {@link #absolutize(String)} runs
 *       on every URL before OkHttp parses it.</li>
 * </ol>
 *
 * <p>Throws {@link SubtitleUnavailableException} only when every resolver
 * agrees the video has no English caption track. Throws
 * {@link FetchFailedException} when a track is found but every download
 * attempt produces empty/unparseable bodies — the caller (PlayerActivity)
 * then prompts the user to upload an SRT (SRS §1.5).</p>
 *
 * <p>{@link TimedTextParser} understands both the legacy {@code <text>}
 * format ({@code fmt=srv1}) and the current {@code <p>} format
 * ({@code fmt=srv3}); {@link VttParser} understands WebVTT. We never
 * explicitly request a format — whatever the upstream serves will be
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
        boolean trackSeen = false;

        // ---- Tier 0: NewPipeExtractor -------------------------------------
        try {
            List<SubtitleLine> lines = fetchViaNewPipe(videoId);
            Log.d(TAG, "tier0 newpipe lines=" + lines.size());
            if (!lines.isEmpty()) return lines;
            // NewPipe returned an empty cue list for a track that does exist
            // — record so we surface FetchFailed instead of Unavailable.
            trackSeen |= newPipeReportedTrack(videoId);
        } catch (Exception e) {
            // NewPipe throws a deep exception hierarchy (ExtractionException,
            // ParsingException, ReCaptchaException, IOException, …). We log
            // and fall through to the legacy tiers — there's no value in
            // letting a NewPipe failure short-circuit the user.
            Log.w(TAG, "tier0 newpipe failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }

        // ---- Tier 1: legacy direct manual EN ------------------------------
        List<SubtitleLine> lines = tryFetchLegacy(timedTextUrl(videoId, false));
        Log.d(TAG, "tier1 lines=" + lines.size());
        if (!lines.isEmpty()) return lines;

        // ---- Tier 2: legacy direct auto EN --------------------------------
        lines = tryFetchLegacy(timedTextUrl(videoId, true));
        Log.d(TAG, "tier2 lines=" + lines.size());
        if (!lines.isEmpty()) return lines;

        // ---- Tier 3: HTML scrape ------------------------------------------
        String html = simpleGet(watchUrl(videoId));
        Log.d(TAG, "tier3 html payload "
                + (html == null ? "null" : html.length() + " chars"));
        if (html != null) {
            CaptionsTrackResolver.Track track =
                    CaptionsTrackResolver.findEnglishTrack(html);
            Log.d(TAG, "tier3 resolved track=" + describe(track));
            if (track != null) {
                trackSeen = true;
                lines = tryFetchLegacy(absolutize(track.baseUrl));
                Log.d(TAG, "tier3 download lines=" + lines.size());
                if (!lines.isEmpty()) return lines;
            }
        }

        if (trackSeen) {
            throw new FetchFailedException(
                    "Caption track exists but body could not be downloaded for "
                            + videoId, null);
        }
        throw new SubtitleUnavailableException(
                "No English caption track for " + videoId);
    }

    // -- NewPipe tier ---------------------------------------------------------

    @NonNull
    private List<SubtitleLine> fetchViaNewPipe(String videoId) throws Exception {
        StreamExtractor extractor = ServiceList.YouTube
                .getStreamExtractor(watchUrl(videoId));
        extractor.fetchPage();

        // Prefer VTT (what NewPipe normalises to), fall back to whatever
        // YouTube hands back if VTT isn't offered.
        List<SubtitlesStream> tracks = extractor.getSubtitles(MediaFormat.VTT);
        if (tracks == null || tracks.isEmpty()) {
            tracks = extractor.getSubtitlesDefault();
        }
        Log.d(TAG, "tier0 newpipe tracks=" + (tracks == null ? 0 : tracks.size()));
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }

        SubtitlesStream pick = pickEnglish(tracks);
        if (pick == null) return Collections.emptyList();
        Log.d(TAG, "tier0 newpipe picked lang=" + pick.getLanguageTag()
                + " auto=" + pick.isAutoGenerated()
                + " fmt=" + (pick.getFormat() == null ? "null" : pick.getFormat().getName()));

        String body = simpleGet(pick.getContent());
        if (body == null || body.isEmpty()) return Collections.emptyList();

        // Try VTT first, fall back to the timedtext parser if the upstream
        // actually served XML (some tracks come back as TRANSCRIPT3 / srv3
        // even when we asked for VTT).
        List<SubtitleLine> parsed = VttParser.parse(body);
        if (parsed.isEmpty()) parsed = TimedTextParser.parse(body);
        if (parsed.isEmpty()) {
            int len = body.length();
            String sample = body.substring(0, Math.min(200, len))
                    .replaceAll("\\s+", " ");
            Log.w(TAG, "tier0 newpipe unparsed body=" + len + "B sample=\"" + sample + "\"");
        }
        return parsed;
    }

    /** Did NewPipe see any caption track at all? Used to disambiguate
     *  {@code Unavailable} vs {@code FetchFailed} when the legacy tiers
     *  also produce nothing. */
    private boolean newPipeReportedTrack(String videoId) {
        try {
            StreamExtractor e = ServiceList.YouTube
                    .getStreamExtractor(watchUrl(videoId));
            e.fetchPage();
            List<SubtitlesStream> all = e.getSubtitlesDefault();
            return all != null && !all.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private static SubtitlesStream pickEnglish(@NonNull List<SubtitlesStream> tracks) {
        SubtitlesStream manualEn = null;
        SubtitlesStream autoEn = null;
        SubtitlesStream anyEn = null;
        for (SubtitlesStream s : tracks) {
            String tag = s.getLanguageTag();
            if (tag == null) continue;
            // languageTag can be "en", "en-US", "en-GB", …
            if (!tag.toLowerCase().startsWith("en")) continue;
            anyEn = s;
            if (s.isAutoGenerated()) {
                if (autoEn == null) autoEn = s;
            } else {
                if (manualEn == null) manualEn = s;
            }
        }
        if (manualEn != null) return manualEn;
        if (autoEn != null) return autoEn;
        return anyEn;
    }

    // -- Legacy tiers ---------------------------------------------------------

    @NonNull
    private List<SubtitleLine> tryFetchLegacy(@Nullable String url) {
        if (url == null || url.isEmpty()) return Collections.emptyList();
        String body;
        try {
            body = simpleGet(url);
        } catch (IllegalArgumentException badUrl) {
            // OkHttp throws this for malformed URLs — swallow so the next
            // tier of the resolver can still run.
            Log.w(TAG, "legacy bad URL: " + url, badUrl);
            return Collections.emptyList();
        }
        if (body == null) return Collections.emptyList();
        List<SubtitleLine> lines = TimedTextParser.parse(body);
        if (lines.isEmpty()) {
            int len = body.length();
            String sample = body.substring(0, Math.min(200, len))
                    .replaceAll("\\s+", " ");
            Log.w(TAG, "legacy unparsed body=" + len + "B sample=\"" + sample + "\"");
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
