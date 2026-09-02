package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class GcmPushListenerService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
