package com.bidscube.sdk.openrtb;

/**
 * One VAST markup belonging to an OpenRTB podded response.
 */
public final class PoddedAdMarkup {

    private final String impId;
    private final Integer slotInPod;
    private final String vastXmlOrTagUrl;
    private final OpenRtbVideoSlotContext slotContext;
    private final int responseOrder;

    public PoddedAdMarkup(
            String impId,
            Integer slotInPod,
            String vastXmlOrTagUrl,
            OpenRtbVideoSlotContext slotContext,
            int responseOrder) {
        this.impId = impId;
        this.slotInPod = slotInPod;
        this.vastXmlOrTagUrl = vastXmlOrTagUrl;
        this.slotContext = slotContext;
        this.responseOrder = responseOrder;
    }

    public String getImpId() {
        return impId;
    }

    public Integer getSlotInPod() {
        return slotInPod;
    }

    public String getVastXmlOrTagUrl() {
        return vastXmlOrTagUrl;
    }

    public OpenRtbVideoSlotContext getSlotContext() {
        return slotContext;
    }

    public int getResponseOrder() {
        return responseOrder;
    }

    public boolean isInlineVast() {
        return vastXmlOrTagUrl != null && vastXmlOrTagUrl.trim().startsWith("<");
    }
}
