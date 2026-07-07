package com.bidscube.sdk.models.video;

/**
 * One VAST {@code MediaFile} candidate inside a Linear creative.
 */
public final class VastMediaFile {

    private final String url;
    private final String mimeType;
    private final Integer width;
    private final Integer height;
    private final Integer bitrate;
    private final String delivery;

    public VastMediaFile(
            String url,
            String mimeType,
            Integer width,
            Integer height,
            Integer bitrate,
            String delivery) {
        this.url = url;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.delivery = delivery;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Integer getBitrate() {
        return bitrate;
    }

    public String getDelivery() {
        return delivery;
    }
}
