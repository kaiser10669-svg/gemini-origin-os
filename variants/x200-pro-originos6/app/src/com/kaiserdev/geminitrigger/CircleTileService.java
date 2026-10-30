package com.kaiserdev.geminitrigger;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

public class CircleTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, ScreenSearchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(
                    this,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
