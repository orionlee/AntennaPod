package de.danoeh.antennapod.core.storage;

import android.support.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedFile;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.feed.FeedMother;
import de.danoeh.antennapod.core.storage.DBWriter.ItemEnqueuePositionCalculator;
import de.danoeh.antennapod.core.storage.DBWriter.ItemEnqueuePositionCalculator.Options;

import static org.junit.Assert.assertEquals;

public class DBWriterTest {

    public static class ItemEnqueuePositionCalculatorTest {

        @RunWith(Parameterized.class)
        public static class IEPCBasicTest {
            @Parameters(name = "{index}: case<{0}>, expected:{1}")
            public static Iterable<Object[]> data() {
                Options optDefault = new Options();
                Options optEnqAtFront = new Options().setEnqueueAtFront(true);

                return Arrays.asList(new Object[][]{
                        {"case default, i.e., add to the end",
                                concat(QUEUE_DEFAULT_IDS, TFI_ID),
                                optDefault, 0, QUEUE_DEFAULT},
                        {"case default (2nd item)",
                                concat(QUEUE_DEFAULT_IDS, TFI_ID),
                                optDefault, 1, QUEUE_DEFAULT},
                        {"case option enqueue at front",
                                concat(TFI_ID, QUEUE_DEFAULT_IDS),
                                optEnqAtFront, 0, QUEUE_DEFAULT},
                        {"case option enqueue at front (2nd item)",
                                list(11L, TFI_ID, 12L, 13L, 14L),
                                optEnqAtFront, 1, QUEUE_DEFAULT},
                        {"case empty queue, option default",
                                list(TFI_ID),
                                optDefault, 0, QUEUE_EMPTY},
                        {"case empty queue, option enqueue at front",
                                list(TFI_ID),
                                optEnqAtFront, 0, QUEUE_EMPTY},
                });
            }

            @Parameter
            public String message;

            @Parameter(1)
            public List<Long> idsExpected;

            @Parameter(2)
            public Options options;

            @Parameter(3)
            public int posAmongAdded; // the position of feed item to be inserted among the list to be inserted.

            @Parameter(4)
            public List<FeedItem> curQueue;


            public static final long TFI_ID = 101;

            /**
             * Add a FeedItem with ID {@link #TFI_ID} with the setup
             */
            @Test
            public void test() {
                ItemEnqueuePositionCalculator calculator = new ItemEnqueuePositionCalculator(options);

                // shallow copy to which the test will add items
                List<FeedItem> queue = new ArrayList<>(curQueue);
                FeedItem tFI = tFI(TFI_ID);
                doAddToQueueAndAssertResult(message,
                        calculator, posAmongAdded, tFI, queue,
                        idsExpected);
            }

        }

        @RunWith(Parameterized.class)
        public static class IEPCKeepInProgressAtFrontTest extends IEPCBasicTest {
            @Parameters(name = "{index}: case<{0}>, expected:{1}")
            public static Iterable<Object[]> data() {
                Options optKeepInProgressAtFront =
                        new Options().setEnqueueAtFront(true).setKeepInProgressAtFront(true);
                // edge case: keep in progress without enabling enqueue at front is meaningless
                Options optKeepInProgressAtFrontWithNoEnqueueAtFront =
                        new Options().setKeepInProgressAtFront(true);

                return Arrays.asList(new Object[][]{
                        {"case option keep in progress at front",
                                list(11L, TFI_ID, 12L, 13L),
                                optKeepInProgressAtFront, 0, QUEUE_FRONT_IN_PROGRESS},
                        {"case option keep in progress at front (2nd item)",
                                list(11L, 12L, TFI_ID, 13L),
                                optKeepInProgressAtFront, 1, QUEUE_FRONT_IN_PROGRESS},
                        {"case option keep in progress at front, front item not in progress",
                                concat(TFI_ID, QUEUE_DEFAULT_IDS),
                                optKeepInProgressAtFront, 0, QUEUE_DEFAULT},
                        {"case option keep in progress at front, front item no media at all",
                                concat(TFI_ID, QUEUE_FRONT_NO_MEDIA_IDS),
                                optKeepInProgressAtFront, 0, QUEUE_FRONT_NO_MEDIA}, // No media should not cause any exception
                        {"case option keep in progress at front, but enqueue at front is disabled",
                                concat(QUEUE_FRONT_IN_PROGRESS_IDS, TFI_ID),
                                optKeepInProgressAtFrontWithNoEnqueueAtFront, 0, QUEUE_FRONT_IN_PROGRESS},
                        {"case empty queue, option keep in progress at front",
                                list(TFI_ID),
                                optKeepInProgressAtFront, 0, QUEUE_EMPTY},
                });
            }

            private static final List<FeedItem> QUEUE_FRONT_IN_PROGRESS = Arrays.asList(tFI(11, 60000), tFI(12), tFI(13));
            private static final List<Long> QUEUE_FRONT_IN_PROGRESS_IDS = toIDs(QUEUE_FRONT_IN_PROGRESS);

            private static final List<FeedItem> QUEUE_FRONT_NO_MEDIA = Arrays.asList(tFINoMedia(11), tFI(12), tFI(13));
            private static final List<Long> QUEUE_FRONT_NO_MEDIA_IDS = toIDs(QUEUE_FRONT_NO_MEDIA);

        }

        @RunWith(Parameterized.class)
        public static class ItemEnqueuePositionCalculatorPreserveDownloadOrderTest {

            @Parameters(name = "{index}: case<{0}>")
            public static Iterable<Object[]> data() {
                Options optDefault = new Options();
                Options optEnqAtFront = new Options().setEnqueueAtFront(true);

                // Attempts to make test more readable by showing the expected list of ids
                // (rather than the expected positions)
                return Arrays.asList(new Object[][] {
                        {"download order test, enqueue default",
                                concat(QUEUE_DEFAULT_IDS, 101L),
                                concat(QUEUE_DEFAULT_IDS, list(101L, 102L)),
                                concat(QUEUE_DEFAULT_IDS, list(101L, 102L, 201L)),
                                concat(QUEUE_DEFAULT_IDS, list(101L, 102L, 201L, 202L)),
                                optDefault, QUEUE_DEFAULT},
                        {"download order test, enqueue at front",
                                concat(101L, QUEUE_DEFAULT_IDS),
                                concat(list(101L, 102L), QUEUE_DEFAULT_IDS),
                                concat(list(101L, 102L, 201L), QUEUE_DEFAULT_IDS),
                                concat(list(101L, 102L, 201L, 202L), QUEUE_DEFAULT_IDS),
                                optEnqAtFront, QUEUE_DEFAULT},
                });
            }

            @Parameter
            public String message;

            @Parameter(1)
            public List<Long> idsExpectedAfter101;

            @Parameter(2)
            public List<Long> idsExpectedAfter102;

            // 2XX are for testing bulk insertion cases
            @Parameter(3)
            public List<Long> idsExpectedAfter201;

            @Parameter(4)
            public List<Long> idsExpectedAfter202;

            @Parameter(5)
            public Options options;

            @Parameter(6)
            public List<FeedItem> queueInitial;

            @Test
            public void testQueueOrderWhenDownloading2Items() {

                // Setup class under test
                //
                ItemEnqueuePositionCalculator calculator = new ItemEnqueuePositionCalculator(options);
                MockDownloadRequester mockDownloadRequester = new MockDownloadRequester();
                calculator.requester = mockDownloadRequester;

                // Setup initial data
                // A shallow copy, as the test code will manipulate the queue
                List<FeedItem> queue = new ArrayList<>(queueInitial);


                // Test body

                // User clicks download on feed item 101
                FeedItem tFI101 = tFI_isDownloading(101, mockDownloadRequester);
                doAddToQueueAndAssertResult(message + " (1st download)",
                        calculator, 0, tFI101, queue,
                        idsExpectedAfter101);

                // Then user clicks download on feed item 102
                FeedItem tFI102 = tFI_isDownloading(102, mockDownloadRequester);
                doAddToQueueAndAssertResult(message + " (2nd download, it should preserve order of download)",
                        calculator, 0, tFI102, queue,
                        idsExpectedAfter102);

                // Items 201 and 202 are added as part of a single DBWriter.addQueueItem() calls

                FeedItem tFI201 = tFI_isDownloading(201, mockDownloadRequester);
                doAddToQueueAndAssertResult(message + " (bulk insertion, 1st item)",
                        calculator, 0, tFI201, queue,
                        idsExpectedAfter201);

                FeedItem tFI202 = tFI_isDownloading(202, mockDownloadRequester);
                doAddToQueueAndAssertResult(message + " (bulk insertion, 2nd item)",
                        calculator, 1, tFI202, queue,
                        idsExpectedAfter202);

                // TODO: simulate download failure cases.
            }


            private static FeedItem tFI_isDownloading(int id, MockDownloadRequester requester) {
                FeedItem item = tFI(id);
                FeedMedia media =
                        new FeedMedia(item, "http://download.url.net/" + id
                                , 100000 + id, "audio/mp3");
                media.setId(item.getId());
                item.setMedia(media);

                requester.mockDownloadingFile(media, true);

                return item;
            }

            private static class MockDownloadRequester implements FeedFileDownloadStatusRequesterInterface {

                private Map<Long, Boolean> downloadingByIds = new HashMap<>();

                @Override
                public synchronized boolean isDownloadingFile(@NonNull FeedFile item) {
                    return downloadingByIds.getOrDefault(item.getId(), false);
                }

                // All other parent methods should not be called

                public void mockDownloadingFile(FeedFile item, boolean isDownloading) {
                    downloadingByIds.put(item.getId(), isDownloading);
                }
            }
        }

        @RunWith(Parameterized.class)
        public static class ItemEnqueueFeedPriorityTest {

            @Parameters(name = "{index}: case<{0}>")
            public static Iterable<Object[]> data() {
                Options optDefault = new Options();
                Options optEnqAtFront = new Options().setEnqueueAtFront(true);

                // Attempts to make test more readable by showing the expected list of ids
                // (rather than the expected positions)
                return Arrays.asList(new Object[][] {
                        {"feed priority test, enqueue default",
                                // 101, of high priority, still put to the front
                                concat(T101H, QUEUE_W_PRIORITY_IDS ),
                                // 102, of normal priority, remains at the back
                                list(T101H, 11L, 12L, 13L, T102N),
                                list(T101H, T103H, 11L, 12L, 13L, T102N),
                                concat(QUEUE_W_PRIORITY_IDS, T201N ),
                                // 202, of high priority, is pushed to the front (as part of 201 - 202 bulk)
                                list(T202H, 11L, 12L, 13L, T201N),
                                optDefault, QUEUE_W_PRIORITY},

                        {"feed priority test, enqueue at front",
                                concat(T101H, QUEUE_W_PRIORITY_IDS ),
                                // 102, of normal priority, will be put behind 101,
                                // instead of being enqueued at front
                                // OPEN: what happens if 101 and 102 are both in the midst of downloading?
                                // i.e., how does priority rule interact with respect download order rules?
                                concat(list(T101H, T102N), QUEUE_W_PRIORITY_IDS ),
                                concat(list(T103H, T101H, T102N), QUEUE_W_PRIORITY_IDS ),
                                concat(list(T201N), QUEUE_W_PRIORITY_IDS ),
                                // OPEN: 202, of high priority, is NOT pushed to the front
                                // (as part of 201 - 202 bulk insertion).
                                // To push 202 to the front, callers should first sort the items to be queued
                                // based on priority before making the call. See DBTasks#downloadFeedItems()
                                concat(list(T201N, T202H), QUEUE_W_PRIORITY_IDS ),
                                optEnqAtFront, QUEUE_W_PRIORITY},
                });
            }

            private static final long T101H = 101L;
            private static final long T102N = 102L;
            private static final long T103H = 103L;
            private static final long T201N = 201L;
            private static final long T202H = 202L;

            @Parameter
            public String message;

            @Parameter(1)
            public List<Long> idsExpectedAfter101;

            @Parameter(2)
            public List<Long> idsExpectedAfter102;

            @Parameter(3)
            public List<Long> idsExpectedAfter103;

            // 2XX are for testing bulk insertion cases
            @Parameter(4)
            public List<Long> idsExpectedAfter201;

            @Parameter(5)
            public List<Long> idsExpectedAfter202;

            @Parameter(6)
            public Options options;

            @Parameter(7)
            public List<FeedItem> queueInitial;

            @Test
            public void testFeedPriority() {
                // Setup class under test
                //
                ItemEnqueuePositionCalculator calculator = new ItemEnqueuePositionCalculator(options);

                // Setup initial data
                // A shallow copy, as the test code will manipulate the queue
                List<FeedItem> queue = new ArrayList<>(queueInitial);

                // Test body

                // User download on feed item 101, high priority
                FeedItem tFI101 = tFI_priority(T101H, TF_P_HIGH_ID);
                doAddToQueueAndAssertResult(message + " (1st download [high priority])",
                        calculator, 0, tFI101, queue,
                        idsExpectedAfter101);

                FeedItem tFI102 = tFI_priority(T102N, TF_P_NORMAL_ID);
                doAddToQueueAndAssertResult(message + " (2nd download [normal priority])",
                        calculator, 0, tFI102, queue,
                        idsExpectedAfter102);

                FeedItem tFI103 = tFI_priority(T103H, TF_P_HIGH_ID);
                doAddToQueueAndAssertResult(message + " (3rd download [high priority])",
                        calculator, 0, tFI103, queue,
                        idsExpectedAfter103);

                // Reset the queue for bulk insertion case (in auto download?)
                queue = new ArrayList<>(queueInitial);

                FeedItem tFI201 = tFI_priority(T201N, TF_P_NORMAL_ID);
                doAddToQueueAndAssertResult(message + " (bulk insertion, 1st item [normal priority])",
                        calculator, 0, tFI201, queue,
                        idsExpectedAfter201);

                FeedItem tFI202 = tFI_priority(T202H, TF_P_HIGH_ID);
                doAddToQueueAndAssertResult(message + " (bulk insertion, 2nd item [high priority])",
                        calculator, 1, tFI202, queue,
                        idsExpectedAfter202);
            }

            private static List<FeedItem> QUEUE_W_PRIORITY =
                    Collections.unmodifiableList(
                            Arrays.asList(tFI_priority(11L, TF_P_NORMAL_ID),
                                    tFI_priority(12L, TF_P_HIGH_ID),
                                    tFI_priority(13L, TF_P_NORMAL_ID)));

            private static final List<Long> QUEUE_W_PRIORITY_IDS =
                    QUEUE_W_PRIORITY.stream().map(fi -> fi.getId()).collect(Collectors.toList());

        }

        // Common helpers:
        // - common queue (of items) for tests
        // - construct FeedItems for tests

        static void doAddToQueueAndAssertResult(String message,
                                                ItemEnqueuePositionCalculator calculator,
                                                int positionAmongAdd,
                                                FeedItem itemToAdd,
                                                List<FeedItem> queue,
                                                List<Long> idsExpected) {
            int posActual = calculator.calcPosition(positionAmongAdd, itemToAdd, queue);
            queue.add(posActual, itemToAdd);
            assertEquals(message, idsExpected, toIDs(queue));
        }

        static final List<FeedItem> QUEUE_EMPTY = Collections.unmodifiableList(Arrays.asList());

        static final List<FeedItem> QUEUE_DEFAULT = Collections.unmodifiableList(Arrays.asList(tFI(11), tFI(12), tFI(13), tFI(14)));
        static final List<Long> QUEUE_DEFAULT_IDS = QUEUE_DEFAULT.stream().map(fi -> fi.getId()).collect(Collectors.toList());


        static FeedItem tFI(long id) {
            return tFI(id, -1);
        }

        static FeedItem tFI(long id, int position) {
            FeedItem item = tFINoMedia(id);
            FeedMedia media = new FeedMedia(item, "download_url", 1234567, "audio/mpeg");
            media.setId(item.getId());
            item.setMedia(media);

            if (position >= 0) {
                media.setPosition(position);
            }

            return item;
        }

        static FeedItem tFINoMedia(long id) {
            FeedItem item = new FeedItem(id, "Item" + id, "ItemId" + id, "url",
                    new Date(), FeedItem.PLAYED, FeedMother.anyFeed());
            return item;
        }

        static final long TF_P_NORMAL_ID = 1;
        static final long TF_P_HIGH_ID = 2;
        static final Map<Long, Integer> T_FEED_PRIORITIES;
        static {
            T_FEED_PRIORITIES = new HashMap<Long, Integer>();
            T_FEED_PRIORITIES.put(TF_P_NORMAL_ID, Feed.PRIORITY_NORMAL);
            T_FEED_PRIORITIES.put(TF_P_HIGH_ID, Feed.PRIORITY_HIGH);
        }

        static FeedItem tFI_priority(long itemId, long feedId) {
            FeedItem item = tFI(itemId);
            item.getFeed().setId(feedId);
            item.getFeed().setPriority(T_FEED_PRIORITIES.getOrDefault(feedId, Feed.PRIORITY_NORMAL));

            return item;
        }


        // Collections helpers

        static <T> List<? extends T> concat(T item, List<? extends T> list) {
            List<T> res = new ArrayList<>(list);
            res.add(0, item);
            return res;
        }

        static <T> List<? extends T> concat(List<? extends T> list, T item) {
            List<T> res = new ArrayList<>(list);
            res.add(item);
            return res;
        }

        static <T> List<? extends T> concat(List<? extends T> list1, List<? extends T> list2) {
            List<T> res = new ArrayList<>(list1);
            res.addAll(list2);
            return res;
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        public static <T> List<T> list(T... a) {
            return Arrays.asList(a);
        }


        static List<Long> toIDs(List<FeedItem> items) {
            return items.stream().map(i->i.getId()).collect(Collectors.toList());
        }

    }

}
