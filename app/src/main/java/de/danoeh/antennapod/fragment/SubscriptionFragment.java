package de.danoeh.antennapod.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.SubscriptionsByGroupAdapter;
import de.danoeh.antennapod.adapter.SubscriptionsByGroupDraggableAdapter;
import de.danoeh.antennapod.core.asynctask.FeedRemover;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.core.feed.EventDistributor;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.dialog.RenameFeedDialog;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Fragment for displaying feed subscriptions
 */
public class SubscriptionFragment extends Fragment {

    public static final String TAG = "SubscriptionFragment";

    private static final int EVENTS = EventDistributor.FEED_LIST_UPDATE
            | EventDistributor.UNREAD_ITEMS_UPDATE;

    private static final String SAVED_STATE_EXPANDABLE_ITEM_MANAGER = "RecyclerViewExpandableItemManager";

    public interface ItemAccess {
        int getCount();
        Feed getItem(int position);
        int getFeedCounter(long feedId);
    }

    private RecyclerView subscriptionLayout;
    private SubscriptionsByGroupAdapter subscriptionAdapter;
    private RecyclerViewExpandableItemManager expandableItemManager;
    private RecyclerViewDragDropManager dragDropManager;
    private RecyclerView.Adapter wrappedSubscriptionAdapter;

    private DBReader.NavDrawerData navDrawerData;

    private Subscription subscription;

    public SubscriptionFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        // So, we certainly *don't* have an options menu,
        // but unless we say we do, old options menus sometimes
        // persist.  mfietz thinks this causes the ActionBar to be invalidated
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_subscriptions, container, false);
        subscriptionLayout = root.findViewById(R.id.subscriptions_view);
        registerForContextMenu(subscriptionLayout);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO LATER: support by group UI and flattened list UI,
        // either by supporting both SubscriptionAdapter and SubscriptionsByGroupAdapter,
        // or make SubscriptionsByGroupAdapter covers flattened list UI case
        subscriptionAdapter = new SubscriptionsByGroupDraggableAdapter(getMainActivity(), itemAccess);

        // Setup expandable feature and RecyclerView. Based on
        // https://github.com/h6ah4i/android-advancedrecyclerview/blob/0.11.0/example/src/main/java/com/h6ah4i/android/example/advrecyclerview/demo_e_basic/ExpandableExampleFragment.java
        {
            // Generic for all ExpandableItem type
            //
            final Parcelable eimSavedState = (savedInstanceState != null) ? savedInstanceState.getParcelable(SAVED_STATE_EXPANDABLE_ITEM_MANAGER) : null;
            expandableItemManager = new RecyclerViewExpandableItemManager(eimSavedState);
            wrappedSubscriptionAdapter = expandableItemManager.createWrappedAdapter(subscriptionAdapter);
            subscriptionLayout.setAdapter(wrappedSubscriptionAdapter);
            expandableItemManager.attachRecyclerView(subscriptionLayout);

            // NOTE: need to disable change animations to ripple effect work properly
            ((SimpleItemAnimator) subscriptionLayout.getItemAnimator()).setSupportsChangeAnimations(false);

            // Specific tweak for SubscriptionsByGroupAdapter, to ensure
            // group (which acts as a section header) spans entire row
            RecyclerView.LayoutManager layoutManager =  subscriptionLayout.getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                setupGridLayoutManagerForGroups((GridLayoutManager)layoutManager);
            }

        }

        // Add drag-and-drop feature on top of exapndable one above
        // https://github.com/h6ah4i/android-advancedrecyclerview/blob/0.11.0/example/src/main/java/com/h6ah4i/android/example/advrecyclerview/demo_ed_with_section/ExpandableDraggableWithSectionExampleFragment.java
        {
            dragDropManager = new RecyclerViewDragDropManager();
            wrappedSubscriptionAdapter = dragDropManager.createWrappedAdapter(wrappedSubscriptionAdapter);
            subscriptionLayout.setAdapter(wrappedSubscriptionAdapter);
            dragDropManager.attachRecyclerView(subscriptionLayout);
        }

        loadSubscriptions();

        getMainActivity().getSupportActionBar().setTitle(R.string.subscriptions_label);

        EventDistributor.getInstance().register(contentUpdate);
    }

    @Override
    public void onDestroyView() {
        // release resources associated with the expandable recycler view
        // based on:
        // https://github.com/h6ah4i/android-advancedrecyclerview/blob/0.11.0/example/src/main/java/com/h6ah4i/android/example/advrecyclerview/demo_e_basic/ExpandableExampleFragment.java

        if (expandableItemManager != null) {
            expandableItemManager.release();
            expandableItemManager = null;
        }

        if (dragDropManager!= null) {
            dragDropManager.release();
            dragDropManager= null;
        }

        if (subscriptionLayout != null) {
            subscriptionLayout.setAdapter(null);
            subscriptionLayout = null;
        }

        if (wrappedSubscriptionAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedSubscriptionAdapter);
            wrappedSubscriptionAdapter = null;
        }

        subscriptionAdapter = null;

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save current state to support screen rotation, etc...
        if (expandableItemManager != null) {
            outState.putParcelable(
                    SAVED_STATE_EXPANDABLE_ITEM_MANAGER,
                    expandableItemManager.getSavedState());
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if(subscription != null) {
            subscription.unsubscribe();
        }
    }

    // Make group span entire row
    private void setupGridLayoutManagerForGroups(GridLayoutManager layoutManager) {

        //  TODO LATER: [draggable] during dragging, it incorrectly make some feed
        //  be shown as a group (occupying entire row), the reason is that during dragging,
        //   getGroupByFlattenedPosition(position) is not accurate.
        final int spanCount = layoutManager.getSpanCount();
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (subscriptionAdapter.getGroupByFlattenedPosition(position) != null) {
                    return spanCount;
                } else {
                    return 1;
                }
            }
        });
    }

    private void loadSubscriptions() {
        if(subscription != null) {
            subscription.unsubscribe();
        }
        subscription = Observable.fromCallable(DBReader::getNavDrawerData)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    navDrawerData = result;
                    subscriptionAdapter.refresh();
                    // refresh() MUST be called before notifyDataSetChanged(),
                    // so that the underlying adapter has the latest data in updating the UI
                    subscriptionAdapter.notifyDataSetChanged();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    // Listeners for item views backed by subscriptionAdapter
    //
    // Design Note: onClick() is done within SubscriptionAdapter.ViewHolder.
    // Android's API makes it hard / awkward to centralize all listeners in one place.
    //
    // If we want to centralize all in SubscriptionAdapter.ViewHolder, (arguably the logical place),
    // specifically on the item view itself.
    // - onclick handling is straightforward: the ViewHolder naturally has the data needed
    // - context menu handling is 50-50:
    //   - onCreateContextMenu() can be done easily
    //   - onContextItemSelected(): there is no such hook at item view level.
    //
    // If we want to centralize all in SubscriptionFragment
    // - context menu handling is somewhat straightforward by overriding
    //   onCreateContextMenu() and onContextItemSelected().
    //   - they, however, require additional logic in SubscriptionAdapter.getSelectedItem()
    //     to know the item being selected.
    // - onclick handling is a bit awkward: even though it can be done similarly, it does require
    //   additional (and similar) logic from SubscriptionAdapter to keep track of the item being clicked.
    //   Keeping track of the item being clicked, however, cannot be done with
    //   itemView.setOnClickListener(), because OnClickListener cannot indicate that the click is not consumed.
    //   It is possible to do the tracking via the lower-level OnTouchListener. However, it requires the
    //   the implementation to deal with low-level logic in identifying whether the touch is a click
    //   (as opposed to swipe, drag, etc).
    //

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        Object selectedObject = subscriptionAdapter.getSelectedItem();
        if (selectedObject == null) {
            Log.w(TAG, "onCreateContextMenu(): selected item is unexpectedly null");
            return;
        }

        if (selectedObject.equals(SubscriptionsByGroupAdapter.ADD_ITEM_OBJ)) {
            return;
        }

        Feed feed = (Feed)selectedObject;

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.nav_feed_context, menu);

        menu.setHeaderTitle(feed.getTitle());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        Object selectedObject = subscriptionAdapter.getSelectedItem();
        if (selectedObject == null) {
            Log.w(TAG, "onContextItemSelected(): selected item is unexpectedly null");
            
        }

        if (selectedObject.equals(SubscriptionsByGroupAdapter.ADD_ITEM_OBJ)) {
            // this is the add object, do nothing
            return false;
        }

        Feed feed = (Feed)selectedObject;
        switch(item.getItemId()) {
            case R.id.mark_all_seen_item:
                ConfirmationDialog markAllSeenConfirmationDialog = new ConfirmationDialog(getActivity(),
                        R.string.mark_all_seen_label,
                        R.string.mark_all_seen_confirmation_msg) {

                    @Override
                    public void onConfirmButtonPressed(DialogInterface dialog) {
                        dialog.dismiss();

                        Observable.fromCallable(() -> DBWriter.markFeedSeen(feed.getId()))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(result -> loadSubscriptions(),
                                        error -> Log.e(TAG, Log.getStackTraceString(error)));
                    }
                };
                markAllSeenConfirmationDialog.createNewDialog().show();
                return true;
            case R.id.mark_all_read_item:
                ConfirmationDialog markAllReadConfirmationDialog = new ConfirmationDialog(getActivity(),
                        R.string.mark_all_read_label,
                        R.string.mark_all_read_confirmation_msg) {

                    @Override
                    public void onConfirmButtonPressed(DialogInterface dialog) {
                        dialog.dismiss();
                        Observable.fromCallable(() -> DBWriter.markFeedRead(feed.getId()))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(result -> loadSubscriptions(),
                                        error -> Log.e(TAG, Log.getStackTraceString(error)));
                    }
                };
                markAllReadConfirmationDialog.createNewDialog().show();
                return true;
            case R.id.rename_item:
                new RenameFeedDialog(getActivity(), feed).show();
                return true;
            case R.id.remove_item:
                final FeedRemover remover = new FeedRemover(getContext(), feed) {
                    @Override
                    protected void onPostExecute(Void result) {
                        super.onPostExecute(result);
                        loadSubscriptions();
                    }
                };
                ConfirmationDialog conDialog = new ConfirmationDialog(getContext(),
                        R.string.remove_feed_label,
                        getString(R.string.feed_delete_confirmation_msg, feed.getTitle())) {
                    @Override
                    public void onConfirmButtonPressed(
                            DialogInterface dialog) {
                        dialog.dismiss();
                        long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
                        if (mediaId > 0 &&
                                FeedItemUtil.indexOfItemWithMediaId(feed.getItems(), mediaId) >= 0) {
                            Log.d(TAG, "Currently playing episode is about to be deleted, skipping");
                            remover.skipOnCompletion = true;
                            int playerStatus = PlaybackPreferences.getCurrentPlayerStatus();
                            if(playerStatus == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
                                getActivity().sendBroadcast(new Intent(
                                        PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE));
                            }
                        }
                        remover.executeAsync();
                    }
                };
                conDialog.createNewDialog().show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSubscriptions();
    }

    private MainActivity getMainActivity() {
        return (MainActivity)getActivity();
    }

    private final EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((EVENTS & arg) != 0) {
                Log.d(TAG, "Received contentUpdate Intent.");
                loadSubscriptions();
            }
        }
    };

    private final ItemAccess itemAccess = new ItemAccess() {
        @Override
        public int getCount() {
            if (navDrawerData != null) {
                return navDrawerData.feeds.size();
            } else {
                return 0;
            }
        }

        @Override
        public Feed getItem(int position) {
            if (navDrawerData != null && 0 <= position && position < navDrawerData.feeds.size()) {
                return navDrawerData.feeds.get(position);
            } else {
                return null;
            }
        }

        @Override
        public int getFeedCounter(long feedId) {
            return navDrawerData != null ? navDrawerData.feedCounters.get(feedId) : 0;
        }
    };

}
