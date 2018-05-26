package de.danoeh.antennapod.adapter;

import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemViewHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
import de.danoeh.antennapod.fragment.ItemlistFragment;
import de.danoeh.antennapod.fragment.SubscriptionFragment;
import jp.shts.android.library.TriangleLabelView;

/**
 * Adapter tha can render the subscriptions / list of feeds by group.
 * The implementation currently groups the feeds by feed priority.
 * But the majority of the logic is agnostic to how the feeds are grouped.
 *
 * Based on example in
 * https://github.com/h6ah4i/android-advancedrecyclerview/blob/0.11.0/example/src/main/java/com/h6ah4i/android/example/advrecyclerview/demo_e_minimal/MinimalExpandableExampleActivity.java
 *
 */
public class SubscriptionsByGroupAdapter extends
        AbstractExpandableItemAdapter<SubscriptionsByGroupAdapter.GroupViewHolder, SubscriptionsByGroupAdapter.FeedViewHolder> {

    private static final String TAG = "SubsByGroupAdapter";

    public class GroupViewHolder extends AbstractExpandableItemViewHolder {
        private final TextView txtvGroupTitle;

        public GroupViewHolder(View itemView) {
            super(itemView);
            txtvGroupTitle = itemView.findViewById(R.id.txtvGroupTitle);
        }


        public void bind(Group group) {
            @DrawableRes int expandIndicator =
                    group.isExpanded() ? R.drawable.ic_expand_less_gray_36dp :
                            R.drawable.ic_expand_more_gray_36dp;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                txtvGroupTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(expandIndicator, 0 , 0, 0);
            } else {
                txtvGroupTitle.setCompoundDrawablesWithIntrinsicBounds(expandIndicator, 0, 0, 0);
            }

            txtvGroupTitle.setText(String.format("%s (%d)", group.getTitle(), group.size()));
        }
    }

    // TODO LATER: factor out common codes with SubscriptionAdapter
    // (Or make the fragment use this adapter for both by group or flat list case)
    public class FeedViewHolder extends AbstractExpandableItemViewHolder
            implements View.OnClickListener, View.OnLongClickListener,
            DraggableItemViewHolder {

        private final TextView txtvTitle;
        private final ImageView imgvCover;
        private final TriangleLabelView triangleCountView;

        private Feed feed = null;

        public FeedViewHolder(View itemView) {
            super(itemView);
            txtvTitle = itemView.findViewById(R.id.txtvTitle);
            imgvCover = itemView.findViewById(R.id.imgvCover);
            triangleCountView = itemView.findViewById(R.id.triangleCountView);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        public void bind(Feed feed) {
            this.feed = feed;

            txtvTitle.setText(feed.getTitle());

            int count = itemAccess.getFeedCounter(feed.getId());
            if(count > 0) {
                triangleCountView.setPrimaryText(String.valueOf(itemAccess.getFeedCounter(feed.getId())));
                triangleCountView.setVisibility(View.VISIBLE);
            } else {
                triangleCountView.setVisibility(View.GONE);
            }

            Glide.with(mainActivityRef.get())
                    .load(feed.getImageLocation())
                    .error(R.color.light_gray)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .fitCenter()
                    .dontAnimate()
                    .into(new CoverTarget(null, txtvTitle, imgvCover, mainActivityRef.get()));

        }

        @Override
        public void onClick(View v) {
            if (feed != null) {
                Fragment fragment = ItemlistFragment.newInstance(feed.getId());
                mainActivityRef.get().loadChildFragment(fragment);
            }
        }

        // Track the item being long-clicked so that the context menu handling logic
        // in parent SubscriptionFragment can have access to the backing feed object.
        @Override
        public boolean onLongClick(View v) {
            //  TODO LATER: [draggable] exclude draggable area from invoking context menu (i.e., return false)
            // It cannot be done here, as onLongClick does not provide the details on where the long-click
            // happens.
            // To do it properly, it might need to be done with the lower-level on touch listeners instead,
            // Or maybe create a view for drag handle (and set on LongClick there)
            SubscriptionsByGroupAdapter.this.selectedItem = feed;
            return false;
        }

        //
        // Draggable support
        //  TODO LATER: [draggable] it should not be done  here in the expandable interface layer. A hack for now

        @DraggableItemStateFlags
        private int mDragStateFlags;

        @Override
        public void setDragStateFlags(@DraggableItemStateFlags int flags) {
            mDragStateFlags = flags;
        }

        @Override
        @DraggableItemStateFlags
        public int getDragStateFlags() {
            return mDragStateFlags;
        }
    }

    protected static class Group {
        private final long id;
        private final String title;
        private final List<Feed> feeds;

        // UI states
        private boolean expanded = true;

        public Group(long id, String title) {
            this.id = id;
            this.title = title;
            this.feeds = new ArrayList<>();;
        }

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public int size() {
            return feeds.size();
        }

        public boolean add(Feed feed) {
            return feeds.add(feed);
        }

        public boolean remove(Object o) {
            return feeds.remove(o);
        }

        public Feed get(int index) {
            return feeds.get(index);
        }

        public boolean isExpanded() {
            return expanded;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

    }

    protected static class Groups {
        private final List<Group> groups;
        private final List<Object> flattenedItemList;

        public Groups() {
            this.groups = new ArrayList<>();
            this.flattenedItemList = new ArrayList<>();
        }

        public boolean add(Group group) {
            return groups.add(group);
        }

        public int size() {
            return groups.size();
        }

        public Group get(int index) {
            return groups.get(index);
        }

        public void clearFeeds() {
            for (Group group : groups) {
                group.feeds.clear();
            }
            updateFlattenedItemList();
        }

        /**
         *
         * @param pos the flattened raw position as seen by the underlying RecyclerView
         *            (RecyclerView has no concept of groups, it only sees a flat list of
         *            groups + feeds)
         *
         * @see android.support.v7.widget.GridLayoutManager.SpanSizeLookup if caller implements
         * APIs such as {@code SpanSizeLookup} that requires the flattened raw position, this
         * method can help the caller to access the correspond object
         *
         * @return the group object for the named position, null otherwise
         */
        public Group getGroupByVisibleFlattenedPosition(int pos) {
            Object item = getItemByFlattenedPosition(pos);

            if (item instanceof Group) {
                return (Group)item;
            } else {
                return null;
            }
        }

        private Object getItemByFlattenedPosition(int pos) {
            try {
                return flattenedItemList.get(pos);
            } catch (IndexOutOfBoundsException e) {
                Log.w(TAG, String.format("getItemByFlattenedPosition(%d): unexpectedly, no such item exists", pos));
                return null;
            }
        }

        void updateFlattenedItemList() {
            flattenedItemList.clear();
            for (Group group : groups) {
                flattenedItemList.add(group);
                if (group.isExpanded()) {
                    for (Feed feed : group.feeds) {
                        flattenedItemList.add(feed);
                    }
                }
                // else the feeds are note visible,
                // not counted in the flatten list as seen by the underlying RecyclerView
            }
        }


    }

    public static final Object ADD_ITEM_OBJ = new Object() {};

    private final WeakReference<MainActivity> mainActivityRef;
    private final SubscriptionFragment.ItemAccess itemAccess;

    protected final Groups groups;

    // UI states
    private Feed selectedItem; // for the use with binding item context menu

    public SubscriptionsByGroupAdapter(MainActivity mainActivity, SubscriptionFragment.ItemAccess itemAccess) {
        super();
        setHasStableIds(true); // this is required for expandable feature.

        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
        this.groups = createInitialGroups();
    }

    // BEGIN Priority-specific
    //
    private static final int IDX_PRI_HIGH = 0;
    private static final int IDX_PRI_NORMAL = 1;
    private static final int IDX_PRI_LOW = 2;

    protected Groups createInitialGroups() {
        Groups res = new Groups();
        res.add(new Group(100, "High"));
        res.add(new Group(101, "Normal"));
        res.add(new Group(102, "Low"));
        return res;
    }


    // TODO LATER: for now hardcoded for experiment.
    /**
     * Update internal data structures for UI based of the underlying data in {@link #itemAccess}
     *
     * @see #notifyDataSetChanged() For users of this class, {@link #refresh()} should be called before calling
     * {@link #notifyDataSetChanged()}, to ensure it operates on the latest data.
     */
    public void refresh() {
        // TODO LATER: requiring callers to explicitly call refresh() is error-prone.
        //
        // It can be eliminated if the logic here can construct the equivalent of {@link #groups}
        // data structure, used by the adapter implementation to supply the data to construct UI,
        // can be created on demand from {@link #itemAccess}
        groups.clearFeeds();

        Log.v(TAG, "refresh() - #items: " + itemAccess.getCount());
        for (int i = 0; i < itemAccess.getCount(); i++) {
            Feed feed = itemAccess.getItem(i);
            Group group = null;
            if (feed.getTitle().matches(".*(Documentary|Up\\sFirst).*")) {
                group = getGroupByPos(IDX_PRI_HIGH);
            } else if (feed.getTitle().matches(".*(Hello|UnderCurrents).*")) {
                group = getGroupByPos(IDX_PRI_LOW);
            } else {
                group = getGroupByPos(IDX_PRI_NORMAL);
            }
            if (group != null) {
                group.add(feed);
            }
        }

        groups.updateFlattenedItemList();
    }

    //
    // END Priority-specific

    public Feed getSelectedItem() {
        return selectedItem;
    }

    public Group getGroupByFlattenedPosition(int pos) {
        return groups.getGroupByVisibleFlattenedPosition(pos);
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getChildCount(int groupPosition) {
        return getGroupByPos(groupPosition).size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroupByPos(groupPosition).getId();
    }

    private @NonNull Group getGroupByPos(int groupPosition) {
        try {
            return groups.get(groupPosition);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Group with position " + groupPosition + " does not exist");
        }
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        // TODO LATER: deal with id range restriction imposed by advanced-recyclerview
        return getFeedByPos(groupPosition, childPosition).getId();
    }

    private Feed getFeedByPos(int groupPosition, int childPosition) {
        Group group = getGroupByPos(groupPosition);
        try {
            Feed feed = group.feeds.get(childPosition);
            return feed;
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Child with position " + childPosition + " in group id < " + group.getId() + " > does not exist");
        }
    }


    @Override
    public GroupViewHolder onCreateGroupViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.subscription_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public FeedViewHolder onCreateChildViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.subscription_item, parent, false);
        return new FeedViewHolder(view);
    }

    @Override
    public void onBindGroupViewHolder(GroupViewHolder holder, int groupPosition, int viewType) {
        Group group = getGroupByPos(groupPosition);
        holder.bind(group);
    }

    @Override
    public void onBindChildViewHolder(FeedViewHolder holder, int groupPosition, int childPosition, int viewType) {
        Feed feed = getFeedByPos(groupPosition, childPosition);
        holder.bind(feed);
    }

    @Override
    public boolean onCheckCanExpandOrCollapseGroup(GroupViewHolder holder, int groupPosition, int x, int y, boolean expand) {
        return true;
    }

    @Override
    public boolean getInitialGroupExpandedState(int groupPosition) {
        return true;
    }

    @Override
    public boolean onHookGroupExpand(int groupPosition, boolean fromUser) {
        Group group = getGroupByPos(groupPosition);
        if (group != null) {
            group.setExpanded(true);
            notifyGroupChanged(group);
        }
        return true;
    }

    @Override
    public boolean onHookGroupCollapse(int groupPosition, boolean fromUser) {
        Group group = getGroupByPos(groupPosition);
        if (group != null) {
            group.setExpanded(false);
            notifyGroupChanged(group);
        }
        return true;
    }

    private void notifyGroupChanged(@NonNull Group group) {
        int flattenedPosition = groups.flattenedItemList.indexOf(group); // TODO LATER: better abstraction
        notifyItemChanged(flattenedPosition);
        // once expand/collapse done, update the flattened item list as seen by RecyclerView
        groups.updateFlattenedItemList();
    }

    // TODO LATER: support add podcast (maybe through an option menu on the parent fragment instead)


}
