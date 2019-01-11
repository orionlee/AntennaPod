package de.danoeh.antennapod.fragment;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.Callable;

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
public abstract class RxFragmentTemplate<T> {
    private static final String TAG = "RxFragmentTemplate";

    private final Consumer<Throwable> defaultRxErrorConsumer = error -> Log.e(TAG, Log.getStackTraceString(error));

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
     * Subclass may call it at some other points as needed as well.
     *
     * @see #getMainRxSupplierCallable() supplies the content
     * @see #getMainRxResultConsumer() process the content
     */
    protected final void loadMainRxContent() {
        disposeIfAny(); // to be on the safe side
        doLoadMainPreRx();
        // OPEN: Probably should replace Observable.fromCallable with Single.fromCallable
        disposable = Observable.fromCallable(getMainRxSupplierCallable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getMainRxResultConsumer(),
                        getMainRxErrorConsumer());
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

    @NonNull
    protected abstract Callable<? extends T> getMainRxSupplierCallable();

    /**
     * Optional hook
     */
    protected void doOnResumePreRx() {}

    /**
     * Optional hook
     */
    protected void doLoadMainPreRx() {}

    /**
     *
     * @return The Consumer that will process the result returned by invoking {@link #getMainRxSupplierCallable()}
     */
    @NonNull
    protected abstract Consumer<? super T> getMainRxResultConsumer();

    /**
     * Optional hook
     */
    protected void doOnResumePostRx() {}

    /**
     * Optional hook, default is to log the exception in Logcat
     */
    protected Consumer<? super Throwable> getMainRxErrorConsumer() {
      return defaultRxErrorConsumer;
    }


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

}
