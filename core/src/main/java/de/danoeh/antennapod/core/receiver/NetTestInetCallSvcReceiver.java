package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import de.danoeh.antennapod.core.util.LogToFile;

public class NetTestInetCallSvcReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestInetCallSvcRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {
        LogToFile.d(context, TAG, "Test check internet from a service");
        doCaseNetworkUnavailable(context);
    }

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        LogToFile.d(context, TAG, "  About to start service which calls hasActiveInternetConnection()...");
        Intent intent = new Intent(context.getApplicationContext(), NetTestInternetCallService.class);
        context.startService(intent);
        new Handler().postDelayed(() -> logNetworkAvailable(context, "Delay 08s"), 8000);
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
