package de.danoeh.antennapod.core.storage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedItemMother;
import de.danoeh.antennapod.core.feed.FeedMother;

import static de.danoeh.antennapod.core.storage.APDownloadAlgorithm.cutCandidatesPerSpaceLeftAndPerFeedLimit;
import static org.junit.Assert.assertEquals;

public class APDownloadAlgorithmTest {

    private final Feed f1 = feedWithId(1);
    private final Feed f2 = feedWithId(2);
    private final Feed f3 = feedWithId(3);
    private final Feed f4 = feedWithId(4);

    private final FeedItem i1_11 = itemWithId(f1, 11);
    private final FeedItem i1_12 = itemWithId(f1, 12);
    private final FeedItem i1_13 = itemWithId(f1, 13);
    private final FeedItem i1_14 = itemWithId(f1, 14);
    private final FeedItem i2_21 = itemWithId(f2, 21);
    private final FeedItem i2_22 = itemWithId(f2, 22);
    private final FeedItem i2_23 = itemWithId(f2, 22);
    private final FeedItem i3_31 = itemWithId(f3, 31);
    private final FeedItem i3_32 = itemWithId(f3, 32);
    private final FeedItem i3_33 = itemWithId(f3, 33);
    private final FeedItem i4_41 = itemWithId(f4, 41);
    private final FeedItem i4_42 = itemWithId(f4, 42);

    private final List<FeedItem> queue = Arrays.asList(i1_11, i2_21, i1_12);

    @Test
    public void cutCandidatesPerSpaceLeft_LimitReached() {
        final int spaceLeft = 4;
        final int maxNumEpisodesPerFeed = 2;

        List<FeedItem> candidates = Arrays.asList(i1_13, // don't add - limit reached *before* download
                i2_22, // will add (1)
                i2_23, // don't add - limit reached *during* download
                i3_31, // will add (2)
                i3_32, // will add (3)
                i3_33, // don't add - limit reached *during* download, and starting from 0
                i4_41, // will add (4)
                i4_42  // don't add (no more space)
        );

        List<? extends FeedItem> expected = Arrays.asList(i2_22, i3_31, i3_32, i4_41);
        List<? extends FeedItem> actual =
                cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, spaceLeft, maxNumEpisodesPerFeed);

        assertEquals("Resulting candidates: feeds 1, 2, 3 should reach per-feed limit",
                expected, actual);
    }
    
    @Test
    public void cutCandidatesPerSpaceLeft_UnlimitedNumEpisodesPerFeed() {
        final int spaceLeft = 2;
        final int maxNumEpisodesPerFeed = 0; // unlimited

        List<FeedItem> candidates = Arrays.asList(i1_13,
                i1_14,
                i2_22,
                i3_31);

        List<? extends FeedItem> expected = Arrays.asList(i1_13, i1_14);
        List<? extends FeedItem> actual =
                cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, spaceLeft, maxNumEpisodesPerFeed);

        assertEquals("Resulting candidates: feed1 will have 4 items, as there is no limit",
                expected, actual);
    }

    @Test
    public void cutCandidatesPerSpaceLeft_UnlimitedNumEpisodesPerFeed_NotEnoughCandidates() {
        final int spaceLeft = 2;
        final int maxNumEpisodesPerFeed = 0; // unlimited

        List<FeedItem> candidates = Arrays.asList(i1_13);

        List<? extends FeedItem> expected = Arrays.asList(i1_13);
        List<? extends FeedItem> actual =
                cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, spaceLeft, maxNumEpisodesPerFeed);

        assertEquals("Resulting candidates: all candidates all picked up (with no per-feed) limit, with not enough candidates",
                expected, actual);
    }

    @Test
    public void cutCandidatesPerSpaceLeft_Boundary_NumCandidatesLessThanSpaceLeft() {
        final int spaceLeft = 3;
        final int maxNumEpisodesPerFeed = 2;

        List<FeedItem> candidates = Arrays.asList(i3_31, i4_41);

        List<? extends FeedItem> expected = Arrays.asList(i3_31, i4_41);
        List<? extends FeedItem> actual =
                cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, spaceLeft, maxNumEpisodesPerFeed);

        assertEquals("Resulting candidates: all picked up as there are enough spaces for all",
                expected, actual);
    }

    @Test
    public void cutCandidatesPerSpaceLeft_Boundary_NoSpaceLeft() {
        final int spaceLeft = 0;
        final int maxNumEpisodesPerFeed = 2;

        List<FeedItem> candidates = Arrays.asList(i3_31, i3_32, i3_33);

        List<? extends FeedItem> expected = Arrays.asList();
        List<? extends FeedItem> actual =
                cutCandidatesPerSpaceLeftAndPerFeedLimit(candidates, queue, spaceLeft, maxNumEpisodesPerFeed);

        assertEquals("Resulting candidates: should be empty as there is no space left",
                expected, actual);
    }


    private static Feed feedWithId(long feedId) {
        Feed feed = FeedMother.anyFeed();
        feed.setId(feedId);
        feed.setTitle(feed.getTitle() + feedId);
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
