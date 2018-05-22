package de.danoeh.antennapod.core.receiver;

import android.content.Context;
import android.os.Handler;

import java.util.List;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.util.LogToFile;

public class NetTestRefresh1FeedReceiver extends NetTestAbstractReceiver {

    private static final String TAG = "NetTestRefresh1FeedRcvr";

    @Override
    protected void doCaseNetworkUnavailable(Context context) {
        List<Feed> feedList = DBReader.getFeedList();
        if (feedList.size() < 1) {
            LogToFile.d(context, tag(), " Unexpected error: there is no feed available for refresh");
            return;
        }
        Feed aFeed = feedList.get(0);
        try {
            DBTasks.forceRefreshFeed(context, aFeed);
        } catch (Exception e) {
            LogToFile.d(context, tag(), " Unexpected error in refreshing feed <" + aFeed.getLink() + "> : " + e.getMessage());
        }
        logNetworkAvailable(context, "After refresh1Feed");
        new Handler().postDelayed(() -> logNetworkAvailable(context, "Delay 02s"), 2000);
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
