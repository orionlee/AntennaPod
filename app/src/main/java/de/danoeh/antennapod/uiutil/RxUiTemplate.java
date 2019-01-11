package de.danoeh.antennapod.uiutil;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Provide a template to load content with RxJava in a fragment class.
 * The supported pattern is that the fragment has some primary content that
 * needs to loaded with RxJava, and display them once there.
 *
 * The template manages the life cycle of the RxJava artifact, and provides
 * default error handling.
 */
public abstract class RxUiTemplate {
    private static final String TAG = "RxUiTemplate";

    protected final Consumer<Throwable> defaultRxErrorConsumer = error -> Log.e(TAG, Log.getStackTraceString(error));

    @Nullable
    private Disposable disposable;

    /**
     * Callers MUST invoke onResume in their on <code>onResume</code> implementation
     */
    public final void onResume() {
        doOnResumePreRx();
        loadMainRxContent();
        doOnResumePostRx();
    }

    /**
     * Load the main content with RxJava, primarily during {@link #onResume()}.
     * Owning class may call it at some other points as needed as well.
     *
     * @see #doLoadMainPreRx() subclass-specific actual RxJava call
     */
    public final void loadMainRxContent() {
        disposeIfAny(); // to be on the safe side
        doLoadMainPreRx();
        disposable = doLoadMainRxContent();
    }

    /**
     * Callers MUST invoke onPause in their on <code>onPause</code> implementation
     */
    public final void onPause() {
        try {
            doOnPause();
        } finally {
            disposeIfAny();
        }
    }

    /**
     * Optional hook
     */
    protected void doOnResumePreRx() {}

    /**
     * Optional hook
     */
    protected void doLoadMainPreRx() {}

    @NonNull
    protected abstract Disposable doLoadMainRxContent();

    /**
     * Optional hook
     */
    protected void doOnResumePostRx() {}



    /**
     * Optional hook
     */
    protected void doOnPause() {}

    private void disposeIfAny() {
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
    }

    //
    // Helpers
    //

    @NonNull
    protected static <T> Observable<T> withDefaultSchedulers(Observable<T> observable) {
        return observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
