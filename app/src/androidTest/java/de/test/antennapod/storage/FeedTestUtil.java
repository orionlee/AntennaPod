package de.test.antennapod.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.feed.FeedPreferences;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import de.danoeh.antennapod.core.util.Consumer;

class FeedTestUtil {
    private FeedTestUtil() { }

    @NonNull
    static Feed createFeed(int titleId,
                           @Nullable Consumer<FeedPreferences> feedPreferencesCustomizer,
                           FeedItem... feedItems) {
        Feed f = new Feed(0, null, "feed " + titleId, null, "link" + titleId, "descr", null, null,
                null, null, "id" + titleId, null, null, "url" + titleId, false, false, null, null, false);

        FeedPreferences fPrefs =
                new FeedPreferences(0, false, FeedPreferences.AutoDeleteAction.GLOBAL, null, null);
        fPrefs.setKeepUpdated(true);

        if (feedPreferencesCustomizer != null) {
            feedPreferencesCustomizer.accept(fPrefs);
        }
        f.setPreferences(fPrefs);

        boolean isAutoDownload = fPrefs.getAutoDownload();

        long curPubDateMillis = System.currentTimeMillis() - feedItems.length * 1000;

        for (int j = 0; j < feedItems.length; j++) {
            FeedItem fi = feedItems[j];
            fi.setFeed(f);
            curPubDateMillis += 1000; // pubDate is set to be in ascending order
            fi.setPubDate(new Date(curPubDateMillis));
            fi.setAutoDownload(isAutoDownload);
            fi.setTitle("item " + j);

            f.getItems().add(fi);
        }

        return f;
    }

    @NonNull
    static FeedItem createFeedItemWithMedia() {
        FeedItem item = new FeedItem();

        FeedMedia media = new FeedMedia(item, "url", 1, "audio/mp3");
        item.setMedia(media);

        return item;
    }

    @NonNull
    static List<Long> toIds(List<? extends FeedItem> feedItems) {
        List<Long> result = new ArrayList<>(feedItems.size());
        for (FeedItem fi : feedItems) {
            result.add(fi.getId());
        }
        return result;
    }

    @NonNull
    static List<Long> toIds(FeedItem... feedItems) {
        List<Long> result = new ArrayList<>(feedItems.length);
        for (FeedItem fi : feedItems) {
            result.add(fi.getId());
        }
        return result;
    }

    @NonNull
    static FeedsAccessor saveFeeds(Feed... feeds) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        try {
            adapter.open();
            adapter.setCompleteFeed(feeds);
        } finally {
            adapter.close();
        }
        return new FeedsAccessor(Arrays.asList(feeds));
    }

    /**
     * Wrapper to provide convenience methods to access feeds / feed items of a collection
     * (usually the test data)
     */
    static class FeedsAccessor {
        @NonNull
        private final List<Feed> feeds;

        public FeedsAccessor(@NonNull List<Feed> feeds) {
            this.feeds = feeds;
        }

        public @NonNull Feed f(int feedIndex) {
            return feeds.get(feedIndex);
        }

        public @NonNull FeedItem fi(int feedIndex, int itemIndex) {
            return feeds.get(feedIndex).getItemAtIndex(itemIndex);
        }

        public long fiId(int feedIndex, int itemIndex) {
            return fi(feedIndex, itemIndex).getId();
        }
    }
}
