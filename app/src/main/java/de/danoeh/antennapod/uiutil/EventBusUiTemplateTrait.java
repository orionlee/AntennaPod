package de.danoeh.antennapod.uiutil;

import de.greenrobot.event.EventBus;

/**
 * Android UI classes (Activity, Fragment) using this trait MUST call the provided lifecycle methods
 * explicitly in their correspond methods
 * - <code>EventBusUiTemplateTrait.super.onResume();</code>
 * - <code>EventBusUiTemplateTrait.super.onPause();</code>
 */
public interface EventBusUiTemplateTrait {
    default void onResume() {
        EventBus.getDefault().registerSticky(this);
    }

    default void onPause() {
        EventBus.getDefault().unregister(this);
    }
}
