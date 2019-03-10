package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.service.playback.PlaybackService;

/** Receives media button events. */
public class MediaButtonReceiver extends BroadcastReceiver {
	private static final String TAG = "MediaButtonReceiver";
	public static final String EXTRA_KEYCODE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.KEYCODE"; // TODO-2716 can be retired
	public static final String EXTRA_SOURCE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.SOURCE"; // TODO-2716 can be retired

	public static final String NOTIFY_BUTTON_RECEIVER = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER"; // TODO-2716 can be retired

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Received intent");
		if (intent == null || intent.getExtras() == null) {
			return;
		}
		KeyEvent event = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
		sendKeyEvent(context, event);
		Log.v(TAG, "onReceive() returned.");
	}

	// This mimics how support lib MediaButtonReceiver works
	// , with the underlying MediaBrowserCompat.connect() and the underlying MediaBrowser.connect()
	private void sendKeyEvent(Context context, KeyEvent event) {
		if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
			return;
		}

		// MUST use applicationContext, BroadcastReceiver's context does not allow bindService
		final Context appContext = context.getApplicationContext();

		Log.d(TAG, "Sending KeyEvent to PlaybackService: " + event);
		Handler mainThreadHandler = new Handler();
		PendingResult pendingResult = goAsync(); // mimic support lib MediaButtonReceiver.onReceive()
		// Mimic MediaBrowserCompat.connect() and the underlying MediaBrowser.connect(), i.e.,
		// binding the service in a handler.post() call
		mainThreadHandler.post(() -> {
			// use mainThreadHandler.post() to ensure service binding is done in main thread, in case goAsync() moves the ops to a different thread
			Log.v(TAG, "in Handler.post() start");
			try {
				ClientConfig.initialize(appContext);
				Intent serviceIntent = new Intent(appContext, PlaybackService.class);
				boolean bound = appContext.bindService(serviceIntent,
						new MediaButtonServiceConnection(appContext, event.getKeyCode(), pendingResult),
						Context.BIND_AUTO_CREATE);
				if (!bound) {
					Log.e(TAG, "Unable to passing media button to PlaybackService - failed to bind to the service");
					pendingResult.finish();
				}
			} catch (Throwable t) {
				Log.e(TAG, "Unable to passing media button to PlaybackService - Unexpected exception", t);
				pendingResult.finish();
			}
			Log.v(TAG, "in Handler.post() end");
		});
	}

	private static class MediaButtonServiceConnection implements ServiceConnection {
		@NonNull
		private final Context context;
		private final int keycode;
		@NonNull
		private final PendingResult pendingResult;

		public MediaButtonServiceConnection(@NonNull Context context, int keycode, @NonNull PendingResult pendingResult) {
			this.context = context;
			this.keycode = keycode;
			this.pendingResult = pendingResult;
		}

		@Override
		public void onBindingDied(ComponentName name) {
			Log.v(TAG, "onBindingDied() called with: name = [" + name + "]");
			pendingResult.finish();
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
			Log.v(TAG, "onServiceConnected() - starts.  thread:" +Thread.currentThread().getName());
			try {
				PlaybackService service = ((PlaybackService.LocalBinder) serviceBinder).getService();
				service.handleKeyCode(keycode);
				// if the keycode is unsupported, the service will provide proper response. No action is needed here
			} finally {
				Log.v(TAG, "onServiceConnected() - about to unbind and pendingResult.finish()");
				try {
					context.unbindService(this);
				} finally {
					pendingResult.finish();
				}
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.v(TAG, "onServiceDisconnected() called with: name = [" + name + "]");
			pendingResult.finish();
		}
	}


	/**
	 *
	 * @return an Intent that can be supplied to the receiver represent a media button event
	 */
	@NonNull
	public static Intent createIntentWithKeyCode(@NonNull Context context, int keycode) {
		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keycode);
		Intent intent = new Intent(context.getApplicationContext(), MediaButtonReceiver.class);
		intent.setAction(MediaButtonReceiver.NOTIFY_BUTTON_RECEIVER);
		intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
		return intent;
	}
}
