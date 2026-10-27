package com.kaiserdev.geminitrigger;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
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

    // Settings Keys
    public static final String PREFS_NAME = "gemini_trigger_prefs";
    public static final String PREF_HAPTIC = "pref_haptic";
    public static final String PREF_WALLET_CTS = "pref_wallet_cts";
    public static final String PREF_AUTO_TTS = "pref_auto_tts";
    public static final String PREF_DOUBLE_POWER_MENU = "pref_double_power_menu";
    public static final String PREF_VTOUCH_CTS = "pref_vtouch_cts";

    // Voice session tracking (instant mic trigger + auto-TTS speech output)
    private long voiceSessionStartTime = 0L;
    private boolean micConfirmedActive = false;
    private boolean micAutoClicked = false;
    private boolean playbackAutoClicked = false;
    private boolean retryLaunchDone = false;
    private String lastPlaybackStatus = "idle";
    private String lastPlaybackDescription = "";

    private static final String GSA_PKG = "com.google.android.googlequicksearchbox";
    private static final String ID_VOICE_WAVE = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_voice_wave_view";
    private static final String ID_TEXT_QUERY = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_query";
    private static final String ID_MIC_BUTTON = "com.google.android.googlequicksearchbox:id/assistant_robin_mic_button";
    private static final String ID_MIC_CONTAINER = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_mic_button";
    private static final String ID_PLAYBACK_ICON = "com.google.android.googlequicksearchbox:id/assistant_robin_playback_icon";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isPrefEnabled(Context context, String key, boolean def) {
        try {
            return getPrefs(context).getBoolean(key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static void setPrefEnabled(Context context, String key, boolean val) {
        try {
            getPrefs(context).edit().putBoolean(key, val).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void performHaptic(Context context) {
        if (!isPrefEnabled(context, PREF_HAPTIC, true)) {
            return;
        }
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                } else {
                    v.vibrate(25);
                }
            }
        } catch (Throwable ignored) {
        }
    }

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
                // High frequency polling (50ms) until mic is active, then relaxed (350ms) for TTS
                long nextDelay = !micConfirmedActive ? 50L : 350L;
                handler.postDelayed(this, nextDelay);
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
        svc.retryLaunchDone = false;
        svc.lastPlaybackStatus = "waiting_for_google_window";
        svc.lastPlaybackDescription = "";

        svc.handler.removeCallbacks(svc.sessionWatcherRunnable);
        // Start watching immediately at 40ms to catch FloatyActivity on first frame
        svc.handler.postDelayed(svc.sessionWatcherRunnable, 40L);
    }

    private void checkVoiceSessionProgress(long elapsed) {
        AccessibilityNodeInfo root = findGoogleQuickSearchBoxRoot();
        if (root == null) {
            return;
        }
        try {
            if (!micConfirmedActive) {
                inspectGoogleFloatyForMic(root, elapsed);
            }
            inspectGoogleFloatyRoot(root, elapsed);
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findGoogleQuickSearchBoxRoot() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root != null) {
                        CharSequence pkg = root.getPackageName();
                        if (pkg != null && GSA_PKG.equals(pkg.toString())) {
                            return root;
                        }
                        root.recycle();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) {
                CharSequence pkg = active.getPackageName();
                if (pkg != null && GSA_PKG.equals(pkg.toString())) {
                    return active;
                }
                active.recycle();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void inspectGoogleFloatyForMic(AccessibilityNodeInfo root, long elapsed) {
        // If the animated voice wave is already visible, Gemini is listening!
        if (hasVisibleNodeWithId(root, ID_VOICE_WAVE)) {
            micConfirmedActive = true;
            lastPlaybackStatus = "mic_listening";
            return;
        }

        // INSTANT ACTIVATION: If Google Floaty opened in text mode or mic is idle,
        // click the mic button IMMEDIATELY on the very first frame without any delay!
        if (!micAutoClicked && (hasVisibleNodeWithId(root, ID_MIC_BUTTON) || hasVisibleNodeWithId(root, ID_MIC_CONTAINER) || hasVisibleNodeWithId(root, ID_TEXT_QUERY))) {
            if (clickMicButton(root)) {
                micAutoClicked = true;
                micConfirmedActive = true;
                lastPlaybackStatus = "mic_auto_clicked_instant";
                return;
            }
        }

        if (!retryLaunchDone && elapsed >= 1200L && !micConfirmedActive) {
            retryLaunchDone = true;
            TriggerActivity.launchGeminiIntentOnly(this, false);
        }
    }

    private boolean clickMicButton(AccessibilityNodeInfo root) {
        if (clickNodeWithId(root, ID_MIC_BUTTON)) {
            return true;
        }
        if (clickNodeWithId(root, ID_MIC_CONTAINER)) {
            return true;
        }
        try {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText("Микрофон");
            if (list != null) {
                for (AccessibilityNodeInfo n : list) {
                    if (n != null) {
                        boolean ok = performClickOnNodeOrParent(n);
                        n.recycle();
                        if (ok) return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean performClickOnNodeOrParent(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo curr = node;
        while (curr != null) {
            if (curr.isClickable() && curr.isEnabled()) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            curr = curr.getParent();
        }
        return false;
    }

    private boolean hasVisibleNodeWithId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null) {
                        boolean ok = n.isVisibleToUser();
                        n.recycle();
                        if (ok) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean clickNodeWithId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes != null) {
                boolean clicked = false;
                for (AccessibilityNodeInfo n : nodes) {
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
        if (!isPrefEnabled(this, PREF_AUTO_TTS, true)) {
            return;
        }

        // Once the user has spoken and Gemini renders the response card (ID_PLAYBACK_ICON becomes visible),
        // automatically click the speaker/playback icon if TTS is not already playing out loud!
        if (!playbackAutoClicked && elapsed >= 1200L && elapsed <= 35000L) {
            if (hasVisibleNodeWithId(root, ID_PLAYBACK_ICON)) {
                if (hasVisibleNodeWithId(root, ID_VOICE_WAVE)) {
                    lastPlaybackStatus = "mic_active";
                    return;
                }
                playbackAutoClicked = true;
                voiceSessionStartTime = 0L;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AccessibilityNodeInfo latestRoot = findGoogleQuickSearchBoxRoot();
                            if (latestRoot != null) {
                                try {
                                    if (!hasVisibleNodeWithId(latestRoot, ID_VOICE_WAVE)) {
                                        // Smart check: avoid stopping audio if already speaking
                                        List<AccessibilityNodeInfo> list = latestRoot.findAccessibilityNodeInfosByViewId(ID_PLAYBACK_ICON);
                                        if (list != null) {
                                            for (AccessibilityNodeInfo btn : list) {
                                                if (btn != null && btn.isVisibleToUser() && btn.isEnabled()) {
                                                    CharSequence desc = btn.getContentDescription();
                                                    lastPlaybackDescription = desc != null ? desc.toString() : "";
                                                    String d = lastPlaybackDescription.toLowerCase(Locale.ROOT);
                                                    if (d.contains("stop") || d.contains("pause")
                                                            || d.contains("останов") || d.contains("пауз")) {
                                                        lastPlaybackStatus = "already_playing";
                                                        btn.recycle();
                                                        return;
                                                    }
                                                    btn.recycle();
                                                }
                                            }
                                        }
                                        boolean clicked = clickNodeWithId(latestRoot, ID_PLAYBACK_ICON);
                                        lastPlaybackStatus = clicked ? "playback_clicked" : "playback_failed";
                                    }
                                } finally {
                                    latestRoot.recycle();
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }, 300L);
            }
        }
    }

    public static boolean showSystemPowerDialog() {
        GeminiPowerService svc = instance;
        if (svc != null) {
            performHaptic(svc);
            return svc.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
        return false;
    }

    public static boolean isMictsInstalled(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("com.parallelc.micts", 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isShizukuRunning(Context context) {
        try {
            Uri uri = Uri.parse("content://moe.shizuku.privileged.api.shizuku");
            ContentResolver cr = context.getContentResolver();
            Bundle res = cr.call(uri, "ping", null, null);
            return res != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isShizukuInstalled(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void triggerCircleToSearch(final Context context) {
        performHaptic(context);

        // 1. TOP PRIORITY: Launch native Circle to Search (MiCTS) with original Google shimmer
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

        // 2. Standalone fallback (only if MiCTS is not installed): Google Lens Screen Search
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

                                    Uri imageUri = ScreenshotProvider.getScreenshotUri();
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(imageUri, "image/jpeg");
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    intent.setPackage(GSA_PKG);
                                    try {
                                        context.startActivity(intent);
                                    } catch (Throwable t) {
                                        Intent generic = new Intent(Intent.ACTION_VIEW);
                                        generic.setDataAndType(imageUri, "image/jpeg");
                                        generic.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        context.startActivity(generic);
                                    }
                                }
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

        // Monitor Google FloatyActivity during active voice session for auto-mic and auto-TTS playback
        if (GSA_PKG.equals(pkg) && voiceSessionStartTime != 0L) {
            long elapsed = SystemClock.elapsedRealtime() - voiceSessionStartTime;
            if (elapsed <= 35000L) {
                checkVoiceSessionProgress(elapsed);
            }
        }

        // Intercept Vivo Wallet (Double Click Power Button) -> Global Circle to Search (MiCTS)
        if (isPrefEnabled(this, PREF_WALLET_CTS, true) && pkg.contains("vivo.wallet") && !cls.contains("Setting") && !cls.contains("CardBagSetting")) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastCtsTriggerTime >= 1200L) {
                lastCtsTriggerTime = now;
                performGlobalAction(GLOBAL_ACTION_BACK);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        triggerCircleToSearch(GeminiPowerService.this);
                    }
                }, 80L);
            }
            return;
        }

        // Intercept Vivo's Chinese VTouch screen recognition -> Global Google Circle to Search
        if (isPrefEnabled(this, PREF_VTOUCH_CTS, true) && "com.vivo.vtouch".equals(pkg) && !cls.contains("Setting") && !cls.contains("FAQ")) {
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

            // Dismiss Vivo's Chinese BlueLM Copilot / Jovi window
            performGlobalAction(GLOBAL_ACTION_BACK);

            // If user holds Power Button a SECOND time within 5 seconds of opening Gemini,
            // open the native System Power Menu (Reboot / Power Off)!
            if (isPrefEnabled(this, PREF_DOUBLE_POWER_MENU, true) && now - lastGeminiOpenTime < 5000L) {
                lastGeminiOpenTime = 0L;
                voiceSessionStartTime = 0L;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showSystemPowerDialog();
                    }
                }, 120L);
                return;
            }

            lastGeminiOpenTime = now;
            // Immediate handoff to Gemini (50ms) so mic starts with zero latency
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TriggerActivity.launchGeminiOverlay(GeminiPowerService.this);
                }
            }, 50L);
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("Gemini by kaiser v3.2 Official");
        writer.println("assistantTriggerCount=" + assistantTriggerCount);
        writer.println("lastAssistantEvent=" + lastAssistantEvent);
        writer.println("voiceSessionActive=" + (voiceSessionStartTime != 0L));
        writer.println("lastPlaybackStatus=" + lastPlaybackStatus);
        writer.println("lastPlaybackDescription=" + lastPlaybackDescription);
        writer.println("mictsInstalled=" + isMictsInstalled(this));
        writer.println("shizukuRunning=" + isShizukuRunning(this));
        writer.println("hapticEnabled=" + isPrefEnabled(this, PREF_HAPTIC, true));
        writer.println("walletCtsEnabled=" + isPrefEnabled(this, PREF_WALLET_CTS, true));
        writer.println("autoTtsEnabled=" + isPrefEnabled(this, PREF_AUTO_TTS, true));
        writer.println("writeSystemSettings=" + Settings.System.canWrite(this));
        TriggerActivity.dumpState(writer);
    }
}
