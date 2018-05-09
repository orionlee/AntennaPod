package de.danoeh.antennapod.core.storage;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.util.LogToFile;

/**
 * Implementation of the EpisodeCleanupAlgorithm interface used by AntennaPod.
 */
public class APCleanupAlgorithm extends EpisodeCleanupAlgorithm {

    private static final String TAG = "APCleanupAlgorithm";
    /** the number of days after playback to wait before an item is eligible to be cleaned up */
    private final int numberOfDaysAfterPlayback;

    public APCleanupAlgorithm(int numberOfDaysAfterPlayback) {
        this.numberOfDaysAfterPlayback = numberOfDaysAfterPlayback;
    }

    /**
     * @return the number of episodes that *could* be cleaned up, if needed
     */
    public int getReclaimableItems()
    {
        return getCandidates(null).size();
    }

    @Override
    public int performCleanup(Context context, int numberOfEpisodesToDelete) {
        List<FeedItem> candidates = getCandidates(context); // Temporary hack for debug trace
        List<FeedItem> delete;

        Collections.sort(candidates, (lhs, rhs) -> {
            Date l = lhs.getMedia().getPlaybackCompletionDate();
            Date r = rhs.getMedia().getPlaybackCompletionDate();

            if (l == null) {
                l = new Date();
            }
            if (r == null) {
                r = new Date();
            }
            return l.compareTo(r);
        });

        if (candidates.size() > numberOfEpisodesToDelete) {
            delete = candidates.subList(0, numberOfEpisodesToDelete);
        } else {
            delete = candidates;
        }

        for (FeedItem item : delete) {
            try {
                DBWriter.deleteFeedMediaOfItem(context, item.getMedia().getId()).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        int counter = delete.size();


        LogToFile.d(context, TAG, String.format(
                "Auto-delete deleted %d episodes (%d requested)", counter,
                numberOfEpisodesToDelete));

        return counter;
    }

    @NonNull
    private List<FeedItem> getCandidates(Context context) { // Context for debug. A temporary hack
        List<FeedItem> candidates = new ArrayList<>();
        List<FeedItem> downloadedItems = DBReader.getDownloadedItems();
        Calendar cal = Calendar.getInstance();
        if (7 == numberOfDaysAfterPlayback ) {
            cal.add(Calendar.HOUR_OF_DAY, -1 * 12); // Experiment: episode cleanup with 12 hours, overriding 7 day cleanup option as a temporary hack .
        } else {
            cal.add(Calendar.DAY_OF_MONTH, -1 * numberOfDaysAfterPlayback);
        }
        Date mostRecentDateForDeletion = cal.getTime();
        for (FeedItem item : downloadedItems) {
            if (item.hasMedia()
                    && item.getMedia().isDownloaded()
                    && !item.isTagged(FeedItem.TAG_QUEUE)
                    && item.isPlayed()
                    && !item.isTagged(FeedItem.TAG_FAVORITE)) {
                FeedMedia media = item.getMedia();
                // make sure this candidate was played at least the proper amount of days prior
                // to now
                ///LogToFile.d(context, TAG, "Potential item to delete: [" + item.getTitle() + ", " + media.getPlaybackCompletionDate() + "]");
                
                if (media != null
                        && media.getPlaybackCompletionDate() != null
                        && media.getPlaybackCompletionDate().before(mostRecentDateForDeletion)) {
                    candidates.add(item);
                }
            }
        }
        { // log candidate list for detail debug tracing
            StringBuilder sb = new StringBuilder();
            sb.append("Cleanup episodes - mostRecentDateForDeletion: ");
            sb.append(mostRecentDateForDeletion);
            sb.append(" candidates: [ ");
            for (FeedItem item : candidates) {
                sb.append(String.format("\n  %s (%s) on %s",
                        item.getTitle(), item.getFeed().getTitle(), item.getMedia().getPlaybackCompletionDate()));
            }
            sb.append("\n]");
            if (context != null) {
                LogToFile.d(context, TAG, sb.toString());
            } else {
                Log.d(TAG, sb.toString());
            }
        }
        return candidates;
    }

    @Override
    public int getDefaultCleanupParameter() {
        return getNumEpisodesToCleanup(0);
    }

    public int getNumberOfDaysAfterPlayback() { return numberOfDaysAfterPlayback; }
}
