package de.danoeh.antennapod.core.receiver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import de.danoeh.antennapod.core.util.LogToFile;

public class NetTestInternetCallService extends Service {

    private static final String TAG = "NetTestInternetCallSvc";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    
        new Handler().postDelayed(() -> new HasActiveInternetConnectionTask(this).execute(), 1000);
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected class HasActiveInternetConnectionTask extends AsyncTask<Void,Void,Void> {
        private Context context;

        public HasActiveInternetConnectionTask(Context context) {
            this.context = context;
        }

        protected Void doInBackground(Void... params) {
            if (hasActiveInternetConnection(context)) {
                LogToFile.d(context, TAG, "  device is connected to internet");
            } else {
                LogToFile.d(context, TAG, "  device is not connected to internet");
            }
            NetTestAbstractReceiver.logNetworkAvailable(context, TAG, "After hasActiveInternetConnection");
            return null;
        }

    }

    private static boolean hasActiveInternetConnection(Context context) {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("http://www.google.com/").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(4000);
            urlc.connect();
            urlc.disconnect();
            return true;
        } catch (IOException ioe) {
            LogToFile.d(context, TAG, "hasActiveInternetConnection() fails: " + ioe.getMessage());
            return false;
        }
    }

}
