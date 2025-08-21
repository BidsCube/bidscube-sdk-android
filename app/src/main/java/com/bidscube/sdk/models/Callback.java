package com.bidscube.sdk.models;

public interface Callback {
    void onSuccess(int responseCode, String responseBody);
    void onFail(Exception e);
}
