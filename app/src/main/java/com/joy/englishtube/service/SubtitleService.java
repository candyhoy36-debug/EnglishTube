package com.joy.englishtube.service;

import com.joy.englishtube.model.SubtitleLine;

import java.util.List;

/**
 * Sprint 2 will implement this. Strategy:
 *  1) GET https://www.youtube.com/api/timedtext?lang=en&v=&lt;id&gt;
 *  2) Fall back to lang=a.en (auto-generated)
 *  3) Fall back to parsing playerCaptionsTracklistRenderer from the HTML page
 *  4) If all fail but the video reports a track, surface FetchFailed so the UI
 *     can prompt the user to upload an SRT/VTT file (per SRS section 1.5).
 */
public interface SubtitleService {

    List<SubtitleLine> fetch(String videoId) throws SubtitleUnavailableException, FetchFailedException;

    /** No subtitle track exists for this video at all. */
    class SubtitleUnavailableException extends Exception {
        public SubtitleUnavailableException(String msg) { super(msg); }
    }

    /** A track exists but cannot be downloaded (signature/pot/timeout). */
    class FetchFailedException extends Exception {
        public FetchFailedException(String msg, Throwable cause) { super(msg, cause); }
    }
}
