package com.kaiserdev.geminitrigger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

public class InfoActivity extends Activity {

    private LinearLayout root;
    private float density;
    private TextView tvA11yVal;
    private TextView tvMictsVal;
    private TextView tvShizukuVal;
    private TextView tvShizukuPermVal;
    private LinearLayout shizukuActionsContainer;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatusIndicators();
                }
            });
        }
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatusIndicators();
                }
            });
        }
    };

    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, final int grantResult) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatusIndicators();
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(InfoActivity.this, "🎉 Доступ Shizuku успешно получен!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(InfoActivity.this, "В доступе Shizuku отказано", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TriggerActivity.ensureSystemSettingsIfPermitted(this);

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);
        } catch (Throwable ignored) {}

        density = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#080B12"));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (20 * density);
        root.setPadding(pad, (int) (28 * density), pad, pad);

        // Header Logo
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        int logoSize = (int) (110 * density);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.setMargins(0, 0, 0, (int) (12 * density));
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
        sub.setText("v3.2.6 Ultimate • by kaiser & Antigravity AI (Official 4PDA Edition)");
        sub.setTextSize(12.5f);
        sub.setTextColor(Color.parseColor("#B294FF"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int) (4 * density), 0, (int) (16 * density));
        root.addView(sub);

        // 1. Dashboard / Live Status Card
        createStatusDashboard();

        // 2. Interactive Behavior Settings (Switches)
        createSettingsCard();

        // 3. Quick Settings Tiles (Android 13+ One-Tap Adder)
        createTilesCard();

        // 4. Testing & OriginOS Shortcuts
        createActionsCard();

        // 5. OriginOS System Gestures Tip Card
        createOriginGesturesTipCard();

        // 6. OriginOS Permissions & Lock Screen
        createPermissionsCard();

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
        updateStatusIndicators();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
        } catch (Throwable ignored) {}
    }

    private void updateStatusIndicators() {
        if (tvA11yVal == null || tvMictsVal == null || tvShizukuVal == null || tvShizukuPermVal == null) {
            return;
        }
        boolean a11yActive = GeminiPowerService.instance != null;
        boolean mictsInstalled = GeminiPowerService.isMictsInstalled(this);
        boolean shizukuInstalled = ShizukuHelper.isShizukuInstalled(this);
        boolean shizukuRunning = GeminiPowerService.isShizukuRunning(this);
        boolean shizukuPerm = ShizukuHelper.hasPermission();

        tvA11yVal.setText(a11yActive ? "АКТИВНА ✅" : "ВЫКЛЮЧЕНА ❌");
        tvA11yVal.setTextColor(Color.parseColor(a11yActive ? "#10B981" : "#EF4444"));

        tvMictsVal.setText(mictsInstalled ? "УСТАНОВЛЕН ✅" : "НЕ НАЙДЕН ℹ️");
        tvMictsVal.setTextColor(Color.parseColor(mictsInstalled ? "#10B981" : "#94A3B8"));

        if (!shizukuInstalled) {
            tvShizukuVal.setText("НЕ УСТАНОВЛЕН ❌");
            tvShizukuVal.setTextColor(Color.parseColor("#EF4444"));
            tvShizukuPermVal.setText("НЕДОСТУПНО ❌");
            tvShizukuPermVal.setTextColor(Color.parseColor("#94A3B8"));
        } else if (shizukuRunning) {
            tvShizukuVal.setText("АКТИВЕН ✅");
            tvShizukuVal.setTextColor(Color.parseColor("#10B981"));
            if (shizukuPerm) {
                tvShizukuPermVal.setText("ВЫДАНО ✅");
                tvShizukuPermVal.setTextColor(Color.parseColor("#10B981"));
            } else {
                tvShizukuPermVal.setText("НЕ ВЫДАНО ⚠️");
                tvShizukuPermVal.setTextColor(Color.parseColor("#F59E0B"));
            }
        } else {
            tvShizukuVal.setText("НЕ ЗАПУЩЕН ⚠️");
            tvShizukuVal.setTextColor(Color.parseColor("#F59E0B"));
            tvShizukuPermVal.setText("ОЖИДАНИЕ СЕРВЕРА ⏳");
            tvShizukuPermVal.setTextColor(Color.parseColor("#94A3B8"));
        }

        updateShizukuControls(shizukuInstalled, shizukuRunning, shizukuPerm);
    }

    private void updateShizukuControls(boolean installed, boolean running, boolean permitted) {
        if (shizukuActionsContainer == null) return;
        shizukuActionsContainer.removeAllViews();

        if (!installed) {
            Button btnInstall = createButton("📥 Установить приложение Shizuku", "#334155");
            btnInstall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app")));
                    } catch (Throwable ignored) {}
                }
            });
            shizukuActionsContainer.addView(btnInstall);
            return;
        }

        if (running && permitted) {
            Button btnAuto = createButton("⚡ Авто-настройка в 1 клик через Shizuku", "#059669");
            btnAuto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleShizukuAutoConfig();
                }
            });
            shizukuActionsContainer.addView(btnAuto);
        } else if (running) {
            Button btnReq = createButton("🔑 Запросить доступ к Shizuku", "#D97706");
            btnReq.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ShizukuHelper.requestPermission(ShizukuHelper.SHIZUKU_REQ_CODE);
                }
            });
            shizukuActionsContainer.addView(btnReq);
        }

        Button btnOpenShizuku = createButton("🚀 Открыть приложение Shizuku", "#1E293B");
        btnOpenShizuku.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i != null) {
                        startActivity(i);
                    }
                } catch (Throwable ignored) {}
            }
        });
        shizukuActionsContainer.addView(btnOpenShizuku);
    }

    private void handleShizukuAutoConfig() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Авто-настройка Shizuku");
        pd.setMessage("Применение системных разрешений и служб...");
        pd.setCancelable(false);
        pd.show();

        ShizukuHelper.runAutoConfigure(this, new ShizukuHelper.AutoConfigCallback() {
            @Override
            public void onProgress(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pd.isShowing()) {
                            pd.setMessage(message);
                        }
                    }
                });
            }

            @Override
            public void onComplete(final boolean success, final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pd.isShowing()) {
                            pd.dismiss();
                        }
                        updateStatusIndicators();

                        AlertDialog.Builder builder = new AlertDialog.Builder(InfoActivity.this);
                        if (success) {
                            builder.setTitle("🎉 Настройка завершена!");
                            builder.setMessage(
                                    "Все системные параметры успешно применены через Shizuku:\n\n" +
                                    "✅ WRITE_SECURE_SETTINGS выдано\n" +
                                    "✅ Окна поверх других приложений включены\n" +
                                    "✅ Энергосбережение отключено (Doze Whitelist)\n" +
                                    "✅ Служба Gemini Power активирована\n" +
                                    "✅ Удержание питания перенаправлено на Jovi -> Gemini\n\n" +
                                    "Теперь всё работает полностью автономно без ПК!"
                            );
                            builder.setPositiveButton("Отлично", null);
                        } else {
                            builder.setTitle("Ошибка авто-настройки");
                            builder.setMessage(message);
                            builder.setPositiveButton("Понятно", null);
                        }
                        builder.show();
                    }
                });
            }
        });
    }

    private void createStatusDashboard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("📊 Статус компонентов системы");
        card.addView(cardTitle);

        tvA11yVal = new TextView(this);
        card.addView(createStatusRow("Служба Gemini Power:", tvA11yVal));

        tvMictsVal = new TextView(this);
        card.addView(createStatusRow("Circle to Search (MiCTS):", tvMictsVal));

        tvShizukuVal = new TextView(this);
        card.addView(createStatusRow("Сервер Shizuku:", tvShizukuVal));

        tvShizukuPermVal = new TextView(this);
        card.addView(createStatusRow("Разрешение Shizuku:", tvShizukuPermVal));

        shizukuActionsContainer = new LinearLayout(this);
        shizukuActionsContainer.setOrientation(LinearLayout.VERTICAL);
        shizukuActionsContainer.setPadding(0, (int) (8 * density), 0, 0);
        card.addView(shizukuActionsContainer);

        updateStatusIndicators();

        root.addView(card);
    }

    private void createSettingsCard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("⚙️ Настройки поведения (v3.2)");
        card.addView(cardTitle);

        card.addView(createSwitchRow(
                "📳 Тактильный виброотклик",
                "Фирменный виброщелчок при вызове Gemini и Circle to Search",
                GeminiPowerService.PREF_HAPTIC,
                true
        ));

        card.addView(createSwitchRow(
                "💳 Двойной клик питания -> Circle to Search",
                "Мгновенный перехват китайского кошелька Vivo и запуск MiCTS",
                GeminiPowerService.PREF_WALLET_CTS,
                true
        ));

        card.addView(createSwitchRow(
                "🔊 Авто-озвучка ответов Gemini (TTS)",
                "Автоматическое воспроизведение ответа Gemini вслух через динамик",
                GeminiPowerService.PREF_AUTO_TTS,
                true
        ));

        card.addView(createSwitchRow(
                "🔴 Двойное удержание питания -> Выключение",
                "Повторное удержание питания в течение 5 сек открывает меню выключения / перезагрузки",
                GeminiPowerService.PREF_DOUBLE_POWER_MENU,
                true
        ));

        card.addView(createSwitchRow(
                "🔍 Перехват Vivo VTouch на Circle to Search",
                "Заменять китайское распознавание экрана VTouch на глобальный CTS",
                GeminiPowerService.PREF_VTOUCH_CTS,
                true
        ));

        Button btnPowerMenu = createButton("📱 Открыть системное меню выключения", "#475569");
        btnPowerMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeminiPowerService.showSystemPowerDialog();
            }
        });
        card.addView(btnPowerMenu);

        root.addView(card);
    }

    private void createTilesCard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("📲 Плитки в шторку быстрых настроек");
        card.addView(cardTitle);

        TextView hint = new TextView(this);
        hint.setText("Добавьте плитки Gemini и Circle to Search прямо в шторку уведомлений OriginOS для мгновенного доступа:");
        hint.setTextSize(12.5f);
        hint.setTextColor(Color.parseColor("#94A3B8"));
        hint.setPadding(0, 0, 0, (int) (10 * density));
        card.addView(hint);

        Button btnAddGeminiTile = createButton("➕ Добавить «Gemini Оверлей» в шторку", "#1D6FF2");
        btnAddGeminiTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAddTile(GeminiTileService.class, "Gemini Оверлей");
            }
        });
        card.addView(btnAddGeminiTile);

        Button btnAddCtsTile = createButton("➕ Добавить «Circle to Search» в шторку", "#6B38FB");
        btnAddCtsTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAddTile(CircleTileService.class, "Circle to Search");
            }
        });
        card.addView(btnAddCtsTile);

        root.addView(card);
    }

    private void createActionsCard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("⚡ Тестирование и системные жесты");
        card.addView(cardTitle);

        Button btnTestGemini = createButton("✨ Запустить голосовой оверлей Gemini", "#1D6FF2");
        btnTestGemini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TriggerActivity.launchGeminiOverlay(InfoActivity.this);
            }
        });
        card.addView(btnTestGemini);

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
        card.addView(btnVoiceWakeup);

        Button btnTestCts = createButton("🔍 Запустить Circle to Search (MiCTS)", "#6B38FB");
        btnTestCts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(0, 0);
                startActivity(new Intent(InfoActivity.this, ScreenSearchActivity.class));
            }
        });
        card.addView(btnTestCts);

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
        card.addView(btnTestLens);

        Button btnShutdown = createButton("🔴 Меню выключения / перезагрузки", "#C5221F");
        btnShutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!GeminiPowerService.showSystemPowerDialog()) {
                    Toast.makeText(InfoActivity.this, "Сначала включите службу Gemini by kaiser в Спец. возможностях", Toast.LENGTH_LONG).show();
                }
            }
        });
        card.addView(btnShutdown);

        Button btnWallet = createButton("💳 Настройки Двойного нажатия питания Vivo", "#3B82F6");
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
        card.addView(btnWallet);

        root.addView(card);
    }

    private void createOriginGesturesTipCard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("💡 Подсказка: Системные жесты OriginOS 7");
        card.addView(cardTitle);

        TextView tipText = new TextView(this);
        tipText.setText(
                "В OriginOS прямые переходы в системные настройки жестов защищены подписью Vivo. Настройте их вручную один раз:\n\n" +
                "1️⃣ Кнопка питания (удержание 0.5 сек ➔ Gemini):\n" +
                "Настройки ➔ Ярлыки и спец. возможности ➔ Кнопка питания ➔ Выберите «Jovi» (наше приложение мгновенно перехватит его на Gemini).\n\n" +
                "2️⃣ Двойной тап по спинке (Back Tap):\n" +
                "Настройки ➔ Ярлыки и спец. возможности ➔ Быстрое действие / Тап по крышке ➔ Выберите «Gemini by kaiser»."
        );
        tipText.setTextSize(13f);
        tipText.setTextColor(Color.parseColor("#CBD5E1"));
        tipText.setLineSpacing(0, 1.25f);
        tipText.setPadding(0, 0, 0, (int) (12 * density));
        card.addView(tipText);

        Button btnSettings = createButton("⚙️ Открыть системные Настройки", "#334155");
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Throwable ignored) {}
            }
        });
        card.addView(btnSettings);

        root.addView(card);
    }

    private void createPermissionsCard() {
        LinearLayout card = createCardContainer();

        TextView cardTitle = createSectionHeader("🛡️ Разрешения OriginOS и Экран блокировки");
        card.addView(cardTitle);

        Button btnGoogleLockScreen = createButton("🔒 OriginOS: Google на экране блокировки", "#1E293B");
        btnGoogleLockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOriginPermissions("com.google.android.googlequicksearchbox");
            }
        });
        card.addView(btnGoogleLockScreen);

        Button btnAppLockScreen = createButton("🔒 OriginOS: Gemini by kaiser на экране блокировки", "#1E293B");
        btnAppLockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOriginPermissions(getPackageName());
            }
        });
        card.addView(btnAppLockScreen);

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
        card.addView(btnA11y);

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
        card.addView(btnUnlockRestricted);

        root.addView(card);
    }

    private void requestAddTile(Class<?> tileServiceClass, final String tileLabel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                StatusBarManager sbm = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
                if (sbm != null) {
                    ComponentName comp = new ComponentName(this, tileServiceClass);
                    sbm.requestAddTileService(
                            comp,
                            tileLabel,
                            Icon.createWithResource(this, R.drawable.ic_launcher),
                            getMainExecutor(),
                            new Consumer<Integer>() {
                                @Override
                                public void accept(Integer result) {
                                    if (result != null && result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                        Toast.makeText(InfoActivity.this, "Плитка «" + tileLabel + "» добавлена в шторку!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                    );
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, "Откройте шторку -> Редактировать (карандаш) -> перетащите плитку «" + tileLabel + "»", Toast.LENGTH_LONG).show();
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

    private LinearLayout createCardContainer() {
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
        cardLp.setMargins(0, 0, 0, (int) (16 * density));
        card.setLayoutParams(cardLp);
        return card;
    }

    private TextView createSectionHeader(String titleText) {
        TextView tv = new TextView(this);
        tv.setText(titleText);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#38BDF8"));
        tv.setPadding(0, 0, 0, (int) (12 * density));
        return tv;
    }

    private LinearLayout createStatusRow(String label, TextView tvVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(13.5f);
        tvLabel.setTextColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvLabel.setLayoutParams(lp1);
        row.addView(tvLabel);

        tvVal.setTextSize(13.5f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvVal);

        return row;
    }

    private LinearLayout createSwitchRow(String title, String subtitle, final String prefKey, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLp.setMargins(0, 0, (int) (8 * density), 0);
        textCol.setLayoutParams(textLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#F1F5F9"));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11.5f);
        tvSub.setTextColor(Color.parseColor("#94A3B8"));
        textCol.addView(tvSub);

        row.addView(textCol);

        Switch sw = new Switch(this);
        boolean currentVal = GeminiPowerService.isPrefEnabled(this, prefKey, defaultValue);
        sw.setChecked(currentVal);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GeminiPowerService.setPrefEnabled(InfoActivity.this, prefKey, isChecked);
                if (isChecked) {
                    GeminiPowerService.performHaptic(InfoActivity.this);
                }
            }
        });
        row.addView(sw);

        return row;
    }

    private Button createButton(String text, String hexColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14f);
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
        int margin = (int) (5 * density);
        lp.setMargins(0, margin, 0, margin);
        b.setLayoutParams(lp);

        int padV = (int) (12 * density);
        int padH = (int) (14 * density);
        b.setPadding(padH, padV, padH, padV);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
