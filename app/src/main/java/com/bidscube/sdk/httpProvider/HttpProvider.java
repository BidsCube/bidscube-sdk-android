package com.bidscube.sdk.httpProvider;

import android.util.Log;

import com.bidscube.sdk.models.Callback;

import java.net.HttpURLConnection;
import java.net.URL;

public class HttpProvider {
    public static void sendRequest(String urlString, String method, Callback callback) {
        Log.v("HttpProvider", "Sending " + method + " request to: " + urlString);

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                int responseCode = connection.getResponseCode();
                String responseBody = "";
                if (responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                    java.io.InputStream is = connection.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                    responseBody = s.hasNext() ? s.next() : "";
                    is.close();
                }
                connection.disconnect();
                if (callback != null) {
                    callback.onSuccess(responseCode, responseBody);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFail(e);
                }
            }
        }).start();
    }
}
