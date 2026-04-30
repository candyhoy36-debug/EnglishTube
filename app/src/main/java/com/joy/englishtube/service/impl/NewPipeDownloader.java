package com.joy.englishtube.service.impl;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * Bridges {@link Downloader} (the abstraction NewPipeExtractor expects) onto
 * the application's existing {@link OkHttpClient}.
 *
 * <p>Mostly mirrors NewPipe's reference DownloaderImpl, minus the cookie /
 * captcha-activity / restricted-mode plumbing we don't need:</p>
 *
 * <ul>
 *   <li>Adds a desktop Firefox-style {@code User-Agent}; YouTube serves
 *       different responses to mobile UAs and we want the same shape NewPipe
 *       uses.</li>
 *   <li>Maps HTTP&nbsp;429 onto {@link ReCaptchaException} so the extractor
 *       surfaces it the way the rest of the library expects.</li>
 *   <li>Forwards every request header from {@link Request} onto OkHttp's
 *       builder, including multi-value headers.</li>
 * </ul>
 */
public final class NewPipeDownloader extends Downloader {

    /**
     * Same UA NewPipe ships in its reference downloader. YouTube serves the
     * full {@code playerCaptionsTracklistRenderer} block to this UA, whereas
     * generic mobile UAs sometimes get a stripped response.
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) "
                    + "Gecko/20100101 Firefox/140.0";

    private final OkHttpClient client;

    public NewPipeDownloader(@NonNull OkHttpClient client) {
        this.client = client;
    }

    @NonNull
    @Override
    public Response execute(@NonNull Request request)
            throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        for (final Map.Entry<String, List<String>> pair : headers.entrySet()) {
            final String name = pair.getKey();
            // Drop our default UA so caller can override.
            if ("User-Agent".equalsIgnoreCase(name)) {
                requestBuilder.removeHeader("User-Agent");
            }
            for (final String value : pair.getValue()) {
                requestBuilder.addHeader(name, value);
            }
        }

        try (okhttp3.Response response =
                     client.newCall(requestBuilder.build()).execute()) {
            if (response.code() == 429) {
                throw new ReCaptchaException("reCaptcha challenge requested", url);
            }

            final ResponseBody body = response.body();
            final String bodyString = body == null ? null : body.string();
            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    bodyString,
                    response.request().url().toString());
        }
    }
}
