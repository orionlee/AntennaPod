package de.danoeh.antennapod.fragment;

import android.util.Log;

import de.danoeh.antennapod.core.feed.EventDistributor;
import de.greenrobot.event.EventBus;

public abstract class RxWithContentUpdateNEventBusFragmentTemplate<T> extends RxFragmentTemplate<T> {

    private static final String TAG = "RxWCntUpdNEvBFrgmntTmpl";

    private final EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & getInterestedEvents()) != 0) {
                Log.d(TAG, "arg: " + arg);
                doContentUpdatePrePx();
                loadMainRxContent();
                doContentUpdatePostPx();
            }
        }
    };

    @Override
    protected void doOnResumePostRx() {
        EventDistributor.getInstance().register(contentUpdate);
        EventBus.getDefault().registerSticky(this);
    }

    @Override
    protected void doOnPause() {
        EventDistributor.getInstance().unregister(contentUpdate);
        EventBus.getDefault().unregister(this);
    }

    /**
     *
     * @see EventDistributor the constants for the events supported
     */
    protected abstract int getInterestedEvents();

    /**
     * Optional hook
     */
    protected void doContentUpdatePrePx() {}

    /**
     * Optional hook
     */
    protected void doContentUpdatePostPx() {}

}
