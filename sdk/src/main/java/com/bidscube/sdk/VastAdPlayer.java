package com.bidscube.sdk;

/**
 * IMA / VAST surface that can be released (see {@link com.bidscube.sdk.view.IMAPlayerHandler} when
 * the IMA module is on the classpath).
 */
public interface VastAdPlayer {

    void release();
}
