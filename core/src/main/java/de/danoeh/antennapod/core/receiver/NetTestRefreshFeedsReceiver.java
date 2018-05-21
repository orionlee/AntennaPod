package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.os.Handler;

import de.danoeh.antennapod.core.storage.DBTasks;

public class NetTestRefreshFeedsReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestRefreshFeedsRcvr";

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        DBTasks.refreshAllFeeds(context, null);
        logNetworkAvailable(context, "After refreshAllFeeds");
        new Handler().postDelayed(() -> logNetworkAvailable(context, "Delay 02s"), 2000);
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
