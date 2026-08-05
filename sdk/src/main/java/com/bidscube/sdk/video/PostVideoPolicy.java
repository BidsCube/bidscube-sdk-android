package com.bidscube.sdk.video;

import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.models.video.VastPreview;

/**
 * Resolves what happens after linear video ends (complete or skip), based on {@code autoClose}
 * and available Companion / post-video content.
 */
public final class PostVideoPolicy {

    public enum Action {
        /** Close fullscreen immediately; release player; no end card / last frame. */
        AUTO_CLOSE,
        /** Replace player with Companion end card. */
        SHOW_END_CARD,
        /** Keep player visible on last frame; show manual close. */
        KEEP_LAST_FRAME,
        /** Do not interrupt — mini-game / playable still running. */
        CONTINUE_POST_VIDEO
    }

    private PostVideoPolicy() {
    }

    /**
     * @param autoClose           SDK {@code autoClose} flag
     * @param hasRenderableCompanion Companion end card available
     * @param postVideoPhaseActive   true when a mini-game / playable phase is active or should start
     */
    public static Action resolve(
            boolean autoClose,
            boolean hasRenderableCompanion,
            boolean postVideoPhaseActive) {
        if (postVideoPhaseActive) {
            return Action.CONTINUE_POST_VIDEO;
        }
        if (autoClose) {
            return Action.AUTO_CLOSE;
        }
        if (hasRenderableCompanion) {
            return Action.SHOW_END_CARD;
        }
        return Action.KEEP_LAST_FRAME;
    }

    public static boolean hasRenderableCompanion(VastPreview preview) {
        return preview != null && preview.hasRenderableCompanion();
    }

    public static boolean hasRenderableCompanion(VastCompanion companion) {
        return companion != null && companion.isRenderable();
    }
}
