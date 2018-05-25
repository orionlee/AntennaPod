package de.danoeh.antennapod.adapter;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemViewHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
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
            // TODO LATER: nicer expand / collapse indicator
            String expandCollapseIndicator = group.isExpanded() ? "V" : ">";
            txtvGroupTitle.setText(String.format("%s  %s (%d)", expandCollapseIndicator, group.getTitle(), group.size()));
            group.setViewHolder(this);
        }
    }

    // TODO LATER: factor out common codes with SubscriptionAdapter
    // (Or make the fragment use this adapter for both by group or flat list case)
    public class FeedViewHolder extends AbstractExpandableItemViewHolder {

        private final TextView txtvTitle;
        private final ImageView imgvCover;
        private final TriangleLabelView triangleCountView;

        public FeedViewHolder(View itemView) {
            super(itemView);
            txtvTitle = itemView.findViewById(R.id.txtvTitle);
            imgvCover = itemView.findViewById(R.id.imgvCover);
            triangleCountView = itemView.findViewById(R.id.triangleCountView);
        }

        public void bind(Feed feed) {
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
    }

    private static class Group {
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

        public boolean isExpanded() {
            return expanded;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

    }

    private static class Groups {
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
        // TODO: interpret the position correctly when some groups are collapsed
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
                for (Feed feed : group.feeds) {
                    flattenedItemList.add(feed);
                }
            }
        }


    }

    public static final Object ADD_ITEM_OBJ = new Object() {};

    private final WeakReference<MainActivity> mainActivityRef;
    private final SubscriptionFragment.ItemAccess itemAccess;

    private final Groups groups;

    public SubscriptionsByGroupAdapter(MainActivity mainActivity, SubscriptionFragment.ItemAccess itemAccess) {
        super();
        setHasStableIds(true); // this is required for expandable feature.

        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
        this.groups = createInitialGroups();

        refresh();
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
     * Reflect any changes of the underlying data in {@link #itemAccess}
     */
    public void refresh() {
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

    public Object getSelectedItem() {
        return null; // TODO: to be implemented;
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
        Log.v(TAG, String.format("getChildId(%d, %d)...", groupPosition, childPosition));
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
    }

}
