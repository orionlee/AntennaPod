package de.danoeh.antennapod.adapter;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
import de.danoeh.antennapod.fragment.AddFeedFragment;
import de.danoeh.antennapod.fragment.ItemlistFragment;
import jp.shts.android.library.TriangleLabelView;

/**
 * Adapter for subscriptions
 */
public class SubscriptionsAdapter extends RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder> {

    /** placeholder object that indicates item should be added */
    public static final Object ADD_ITEM_OBJ = new Object();

    /** the position in the view that holds the add item; 0 is the first, -1 is the last position */
    private static final int ADD_POSITION = -1;
    private static final String TAG = "SubscriptionsAdapter";

    public interface ItemAccess {
        int getCount();
        Feed getItem(int position);
        int getFeedCounter(long feedId);
    }

    public class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        private final TextView txtvTitle;
        private final ImageView imgvCover;
        private final TriangleLabelView triangleCountView;

        // TODO LATER: consider to use a generic Object item
        private Feed feed; // the underlying data backing this ViewHolder

        public ViewHolder(View itemView) {
            super(itemView);
            txtvTitle = itemView.findViewById(R.id.txtvTitle);
            imgvCover = itemView.findViewById(R.id.imgvCover);
            triangleCountView = itemView.findViewById(R.id.triangleCountView);
            itemView.setOnClickListener(this);
        }

        void bind(Feed feed) {
            if (feed == null) {
                Log.w(TAG, "SubscriptionsAdapter.ViewHolder.bind(Feed): feed is unexpectedly null. silently return.");
                return;
            }

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

        void bindAddFeed() {
            this.feed = null;

            txtvTitle.setText("{md-add 500%}\n\n" + mainActivityRef.get().getString(R.string.add_feed_label));

            // prevent any accidental re-use of old values (not sure how that would happen...)
            triangleCountView.setPrimaryText("");
            // make it go away, we don't need it for add feed
            triangleCountView.setVisibility(View.INVISIBLE);

            // when this holder is reused, we could else end up with a cover image
            Glide.clear(imgvCover);
        }

        private boolean isItemAddFeed() {
            return this.feed == null;
        }

        @Override
        public void onClick(View v) {
            if (isItemAddFeed()) {
                mainActivityRef.get().loadChildFragment(new AddFeedFragment());
            } else {
                Fragment fragment = ItemlistFragment.newInstance(feed.getId());
                mainActivityRef.get().loadChildFragment(fragment);
            }
        }

    }

    private final WeakReference<MainActivity> mainActivityRef;
    private final ItemAccess itemAccess;

    private Object selectedItem; // for the use with binding item context menu

    public SubscriptionsAdapter(MainActivity mainActivity, ItemAccess itemAccess) {
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
    }


    public int getAddTilePosition() {
        if(ADD_POSITION < 0) {
            return ADD_POSITION + getItemCount();
        }
        return ADD_POSITION;
    }

    private int getAdjustedPosition(int origPosition) {
        return origPosition < getAddTilePosition() ? origPosition : origPosition - 1;
    }

    @Override
    public int getItemCount() {
        return 1 + itemAccess.getCount();
    }

    @Override
    public long getItemId(int position) {
        if (position == getAddTilePosition()) {
            return 0;
        }
        return itemAccess.getItem(getAdjustedPosition(position)).getId();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.subscription_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position == getAddTilePosition()) {
            holder.bindAddFeed();
            holder.itemView.setOnLongClickListener(v -> { // TODO LATER: not needed when context menu handling moved to ViewHolder
                selectedItem = ADD_ITEM_OBJ;
                return false;
            });
        } else {
            Feed feed = itemAccess.getItem(position);
            holder.bind(feed);
            holder.itemView.setOnLongClickListener(v -> {
                selectedItem = feed;
                return false;
            });
        }
    }

    /**
     *
     * @return the item the user long-clicked, for, e.g., invoking context menu.
     * Typically a {@link Feed} object, but it can also be {@link #ADD_ITEM_OBJ} to indicate
     * the item is the UI control to launch {@link AddFeedFragment}
     */
    // TODO LATER: not needed if context menu handling is moved to ViewHolder
    @Nullable
    public Object getSelectedItem() {
        return selectedItem;
    }

    // TODO LATER: remove it once done
    private void dbgMessage(String message) {
        Toast.makeText(mainActivityRef.get().getApplicationContext(),
                message,
                Toast.LENGTH_SHORT)
                .show();
    }

}
