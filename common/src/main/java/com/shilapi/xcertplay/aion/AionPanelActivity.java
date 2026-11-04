package com.shilapi.xcertplay.aion;

import android.app.Activity;
import android.content.Context;
import com.shilapi.xcertplay.AppLocale;

/** Use the same Chinese locale as the host, including panels opened from a notification. */
public abstract class AionPanelActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.INSTANCE.wrap(base));
    }
}
