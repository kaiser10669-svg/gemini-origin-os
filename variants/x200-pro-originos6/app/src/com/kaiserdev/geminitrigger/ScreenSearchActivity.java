package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class ScreenSearchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
        finish();
        overridePendingTransition(0, 0);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                GeminiPowerService.triggerCircleToSearch(getApplicationContext());
            }
        }, 80L);
    }
}
