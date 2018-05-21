package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.os.Handler;

public class NetTestSimpleRetryReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestSimpleRetryRcvr";

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        new Handler().postDelayed(()-> logNetworkAvailable(context, "Delay 05s"), 5000);
        new Handler().postDelayed(()-> logNetworkAvailable(context, "Delay 10s"), 10000);
    }

    @Override
    protected String tag() {
        return TAG;
    }


}
