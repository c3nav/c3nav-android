package de.c3nav.droid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class LiveLocationService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Do something in the background
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }
}
