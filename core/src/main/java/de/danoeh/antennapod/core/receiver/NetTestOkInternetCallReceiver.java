package de.danoeh.antennapod.core.receiver;

import android.content.Context;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.util.LogToFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetTestOkInternetCallReceiver extends NetTestInternetCallReceiver {

    private static final String TAG = "NetTestOkInetCallRcvr";

    @Override
    protected boolean hasActiveInternetConnection(Context context) {
        // simulate {@link de.danoeh.antennapod.core.service.download.HttpDownloader}
        try {
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(4000, TimeUnit.MILLISECONDS); /// AntennapodHttpClient.newBuilder();
            OkHttpClient httpClient = httpClientBuilder.build();
            Request.Builder httpReq = new Request.Builder().url(new URL("http://www.google.com/"))
                    .header("User-Agent", "Test")
                    .header("Connection", "close")
                    .get();
            try (Response response = httpClient.newCall(httpReq.build()).execute()) {
                response.code(); // exercise the response just in case.
                /// LogToFile.d(context, tag(), "    HTTP response code: " + response.code());
                return true;
            }
        } catch (IOException ioe) {
            LogToFile.d(context, tag(), "hasActiveInternetConnection() fails: " + ioe.getMessage());
            return false;
        }
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
