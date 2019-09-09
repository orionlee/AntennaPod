package de.danoeh.antennapod.core.storage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedItemMother;
import de.danoeh.antennapod.core.feed.FeedMother;
import de.danoeh.antennapod.core.feed.FeedPreferences;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class APDownloadAlgorithmRandomDownloadTest {

    private final Feed f1 = feedWithIdAndAutoDl(1, true);
    private final Feed f2 = feedWithIdAndAutoDl(2, true);
    private final Feed f3 = feedWithIdAndAutoDl(3, false);
    private final Feed f4 = feedWithIdAndAutoDl(4, false);

    private final FeedItem i1_11 = itemWithId(f1, 11);
    private final FeedItem i1_12 = itemWithId(f1, 12);
    private final FeedItem i2_21 = itemWithId(f2, 21);
    private final FeedItem i3_31 = itemWithId(f3, 31);
    private final List<FeedItem> i3_all;
    private final FeedItem i4_41 = itemWithId(f4, 41);
    private final FeedItem i4_42 = itemWithId(f4, 42);

    // queue's items are all auto-downloaded
    private final List<FeedItem> queueNoNonAutoDl = Arrays.asList(i1_11, i2_21, i1_12);

    // queue has some items that are not auto-downloaded
    private final List<FeedItem> queueWithNonAutoDl = Arrays.asList(i1_11, i3_31, i2_21, i4_42);

    private final List<FeedItem> nonAutoDlItems;

    public APDownloadAlgorithmRandomDownloadTest() {
        final int numFeed3Items = 30;
        i3_all = new ArrayList<>(numFeed3Items );
        i3_all.add(i3_31);
        for(int i = 2; i <= numFeed3Items; i++) {
            i3_all.add(itemWithId(f3, 30 + i));
        }

        nonAutoDlItems = new ArrayList<>(numFeed3Items);
        nonAutoDlItems.add(i4_41);
        // add all feed3 items EXCEPT i3_31 (it might be in the queue in same test cases)
        for(int i = 2; i <= numFeed3Items; i++) {
            nonAutoDlItems.add(i3_all.get(i - 1));
        }
    }


    @Test
    public void getSomeRandomNonAutoDownloadEpisodes_QueueWithNonAutoDl() {
        List<? extends FeedItem> actual =
                APDownloadAlgorithm.getSomeRandomNonAutoDownloadEpisodes(nonAutoDlItems, queueWithNonAutoDl);
        assertEquals("Queue has some non auto download items - No more is needed.",
                EMPTY_LIST, actual);
    }

    @Test
    public void getSomeRandomNonAutoDownloadEpisodes_QueueNoNonAutoDl() {
        boolean hasResultsFromNonHead = false;

        for(int i = 0; i < 10; i++) { // run multiple time for randomness test below
            List<? extends FeedItem> actual =
                    APDownloadAlgorithm.getSomeRandomNonAutoDownloadEpisodes(nonAutoDlItems, queueNoNonAutoDl);
            assertEquals("Queue has only auto download items - Get 1 random.",
                    1, actual.size());
            assertTrue("Queue has only auto download items - The result comes from non auto download item list.",
                    nonAutoDlItems.contains(actual.get(0)));
            if (actual.get(0) != nonAutoDlItems.get(0)) {
                hasResultsFromNonHead = true;
            }
        }
        assertTrue("Queue has only auto download items - ensure result is random, just the first one",
                hasResultsFromNonHead);

    }

    @Test
    public void getSomeRandomNonAutoDownloadEpisodes_EmptyNonAutoDlItems() {
        List<? extends FeedItem> actual =
                APDownloadAlgorithm.getSomeRandomNonAutoDownloadEpisodes(EMPTY_LIST, queueNoNonAutoDl);
        assertEquals("No non auto download items available - result should be empty.",
                EMPTY_LIST, actual);
    }


    private static Feed feedWithIdAndAutoDl(long feedId, boolean isAutoDownload) {
        Feed feed = FeedMother.anyFeed();
        feed.setId(feedId);
        feed.setTitle(feed.getTitle() + feedId);
        feed.setPreferences(new FeedPreferences(feedId, isAutoDownload,
                FeedPreferences.AutoDeleteAction.GLOBAL, "", ""));
        return feed;
    }

    private static FeedItem itemWithId(Feed feed, long itemId) {
        FeedItem item = FeedItemMother.anyFeedItemWithImage();
        item.setId(itemId);
        item.setTitle(item.getTitle() + itemId);
        item.setFeed(feed);
        item.setFeedId(feed.getId());
        return item;
    }

}
