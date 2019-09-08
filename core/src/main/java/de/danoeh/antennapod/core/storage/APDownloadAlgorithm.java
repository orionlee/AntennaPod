package de.danoeh.antennapod.core.storage;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.danoeh.antennapod.core.feed.FeedFilter;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.NetworkUtils;
import de.danoeh.antennapod.core.util.PowerUtils;

/**
 * Implements the automatic download algorithm used by AntennaPod. This class assumes that
 * the client uses the APEpisodeCleanupAlgorithm.
 */
public class APDownloadAlgorithm implements AutomaticDownloadAlgorithm {
    private static final String TAG = "APDownloadAlgorithm";

    /**
     * Looks for undownloaded episodes in the queue or list of new items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Runnable that will be submitted to an ExecutorService.
     */
    @Override
    public Runnable autoDownloadUndownloadedItems(final Context context) {
        return () -> {

            // true if we should auto download based on network status
            boolean networkShouldAutoDl = NetworkUtils.autodownloadNetworkAvailable()
                    && UserPreferences.isEnableAutodownload();

            // true if we should auto download based on power status
            boolean powerShouldAutoDl = PowerUtils.deviceCharging(context)
                    || UserPreferences.isEnableAutodownloadOnBattery();

            // we should only auto download if both network AND power are happy
            if (networkShouldAutoDl && powerShouldAutoDl) {

                Log.d(TAG, "Performing auto-dl of undownloaded episodes");

                List<FeedItem> candidates;
                final List<FeedItem> queue = DBReader.getQueue();
                final List<FeedItem> newItems = DBReader.getNewItemsList();
                candidates = new ArrayList<>(queue.size() + newItems.size());
                candidates.addAll(queue);
                for(FeedItem newItem : newItems) {
                    FeedPreferences feedPrefs = newItem.getFeed().getPreferences();
                    FeedFilter feedFilter = feedPrefs.getFilter();
                    if(!candidates.contains(newItem) && feedFilter.shouldAutoDownload(newItem)) {
                        candidates.add(newItem);
                    }
                }

                // filter items that are not auto downloadable
                Iterator<FeedItem> it = candidates.iterator();
                while(it.hasNext()) {
                    FeedItem item = it.next();
                    if(!item.isAutoDownloadable()) {
                        it.remove();
                    }
                }

                int autoDownloadableEpisodes = candidates.size();
                int downloadedEpisodes = DBReader.getNumberOfDownloadedEpisodes();
                int deletedEpisodes = UserPreferences.getEpisodeCleanupAlgorithm()
                        .makeRoomForEpisodes(context, autoDownloadableEpisodes);
                boolean cacheIsUnlimited = UserPreferences.getEpisodeCacheSize() == UserPreferences
                        .getEpisodeCacheSizeUnlimited();
                int episodeCacheSize = UserPreferences.getEpisodeCacheSize();

                int episodeSpaceLeft;
                if (cacheIsUnlimited ||
                        episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) {
                    episodeSpaceLeft = autoDownloadableEpisodes;
                } else {
                    episodeSpaceLeft = episodeCacheSize - (downloadedEpisodes - deletedEpisodes);
                }

                List<? extends FeedItem> itemsToDownload =
                        cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, episodeSpaceLeft);

                Log.d(TAG, "Enqueueing " + itemsToDownload.size()+ " items for download");

                try {
                    DBTasks.downloadFeedItems(false, context,
                            itemsToDownload.toArray(new FeedItem[itemsToDownload.size()]));
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                }

            }
        };
    }

    private static List<? extends FeedItem> cutCandidatesPerSpaceLeftAndPerFeedLimit(List<? extends FeedItem> candidates,
                                                                                     List<? extends FeedItem> queue,
                                                                                     int episodeSpaceLeft) {
        final int MAX_NUM_EPISODES_PER_FEED_IN_QUEUE = 2; // LATER - make it configurable
        return cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, episodeSpaceLeft,
                MAX_NUM_EPISODES_PER_FEED_IN_QUEUE);
    }

    /**
     * Cut candidates per space left, but each podcast will have a max of
     * <code>maxNumEpisodesPerFeed</code> in the resulting queue.
     *
     * Use case: avoid podcasts that frequently release new episodes dominating the queue.
     */
    @VisibleForTesting
    static List<? extends FeedItem> cutCandidatesPerSpaceLeftAndPerFeedLimit(List<? extends FeedItem> candidates,
                                                                             List<? extends FeedItem> queue,
                                                                             int episodeSpaceLeft,
                                                                             int maxNumEpisodesPerFeed) {
        if (maxNumEpisodesPerFeed < 1) { // case unlimited
            if (candidates.size() > episodeSpaceLeft) {
                return candidates.subList(0, episodeSpaceLeft);
            } else {
                return candidates;
            }
        }

        // case limit number of episodes per feed in queue

        ArrayMap feedNumEpisodesMap = new ArrayMap<Long, Integer>();
        for (FeedItem item : queue) {
            increment(feedNumEpisodesMap, item.getFeedId());
        }

        List<FeedItem> result = new ArrayList<FeedItem>(episodeSpaceLeft);

        Iterator<? extends FeedItem> it = candidates.iterator();
        while(it.hasNext() && result.size() < episodeSpaceLeft) {
            FeedItem item = it.next();
            long feedId = item.getFeedId();
            if (getOrZero(feedNumEpisodesMap, feedId) < maxNumEpisodesPerFeed) {
                result.add(item);
                increment(feedNumEpisodesMap, feedId);
            } else {
                Log.v(TAG, "item skipped because the feed has reached the per-feed limit. item: " +
                        item.getTitle() + " , of feed: " + item.getFeed().getTitle());
            }
        }

        return result;
    }

    private static void increment(ArrayMap<Long, Integer> map, long id) {
        Integer curValIfAny = map.get(id);
        int newVal = curValIfAny != null ? curValIfAny + 1 : 1;
        map.put(id, newVal);
    }

    private static int getOrZero(ArrayMap<Long, Integer> map, long id) {
        Integer curValIfAny = map.get(id);
        return curValIfAny != null ? curValIfAny : 0;
    }
}
