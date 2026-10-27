package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent intent = getIntent();
            String action = intent != null ? intent.getAction() : null;
            String type = intent != null ? intent.getType() : null;

            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) {
                        Intent lensIntent = new Intent(Intent.ACTION_VIEW);
                        lensIntent.setDataAndType(imageUri, "image/*");
                        lensIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        lensIntent.setPackage("com.google.android.googlequicksearchbox");
                        try {
                            GeminiPowerService.performHaptic(this);
                            startActivity(lensIntent);
                        } catch (Throwable t) {
                            Intent chooser = Intent.createChooser(intent, "Анализ изображения в Google");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chooser);
                        }
                    }
                } else if (type.startsWith("text/")) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null && !sharedText.isEmpty()) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("Gemini Query", sharedText));
                        }
                        Toast.makeText(this, "Текст отправлен в Gemini", Toast.LENGTH_SHORT).show();
                        GeminiPowerService.performHaptic(this);
                        TriggerActivity.launchGeminiOverlay(this);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
