package com.joy.englishtube.service.impl;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.joy.englishtube.service.TranslationService;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Translate via Google's free public single-translate endpoint
 * (used by translate.google.com / Chrome). No API key required.
 *
 * The endpoint has soft per-request body limits, so this service chunks the
 * input list into separately-translated batches whose serialized payload
 * stays under {@link #MAX_CHUNK_CHARS}. The chunk separator is a sentinel
 * token unlikely to appear in subtitles ({@link #SENTINEL}); after the round
 * trip we split the response on the same sentinel and re-assemble in order.
 */
public class GoogleTranslateService implements TranslationService {

    /** Joining cues with a unique sentinel keeps them in one HTTP call. */
    @VisibleForTesting
    static final String SENTINEL = "\n@@@\n";

    /**
     * Soft cap on the joined payload per request. Google rejects larger
     * bodies with HTTP 414 / unhelpful HTML. 4500 chars stays comfortably
     * inside the working envelope while still cutting round-trip count.
     */
    @VisibleForTesting
    static final int MAX_CHUNK_CHARS = 4500;

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private final OkHttpClient http;

    public GoogleTranslateService(@NonNull OkHttpClient http) {
        this.http = http;
    }

    @Override
    public List<String> translateEnToVi(List<String> sources) throws TranslationException {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(sources.size());
        // Build chunks so each HTTP body stays under MAX_CHUNK_CHARS.
        List<List<String>> chunks = chunk(sources, MAX_CHUNK_CHARS);
        for (List<String> chunk : chunks) {
            String[] translated = translateChunk(chunk);
            Collections.addAll(out, translated);
        }
        // Defensive — chunk() preserves order and translateChunk() returns
        // exactly chunk.size() entries, so totals must match.
        if (out.size() != sources.size()) {
            throw new TranslationException(
                    "translated " + out.size() + " of " + sources.size() + " lines",
                    null);
        }
        return out;
    }

    @VisibleForTesting
    static List<List<String>> chunk(List<String> sources, int maxChars) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLen = 0;
        for (String s : sources) {
            int add = (s == null ? 0 : s.length()) + SENTINEL.length();
            if (!current.isEmpty() && currentLen + add > maxChars) {
                chunks.add(current);
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(s == null ? "" : s);
            currentLen += add;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    private String[] translateChunk(List<String> chunk) throws TranslationException {
        String joined = joinWithSentinel(chunk);
        String url = buildUrl(joined);

        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .build();

        String body;
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new TranslationException(
                        "google translate http " + resp.code(), null);
            }
            ResponseBody rb = resp.body();
            body = rb == null ? "" : rb.string();
        } catch (IOException e) {
            throw new TranslationException("google translate io error", e);
        }

        String translated = parseTranslated(body);
        return splitOnSentinel(translated, chunk.size());
    }

    @VisibleForTesting
    static String joinWithSentinel(List<String> chunk) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunk.size(); i++) {
            if (i > 0) sb.append(SENTINEL);
            sb.append(chunk.get(i));
        }
        return sb.toString();
    }

    /**
     * Build the full GET URL. Using HttpUrl ensures correct query encoding —
     * we need `q=` to be percent-encoded UTF-8.
     */
    private static String buildUrl(String joined) {
        // Some characters (notably '+') get mis-decoded by the endpoint when
        // OkHttp's HttpUrl url-encodes them differently than expected. Encode
        // the q parameter manually for parity with translate.google.com.
        String encoded = URLEncoder.encode(joined, StandardCharsets.UTF_8);
        HttpUrl.Builder b = HttpUrl.parse("https://translate.googleapis.com/translate_a/single")
                .newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "en")
                .addQueryParameter("tl", "vi")
                .addQueryParameter("dt", "t")
                .addQueryParameter("ie", "UTF-8")
                .addQueryParameter("oe", "UTF-8");
        // Replace HttpUrl's automatic encoding with the manually-encoded q.
        return b.build() + "&q=" + encoded;
    }

    /**
     * The response is a deeply-nested JSON array. The translated text we
     * care about lives at {@code root[0][i][0]} for each segment chunk. We
     * concatenate all segments to recover the joined sentinel string.
     */
    @VisibleForTesting
    static String parseTranslated(String body) throws TranslationException {
        try {
            JSONArray root = new JSONArray(body);
            JSONArray segs = root.optJSONArray(0);
            if (segs == null) {
                throw new TranslationException("malformed response: " + body, null);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segs.length(); i++) {
                JSONArray seg = segs.optJSONArray(i);
                if (seg == null) continue;
                String piece = seg.optString(0, "");
                sb.append(piece);
            }
            return sb.toString();
        } catch (JSONException e) {
            throw new TranslationException("malformed response", e);
        }
    }

    /**
     * Split the translated string back into the original number of cues. If
     * the model dropped a sentinel for some reason we pad with empty strings
     * so callers always receive {@code expected} entries (and don't index
     * out of bounds).
     */
    @VisibleForTesting
    static String[] splitOnSentinel(String translated, int expected) {
        // Trim sentinel — the model occasionally rewrites the @ sequence.
        // First try the exact sentinel; if it's missing, fall back to
        // splitting on the @@@ core which the model preserves more
        // consistently than the surrounding newlines.
        String[] parts;
        if (translated.contains(SENTINEL)) {
            parts = translated.split(java.util.regex.Pattern.quote(SENTINEL), -1);
        } else {
            parts = translated.split("\\s*@@@\\s*", -1);
        }
        if (parts.length == expected) return parts;
        String[] out = new String[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = i < parts.length ? parts[i] : "";
        }
        return out;
    }
}
