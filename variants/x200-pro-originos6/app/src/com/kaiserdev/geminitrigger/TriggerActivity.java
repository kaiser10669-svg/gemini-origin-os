package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import java.io.PrintWriter;

public class TriggerActivity extends Activity {

    private static final String TAG = "GeminiTrigger";
    private static String lastLaunchState = "not_requested";
    private static boolean lastLaunchLocked = false;
    private boolean launched = false;

    public static void ensureSystemSettingsIfPermitted(Context context) {
        try {
            ContentResolver cr = context.getContentResolver();
            String gsaService = "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService";
            String gsaRec = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService";
            Settings.Secure.putString(cr, "assistant", gsaService);
            Settings.Secure.putString(cr, "voice_interaction_service", gsaService);
            Settings.Secure.putString(cr, "voice_recognition_service", gsaRec);
            Settings.Secure.putInt(cr, "vivo.assistant.jovi.switch", 1);
            Settings.Secure.putInt(cr, "copilot_voicewakeup_feedback", 0);
            // Jovi передаёт вызов Gemini без параллельной озвучки ответа
            Settings.Secure.putInt(cr, "setting_voice_mode", 2);
            Settings.Global.putInt(cr, "power_button_long_press", 5);
            Settings.Global.putInt(cr, "system_navigation_wake_up_switch", 1);

            String myService = "com.kaiserdev.geminitrigger/com.kaiserdev.geminitrigger.GeminiPowerService";
            String currentA11y = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (currentA11y != null) {
                currentA11y = currentA11y.replace("\r", "").replace("\n", "").trim();
                if (currentA11y.startsWith("null:")) {
                    currentA11y = currentA11y.substring(5);
                } else if (currentA11y.equals("null")) {
                    currentA11y = "";
                }
            }
            if (currentA11y == null || currentA11y.isEmpty()) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, myService);
            } else if (!currentA11y.contains(myService)) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, currentA11y + ":" + myService);
            } else {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, currentA11y);
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Throwable error) {
            Log.w(TAG, "Не удалось применить настройки ассистента", error);
        }

        // Настройки Vivo находятся в System и требуют отдельного разрешения
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.getContentResolver(), "vivo_jovi_power_wakeup_switch", 1);
                Settings.System.putInt(context.getContentResolver(), "voicewakeup_switch", 1);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Не удалось включить вызов Jovi", error);
        }
    }

    public static void launchGeminiOverlay(Context context) {
        // OriginOS ограничивает открытие Google из фоновой службы
        if (!(context instanceof Activity)) {
            KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            lastLaunchLocked = keyguard != null && keyguard.isKeyguardLocked();
            try {
                Intent bridge = new Intent(context, TriggerActivity.class);
                bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(bridge);
                lastLaunchState = "foreground_bridge_started";
            } catch (Throwable error) {
                lastLaunchState = "foreground_bridge_failed: " + error.getClass().getSimpleName();
                Log.w(TAG, "Не удалось открыть окно вызова Gemini", error);
            }
            return;
        }

        ensureSystemSettingsIfPermitted(context);
        GeminiPowerService.notifyGeminiSessionStarted();
        launchGeminiIntentOnly(context);
    }

    public static void launchGeminiIntentOnly(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        lastLaunchLocked = keyguard != null && keyguard.isKeyguardLocked();
        try {
            Intent intent = new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE");
            intent.setComponent(new ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.handsfree.HandsFreeActivity"
            ));
            intent.putExtra("android.speech.extra.SECURE", lastLaunchLocked);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            lastLaunchState = "hands_free_started";
            return;
        } catch (Throwable error) {
            lastLaunchState = "hands_free_failed: " + error.getClass().getSimpleName();
            Log.w(TAG, "Не удалось вызвать голосовой оверлей", error);
        }

        // Полноэкранный Gemini не заменяет голосовой оверлей поверх блокировки
        if (lastLaunchLocked) {
            return;
        }

        try {
            Intent fallback = new Intent(Intent.ACTION_MAIN);
            fallback.setComponent(new ComponentName(
                    "com.google.android.apps.bard",
                    "com.google.android.apps.bard.shellapp.BardEntryPointActivity"
            ));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(fallback);
            lastLaunchState = "bard_fallback_started";
        } catch (Throwable error) {
            lastLaunchState = "bard_fallback_failed: " + error.getClass().getSimpleName();
            Log.w(TAG, "Не удалось открыть Gemini", error);
        }
    }

    static void dumpState(PrintWriter writer) {
        writer.println("lastLaunchState=" + lastLaunchState);
        writer.println("lastLaunchLocked=" + lastLaunchLocked);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (launched) {
            return;
        }
        launched = true;
        launchGeminiOverlay(this);
        finish();
        overridePendingTransition(0, 0);
    }
}
