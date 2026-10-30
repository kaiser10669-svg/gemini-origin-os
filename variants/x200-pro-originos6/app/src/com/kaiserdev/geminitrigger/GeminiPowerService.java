package com.kaiserdev.geminitrigger;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class GeminiPowerService extends AccessibilityService {

    public static GeminiPowerService instance;
    private long lastTriggerTime = 0L;
    private long lastGeminiOpenTime = 0L;
    private long lastCtsTriggerTime = 0L;
    private int assistantTriggerCount = 0;
    private String lastAssistantEvent = "none";
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Состояние голосового сеанса и автоматической озвучки
    private long voiceSessionStartTime = 0L;
    private boolean micConfirmedActive = false;
    private boolean micAutoClicked = false;
    private boolean playbackAutoClicked = false;
    private boolean playbackClickPending = false;
    private boolean retryLaunchDone = false;
    private String lastPlaybackStatus = "idle";
    private String lastPlaybackDescription = "";
    private String lastGoogleRootId = "none";
    private int playbackClickAttempts = 0;

    private static final String GSA_PKG = "com.google.android.googlequicksearchbox";
    private static final String ID_VOICE_WAVE = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_voice_wave_view";
    private static final String ID_TEXT_QUERY = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_query";
    private static final String ID_MIC_BUTTON = "com.google.android.googlequicksearchbox:id/assistant_robin_mic_button";
    private static final String ID_PLAYBACK_ICON = "com.google.android.googlequicksearchbox:id/assistant_robin_playback_icon";

    private static boolean isVivoCopilotPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        return "com.vivo.ai.copilot".equals(pkg)
                || "com.vivo.agent".equals(pkg)
                || "com.vivo.ai.base.copilot".equals(pkg)
                || "com.vivo.ai.gptagent".equals(pkg)
                || "com.vivo.ai.copilot.shortcut.component".equals(pkg);
    }

    private final Runnable sessionWatcherRunnable = new Runnable() {
        @Override
        public void run() {
            if (voiceSessionStartTime == 0L || playbackAutoClicked) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime() - voiceSessionStartTime;
            if (elapsed > 35000L) {
                lastPlaybackStatus = "timed_out";
                voiceSessionStartTime = 0L;
                return;
            }
            checkVoiceSessionProgress(elapsed);
            if (voiceSessionStartTime != 0L && !playbackAutoClicked) {
                handler.postDelayed(this, 500L);
            }
        }
    };

    public static void notifyGeminiSessionStarted() {
        final GeminiPowerService svc = instance;
        if (svc == null) {
            return;
        }
        svc.voiceSessionStartTime = SystemClock.elapsedRealtime();
        svc.micConfirmedActive = false;
        svc.micAutoClicked = false;
        svc.playbackAutoClicked = false;
        svc.playbackClickPending = false;
        svc.retryLaunchDone = false;
        svc.lastPlaybackStatus = "waiting_for_google_window";
        svc.lastPlaybackDescription = "";
        svc.lastGoogleRootId = "none";
        svc.playbackClickAttempts = 0;

        svc.handler.removeCallbacks(svc.sessionWatcherRunnable);
        svc.handler.postDelayed(svc.sessionWatcherRunnable, 260L);
    }

    private void checkVoiceSessionProgress(long elapsed) {
        if (voiceSessionStartTime == 0L || elapsed > 35000L) {
            return;
        }

        AccessibilityNodeInfo gsaRoot = findGoogleQuickSearchBoxRoot();
        if (gsaRoot == null) {
            lastPlaybackStatus = "waiting_for_google_window";
            // При холодном запуске повторяем вызов один раз, если окно Google ещё не появилось
            if (elapsed >= 500L && elapsed <= 1800L && !retryLaunchDone && lastGeminiOpenTime != 0L) {
                retryLaunchDone = true;
                TriggerActivity.launchGeminiIntentOnly(this);
            } else if (elapsed > 10000L) {
                // Завершаем ожидание отсутствующего окна Google
                voiceSessionStartTime = 0L;
                lastPlaybackStatus = "google_window_missing";
            }
            return;
        }

        try {
            lastGoogleRootId = String.valueOf(gsaRoot.getViewIdResourceName());
            inspectGoogleFloatyRoot(gsaRoot, elapsed);
        } finally {
            try {
                gsaRoot.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    private AccessibilityNodeInfo findGoogleQuickSearchBoxRoot() {
        // Активное окно важнее остальных окон процесса Google
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) {
                CharSequence p = active.getPackageName();
                if (p != null && GSA_PKG.equals(p.toString())) {
                    return active;
                }
                active.recycle();
            }
        } catch (Throwable ignored) {
        }
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root != null) {
                        CharSequence p = root.getPackageName();
                        if (p != null && GSA_PKG.equals(p.toString())) {
                            return root;
                        }
                        root.recycle();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean hasVisibleNodeWithId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(viewId);
            if (list != null) {
                boolean found = false;
                for (AccessibilityNodeInfo n : list) {
                    if (n != null) {
                        if (n.isVisibleToUser()) {
                            found = true;
                        }
                        n.recycle();
                    }
                }
                return found;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean clickNodeWithId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(viewId);
            if (list != null) {
                boolean clicked = false;
                for (AccessibilityNodeInfo n : list) {
                    if (n != null) {
                        if (!clicked && n.isVisibleToUser() && n.isEnabled()) {
                            AccessibilityNodeInfo target = n;
                            while (target != null && !target.isClickable()) {
                                target = target.getParent();
                            }
                            if (target != null) {
                                clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                        }
                        n.recycle();
                    }
                }
                return clicked;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void inspectGoogleFloatyRoot(AccessibilityNodeInfo root, long elapsed) {
        if (playbackAutoClicked || playbackClickPending || elapsed < 1400L || elapsed > 35000L) {
            return;
        }
        if (!hasVisibleNodeWithId(root, ID_PLAYBACK_ICON)) {
            lastPlaybackStatus = "waiting_for_playback_button";
            return;
        }
        if (hasVisibleNodeWithId(root, ID_VOICE_WAVE)) {
            lastPlaybackStatus = "microphone_active";
            return;
        }

        // Даём Google время обновить состояние кнопки после появления ответа
        playbackClickPending = true;
        final long sessionStart = voiceSessionStartTime;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (voiceSessionStartTime != sessionStart || sessionStart == 0L || playbackAutoClicked) {
                        return;
                    }
                    AccessibilityNodeInfo latestRoot = findGoogleQuickSearchBoxRoot();
                    if (latestRoot == null) {
                        lastPlaybackStatus = "google_window_unavailable";
                        return;
                    }
                    try {
                        if (hasVisibleNodeWithId(latestRoot, ID_VOICE_WAVE)) {
                            lastPlaybackStatus = "microphone_active";
                            return;
                        }
                        tryStartPlayback(latestRoot);
                    } finally {
                        latestRoot.recycle();
                    }
                } catch (Throwable error) {
                    lastPlaybackStatus = "playback_error: " + error.getClass().getSimpleName();
                } finally {
                    if (voiceSessionStartTime == sessionStart || voiceSessionStartTime == 0L) {
                        playbackClickPending = false;
                    }
                }
            }
        }, 750L);
    }

    private void tryStartPlayback(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByViewId(ID_PLAYBACK_ICON);
        if (buttons == null || buttons.isEmpty()) {
            lastPlaybackStatus = "waiting_for_playback_button";
            return;
        }
        try {
            for (AccessibilityNodeInfo button : buttons) {
                if (button == null || !button.isVisibleToUser() || !button.isEnabled()) {
                    continue;
                }
                CharSequence description = button.getContentDescription();
                lastPlaybackDescription = description == null ? "" : description.toString();
                String action = lastPlaybackDescription.toLowerCase(Locale.ROOT);

                // Повторное нажатие остановило бы уже начавшуюся озвучку
                if (action.contains("stop") || action.contains("pause")
                        || action.contains("останов") || action.contains("пауз")) {
                    playbackAutoClicked = true;
                    voiceSessionStartTime = 0L;
                    lastPlaybackStatus = "already_playing";
                    return;
                }
                if (!action.contains("listen") && !action.contains("play") && !action.contains("read aloud")
                        && !action.contains("слуш") && !action.contains("озвуч") && !action.contains("читать")) {
                    lastPlaybackStatus = "unknown_playback_action";
                    continue;
                }

                playbackClickAttempts++;
                boolean clicked = button.isClickable()
                        ? button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        : clickNodeWithId(root, ID_PLAYBACK_ICON);
                lastPlaybackStatus = clicked ? "playback_clicked" : "playback_click_failed";
                if (clicked) {
                    playbackAutoClicked = true;
                    voiceSessionStartTime = 0L;
                }
                return;
            }
        } finally {
            for (AccessibilityNodeInfo button : buttons) {
                if (button != null) {
                    button.recycle();
                }
            }
        }
    }

    public static boolean showSystemPowerDialog() {
        GeminiPowerService svc = instance;
        if (svc != null) {
            return svc.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
        return false;
    }

    public static void triggerCircleToSearch(final Context context) {
        // Сначала пробуем нативный Circle to Search через установленный MiCTS
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("com.parallelc.micts", 0);
            Intent micts = new Intent();
            micts.setComponent(new ComponentName("com.parallelc.micts", "com.parallelc.micts.ui.activity.MainActivity"));
            micts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(micts);
            return;
        } catch (Throwable ignored) {
        }

        // Без MiCTS сохраняем снимок экрана и открываем поиск Google Lens
        triggerLensScreenSearch(context);
    }

    public static void triggerLensScreenSearch(final Context context) {
        final GeminiPowerService svc = instance;
        if (svc != null) {
            try {
                Executor mainExec = new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        svc.handler.post(command);
                    }
                };
                svc.takeScreenshot(Display.DEFAULT_DISPLAY, mainExec, new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult screenshot) {
                        try {
                            HardwareBuffer hb = screenshot.getHardwareBuffer();
                            ColorSpace cs = screenshot.getColorSpace();
                            Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                            if (hwBmp != null) {
                                Bitmap swBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                                hwBmp.recycle();
                                hb.close();
                                if (swBmp != null) {
                                    File outFile = ScreenshotProvider.getScreenshotFile(context);
                                    FileOutputStream fos = new FileOutputStream(outFile);
                                    swBmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                                    fos.flush();
                                    fos.close();
                                    swBmp.recycle();

                                    Uri uri = ScreenshotProvider.getScreenshotUri();
                                    Intent lens = new Intent(Intent.ACTION_SEND);
                                    lens.setComponent(new ComponentName(
                                            "com.google.android.googlequicksearchbox",
                                            "com.google.android.apps.search.lens.LensShareEntryPointActivity"
                                    ));
                                    lens.setType("image/jpeg");
                                    lens.putExtra(Intent.EXTRA_STREAM, uri);
                                    lens.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    context.grantUriPermission("com.google.android.googlequicksearchbox", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    context.startActivity(lens);
                                }
                            } else {
                                hb.close();
                            }
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        voiceSessionStartTime = 0L;
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs != null ? pkgCs.toString() : "";
        CharSequence clsCs = event.getClassName();
        String cls = clsCs != null ? clsCs.toString() : "";

        // Следим за окном Google во время голосового сеанса для автоматической озвучки
        if (GSA_PKG.equals(pkg) && voiceSessionStartTime != 0L) {
            long elapsed = SystemClock.elapsedRealtime() - voiceSessionStartTime;
            if (elapsed <= 35000L) {
                checkVoiceSessionProgress(elapsed);
            }
        }

        // Перенаправляем распознавание экрана Vivo VTouch в Google Circle to Search
        if ("com.vivo.vtouch".equals(pkg) && !cls.contains("Setting") && !cls.contains("FAQ")) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastCtsTriggerTime >= 1500L) {
                lastCtsTriggerTime = now;
                performGlobalAction(GLOBAL_ACTION_BACK);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        triggerCircleToSearch(GeminiPowerService.this);
                    }
                }, 100L);
            }
            return;
        }

        boolean matched = isVivoCopilotPackage(pkg);

        if (!matched && event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        CharSequence title = w.getTitle();
                        if (title != null && title.toString().contains("BlueAI")) {
                            matched = true;
                            break;
                        }
                        AccessibilityNodeInfo root = w.getRoot();
                        if (root != null) {
                            CharSequence rootPkg = root.getPackageName();
                            boolean isCopilot = rootPkg != null && isVivoCopilotPackage(rootPkg.toString());
                            root.recycle();
                            if (isCopilot) {
                                matched = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (matched) {
            lastAssistantEvent = pkg + "/" + cls;
            long now = SystemClock.elapsedRealtime();
            if (now - lastTriggerTime < 900L) {
                return;
            }
            lastTriggerTime = now;
            assistantTriggerCount++;

            // Закрываем окно BlueLM Copilot / Jovi
            performGlobalAction(GLOBAL_ACTION_BACK);

            // Повторное удержание в течение пяти секунд открывает штатное меню питания
            if (now - lastGeminiOpenTime < 5000L) {
                lastGeminiOpenTime = 0L;
                voiceSessionStartTime = 0L;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                    }
                }, 120L);
                return;
            }

            lastGeminiOpenTime = now;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TriggerActivity.launchGeminiOverlay(GeminiPowerService.this);
                }
            }, 140L);
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("assistantTriggerCount=" + assistantTriggerCount);
        writer.println("lastAssistantEvent=" + lastAssistantEvent);
        writer.println("voiceSessionActive=" + (voiceSessionStartTime != 0L));
        writer.println("lastPlaybackStatus=" + lastPlaybackStatus);
        writer.println("lastPlaybackDescription=" + lastPlaybackDescription);
        writer.println("lastGoogleRootId=" + lastGoogleRootId);
        writer.println("playbackClickAttempts=" + playbackClickAttempts);
        writer.println("writeSystemSettings=" + android.provider.Settings.System.canWrite(this));
        TriggerActivity.dumpState(writer);
    }
}
