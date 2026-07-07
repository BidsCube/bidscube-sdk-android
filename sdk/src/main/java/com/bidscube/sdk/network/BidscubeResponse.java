package com.bidscube.sdk.network;

import com.bidscube.sdk.openrtb.PoddedAdResponse;

/**
 * Response model for Bidscube ad requests.
 * Contains adm content, position, optional raw JSON, and normalized OpenRTB pod metadata.
 */
public class BidscubeResponse {
    private final String adm;
    private final int position;
    private final String rawJson;
    private final PoddedAdResponse poddedAdResponse;

    public BidscubeResponse(String adm, int position) {
        this(adm, position, null, null);
    }

    public BidscubeResponse(String adm, int position, String rawJson, PoddedAdResponse poddedAdResponse) {
        this.adm = adm;
        this.position = position;
        this.rawJson = rawJson;
        this.poddedAdResponse = poddedAdResponse;
    }

    public String getAdm() {
        return adm;
    }

    public int getPosition() {
        return position;
    }

    /** Original JSON body when the response was JSON-shaped. */
    public String getRawJson() {
        return rawJson;
    }

    /** Normalized OpenRTB podded response, when present. */
    public PoddedAdResponse getPoddedAdResponse() {
        return poddedAdResponse;
    }

    public boolean hasOpenRtbPodMetadata() {
        return poddedAdResponse != null && poddedAdResponse.isPodded();
    }

    @Override
    public String toString() {
        return "BidscubeResponse{position=" + position
                + ", admLength=" + (adm != null ? adm.length() : 0)
                + ", podded=" + hasOpenRtbPodMetadata() + "}";
    }
}
