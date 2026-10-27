package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TriggerActivity.ensureSystemSettingsIfPermitted(this);

        float density = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#080B12"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (22 * density);
        root.setPadding(pad, (int) (36 * density), pad, pad);

        // Glowing Emblem Header
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        int logoSize = (int) (136 * density);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.setMargins(0, 0, 0, (int) (14 * density));
        logo.setLayoutParams(logoLp);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText("GEMINI BY KAISER");
        title.setTextSize(23f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#4FACFE"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v3.1 Ultimate • by kaiser & Antigravity AI (Official 4PDA Edition)");
        sub.setTextSize(13f);
        sub.setTextColor(Color.parseColor("#B294FF"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int) (4 * density), 0, (int) (22 * density));
        root.addView(sub);

        // Info Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#121826"));
        cardBg.setCornerRadius(16 * density);
        cardBg.setStroke((int) (1 * density), Color.parseColor("#263554"));
        card.setBackground(cardBg);
        int cardPad = (int) (16 * density);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, (int) (20 * density));
        card.setLayoutParams(cardLp);

        TextView desc = new TextView(this);
        desc.setText(
                "⚡ 1. Кнопка питания (0.5 сек) и голос «Hi, Jovi» -> Google Gemini:\n" +
                "• 1-е зажатие (0.5с) или фраза «Hi, Jovi»: мгновенно вызывает голосовой оверлей Google Gemini с активным микрофоном и автоматической голосовой озвучкой ответа (TTS).\n" +
                "• 2-е зажатие подряд (в течение 5с): открывает штатное меню Выключения / Перезагрузки.\n\n" +
                "🔍 2. Глобальный Circle to Search (Обвести и найти):\n" +
                "• Работает через иконку «Circle to Search» (назначьте на Двойной тап задней панели / Боковую панель), плитку в шторке или при касании двумя пальцами (VTouch).\n" +
                "• Включает 2 режима: нативный Global Circle to Search (если активен MiCTS) + автономный Google Lens Поиск по экрану (работает всегда без Shizuku!).\n\n" +
                "🔒 3. Экран блокировки (OriginOS Lock Screen):\n" +
                "• Включите в OriginOS для Google и Gemini by kaiser разрешения «Показ на экране блокировки», «Окна из фона» и «Автозапуск» (кнопки ниже)."
        );
        desc.setTextSize(14f);
        desc.setTextColor(Color.parseColor("#E2E8F0"));
        desc.setLineSpacing(0f, 1.25f);
        card.addView(desc);
        root.addView(card);

        Button btnGoogleLockScreen = createButton("🔒 OriginOS: Google на экране блокировки", "#1E293B");
        btnGoogleLockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOriginPermissions("com.google.android.googlequicksearchbox");
            }
        });
        root.addView(btnGoogleLockScreen);

        Button btnAppLockScreen = createButton("🔒 OriginOS: Gemini by kaiser на экране блокировки", "#1E293B");
        btnAppLockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOriginPermissions(getPackageName());
            }
        });
        root.addView(btnAppLockScreen);

        Button btnTestGemini = createButton("✨ Запустить голосовой оверлей Gemini", "#1D6FF2");
        btnTestGemini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TriggerActivity.launchGeminiOverlay(InfoActivity.this);
            }
        });
        root.addView(btnTestGemini);

        Button btnVoiceWakeup = createButton("🗣️ Голосовой вызов «Hi, Jovi» -> Gemini", "#0284C7");
        btnVoiceWakeup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.vivo.voicewakeup", "com.vivo.voicewakeup.activities.WakeupSettingsActivity"));
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(InfoActivity.this, "Модуль голосового пробуждения не найден", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnVoiceWakeup);

        Button btnTestCts = createButton("🔍 Запустить Circle to Search (Обвести и найти)", "#6B38FB");
        btnTestCts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(0, 0);
                startActivity(new Intent(InfoActivity.this, ScreenSearchActivity.class));
            }
        });
        root.addView(btnTestCts);

        Button btnTestLens = createButton("📷 Google Объектив (Автономный поиск по экрану)", "#0F766E");
        btnTestLens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (GeminiPowerService.instance == null) {
                    Toast.makeText(InfoActivity.this, "Сначала включите службу Gemini by kaiser в Спец. возможностях", Toast.LENGTH_LONG).show();
                    return;
                }
                GeminiPowerService.triggerLensScreenSearch(InfoActivity.this);
            }
        });
        root.addView(btnTestLens);

        Button btnShutdown = createButton("🔴 Меню выключения / перезагрузки", "#C5221F");
        btnShutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!GeminiPowerService.showSystemPowerDialog()) {
                    Toast.makeText(InfoActivity.this, "Сначала включите службу Gemini by kaiser в Спец. возможностях", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(btnShutdown);

                Button btnWallet = createButton("💳 Двойное нажатие питания -> Circle to Search", "#6B38FB");
        btnWallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.vivo.wallet", "com.vivo.pay.swing.activity.ShortCutSettingActivity"));
                    startActivity(i);
                } catch (Throwable t) {
                    try {
                        Intent i2 = new Intent();
                        i2.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerMenuSettingsActivity"));
                        startActivity(i2);
                    } catch (Throwable t2) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                }
            }
        });
        root.addView(btnWallet);

        Button btnBackTap = createButton("👆 Настроить Двойной тап по спинке (Back Tap)", "#1E293B");
        btnBackTap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.vivo.SmartKey", "com.vivo.SmartKey.backtap.ui.MainActivity"));
                    startActivity(i);
                } catch (Throwable t) {
                    try {
                        Intent i2 = new Intent();
                        i2.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$ShortcutsAccessibilityActivity"));
                        startActivity(i2);
                    } catch (Throwable t2) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                }
            }
        });
        root.addView(btnBackTap);

        Button btnPower = createButton("⚙️ Настройки кнопки питания (0.5 сек)", "#1E293B");
        btnPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerMenuSettingsActivity"));
                    startActivity(i);
                } catch (Throwable t) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });
        root.addView(btnPower);

        Button btnA11y = createButton("🛡️ Открыть Специальные возможности", "#1E293B");
        btnA11y.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Throwable ignored) {
                }
            }
        });
        root.addView(btnA11y);

        Button btnUnlockRestricted = createButton("🔓 Снять блокировку тумблера (О приложении -> ⋮)", "#D97706");
        btnUnlockRestricted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Toast.makeText(
                            InfoActivity.this,
                            "Нажмите три точки (⋮) в правом верхнем углу -> «Разрешить ограниченные настройки»",
                            Toast.LENGTH_LONG
                    ).show();
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable ignored) {
                }
            }
        });
        root.addView(btnUnlockRestricted);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
    }

    private void openOriginPermissions(String packageName) {
        try {
            Intent intent = new Intent("permission.intent.action.softPermissionDetail");
            intent.setPackage("com.vivo.permissionmanager");
            intent.putExtra("packagename", packageName);
            intent.putExtra("packageName", packageName);
            startActivity(intent);
            String message = getPackageName().equals(packageName)
                    ? "Включите Показ на экране блокировки, Окна из фона и Автозапуск"
                    : "Включите «Показ на экране блокировки» (Display on lock screen)";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }

    private Button createButton(String text, String hexColor) {
        float density = getResources().getDisplayMetrics().density;
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(12 * density);
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (6 * density);
        lp.setMargins(0, margin, 0, margin);
        b.setLayoutParams(lp);

        int padV = (int) (13 * density);
        int padH = (int) (16 * density);
        b.setPadding(padH, padV, padH, padV);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
