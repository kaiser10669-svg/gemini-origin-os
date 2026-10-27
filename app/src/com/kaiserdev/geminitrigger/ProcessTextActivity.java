package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ProcessTextActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (text != null && text.length() > 0) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Gemini Query", text));
                }
                Toast.makeText(this, "Текст скопирован в буфер для Gemini", Toast.LENGTH_SHORT).show();
                GeminiPowerService.performHaptic(this);
                TriggerActivity.launchGeminiOverlay(this);
            }
        } catch (Throwable ignored) {
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
