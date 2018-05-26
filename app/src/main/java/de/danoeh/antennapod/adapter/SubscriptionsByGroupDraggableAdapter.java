package de.danoeh.antennapod.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags;
import com.h6ah4i.android.widget.advrecyclerview.expandable.ExpandableDraggableItemAdapter;

import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.fragment.SubscriptionFragment;

public class SubscriptionsByGroupDraggableAdapter extends SubscriptionsByGroupAdapter
        implements ExpandableDraggableItemAdapter<SubscriptionsByGroupAdapter.GroupViewHolder,
        SubscriptionsByGroupDraggableAdapter.FeedDraggableViewHolder> {

    public class FeedDraggableViewHolder extends FeedViewHolder
            implements DraggableItemViewHolder {

        public FeedDraggableViewHolder(View itemView) {
            super(itemView);
        }

        // Based on AbstractDraggableItemViewHolder

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

    public SubscriptionsByGroupDraggableAdapter(MainActivity mainActivity, SubscriptionFragment.ItemAccess itemAccess) {
        super(mainActivity, itemAccess);
    }

    @Override
    public FeedDraggableViewHolder onCreateChildViewHolder(ViewGroup parent, int viewType) {
        View view = createChildView(parent, viewType);
        return new FeedDraggableViewHolder(view);
    }

    //
    // Drag Drop API implementation
    //

    @Override
    public boolean onCheckChildCanStartDrag(FeedDraggableViewHolder  holder, int groupPosition, int childPosition, int x, int y) {
        // TODO LATER: can drag when the touch position is in left 1/3 of the item
        return (x < holder.itemView.getWidth() * 0.33);
    }

    @Override
    public ItemDraggableRange onGetChildItemDraggableRange(FeedDraggableViewHolder holder, int groupPosition, int childPosition) {
        return null; // default: all places permissible
    }

    @Override
    public void onMoveChildItem(int fromGroupPosition, int fromChildPosition, int toGroupPosition, int toChildPosition) {
        { // TODO LATER: should move in the backend instead.
            Group fromGroup = groups.get(fromGroupPosition);
            Group toGroup = groups.get(toGroupPosition);

            Feed feed = fromGroup.get(fromChildPosition);
            fromGroup.remove(feed);
            toGroup.add(feed);
            groups.updateFlattenedItemList();
        }
    }

    @Override
    public boolean onCheckChildCanDrop(int draggingGroupPosition, int draggingChildPosition, int dropGroupPosition, int dropChildPosition) {
        return false;
    }

    @Override
    public void onChildDragStarted(int groupPosition, int childPosition) {
        notifyDataSetChanged();
    }

    @Override
    public void onChildDragFinished(int fromGroupPosition, int fromChildPosition, int toGroupPosition, int toChildPosition, boolean result) {
        notifyDataSetChanged();
    }

    // Groups not draggable
    // TODO LATER OPEN: refactor them to a MixIn using default methods in interface (ran into syntax problem with generics)

    @Override
    public boolean onCheckGroupCanStartDrag(SubscriptionsByGroupAdapter.GroupViewHolder holder, int groupPosition, int x, int y) {
        return false;
    }

    @Override
    public ItemDraggableRange onGetGroupItemDraggableRange(SubscriptionsByGroupAdapter.GroupViewHolder holder, int groupPosition) {
        throw new UnsupportedOperationException("Group is not draggable");
    }

    @Override
    public void onMoveGroupItem(int fromGroupPosition, int toGroupPosition) {
        throw new UnsupportedOperationException("Group is not draggable");
    }

    @Override
    public boolean onCheckGroupCanDrop(int draggingGroupPosition, int dropGroupPosition) {
        return false;
    }

    @Override
    public void onGroupDragStarted(int groupPosition) {
        throw new UnsupportedOperationException("Group is not draggable");
    }

    @Override
    public void onGroupDragFinished(int fromGroupPosition, int toGroupPosition, boolean result) {
        throw new UnsupportedOperationException("Group is not draggable");
    }

}
