package de.danoeh.antennapod.core.storage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.core.feed.FeedItem;

import static de.danoeh.antennapod.core.storage.DBWriterTest.ItemEnqueuePositionCalculatorTest.TF_P_HIGH_ID;
import static de.danoeh.antennapod.core.storage.DBWriterTest.ItemEnqueuePositionCalculatorTest.TF_P_NORMAL_ID;
import static de.danoeh.antennapod.core.storage.DBWriterTest.ItemEnqueuePositionCalculatorTest.tFI_priority;
import static de.danoeh.antennapod.core.storage.DBWriterTest.ItemEnqueuePositionCalculatorTest.toIDs;
import static org.junit.Assert.assertEquals;

public class DBTasksTest {

    @Test
    public void testToFeedItemsOrderedByPriority() {
        FeedItem[] items = new FeedItem[] {
          tFI_priority(50L, TF_P_NORMAL_ID),
                tFI_priority(51L, TF_P_HIGH_ID),
                tFI_priority(52L, TF_P_NORMAL_ID),
                tFI_priority(53L, TF_P_HIGH_ID),
        };

        doTestToFeedItemsOrderedByPriority("Average case",
                items, Arrays.asList(51L, 53L, 50L, 52L));

        doTestToFeedItemsOrderedByPriority("Boundary case",
                new FeedItem[0], Collections.emptyList());

    }

    private void doTestToFeedItemsOrderedByPriority(String msg,
                                                    FeedItem[] items, List<Long> idsExpected) {
        FeedItem[] sorted =  DBTasks.toFeedItemsOrderedByPriority(items);
        assertEquals(msg, idsExpected, toIDs(Arrays.asList(sorted)));
    }

}