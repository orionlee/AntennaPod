package de.danoeh.antennapod.uiutil;

import android.util.Log;

import de.danoeh.antennapod.core.feed.EventDistributor;

public abstract class RxWithContentUpdateUiTemplate extends RxUiTemplate {

    private static final String TAG = "RxWContentUpdateUiTmplt";

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
    }

    @Override
    protected void doOnPause() {
        EventDistributor.getInstance().unregister(contentUpdate);
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
