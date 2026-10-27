package com.kaiserdev.geminitrigger;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
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

    // Voice session tracking (mic trigger + auto-TTS speech output)
    private long voiceSessionStartTime = 0L;
    private boolean micConfirmedActive = false;
    private int micAttemptCount = 0;
    private boolean playbackAutoClicked = false;
    private String lastPlaybackStatus = "idle";
    private String lastPlaybackDescription = "";

    private AudioManager audioManager;
    private AudioManager.AudioRecordingCallback recordingCallback;

    private static final String GSA_PKG = "com.google.android.googlequicksearchbox";
    private static final String ID_VOICE_WAVE = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_voice_wave_view";
    private static final String ID_TEXT_QUERY = "com.google.android.googlequicksearchbox:id/assistant_robin_floaty_single_line_query";
    private static final String ID_MIC_BUTTON = "com.google.android.googlequicksearchbox:id/assistant_robin_mic_button";
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

    private void setupAudioRecordingMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    recordingCallback = new AudioManager.AudioRecordingCallback() {
                        @Override
                        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                            super.onRecordingConfigChanged(configs);
                            if (voiceSessionStartTime != 0L && !micConfirmedActive) {
                                if (isAudioRecordingActive(configs)) {
                                    micConfirmedActive = true;
                                    lastPlaybackStatus = "mic_listening_hardware";
                                    performHaptic(GeminiPowerService.this);
                                    handler.removeCallbacks(micActivationRunnable);
                                }
                            }
                        }
                    };
                    audioManager.registerAudioRecordingCallback(recordingCallback, handler);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean checkIfRecordingActive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioManager != null) {
            try {
                List<AudioRecordingConfiguration> configs = audioManager.getActiveRecordingConfigurations();
                return isAudioRecordingActive(configs);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private boolean isAudioRecordingActive(List<AudioRecordingConfiguration> configs) {
        if (configs == null || configs.isEmpty()) {
            return false;
        }
        for (AudioRecordingConfiguration c : configs) {
            if (c != null) {
                int src = c.getClientAudioSource();
                if (src == MediaRecorder.AudioSource.VOICE_RECOGNITION 
                        || src == MediaRecorder.AudioSource.MIC) {
                    return true;
                }
            }
        }
        return false;
    }

    private final Runnable micActivationRunnable = new Runnable() {
        @Override
        public void run() {
            if (voiceSessionStartTime == 0L || micConfirmedActive) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime() - voiceSessionStartTime;
            if (elapsed > 2500L) {
                return;
            }

            // Check if audio recording is already active
            if (checkIfRecordingActive()) {
                micConfirmedActive = true;
                lastPlaybackStatus = "mic_listening_confirmed";
                performHaptic(GeminiPowerService.this);
                return;
            }

            // Calculate precise physical coordinates of the microphone button on Gemini card
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            // Physical button center on Vivo X300 Ultra (1260x2772: 867, 2625)
            int micX = (int) (screenWidth * 0.6881f);
            int micY = (int) (screenHeight * 0.94697f);

            micAttemptCount++;
            lastPlaybackStatus = "mic_tap_phys_" + micAttemptCount;
            tapCoordinates(micX, micY);

            // Retry after 260ms if still not active and attempt count < 3
            if (micAttemptCount < 3 && !micConfirmedActive && voiceSessionStartTime != 0L) {
                handler.postDelayed(this, 260L);
            }
        }
    };

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
                handler.postDelayed(this, 800L);
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
        svc.micAttemptCount = 0;
        svc.playbackAutoClicked = false;
        svc.lastPlaybackStatus = "waiting_for_google_window";
        svc.lastPlaybackDescription = "";

        // Physical mic activation ticker (starts at 290ms when Floaty card has slid up)
        svc.handler.removeCallbacks(svc.micActivationRunnable);
        svc.handler.postDelayed(svc.micActivationRunnable, 290L);

        // Relaxed watcher for TTS answer playback
        svc.handler.removeCallbacks(svc.sessionWatcherRunnable);
        svc.handler.postDelayed(svc.sessionWatcherRunnable, 2000L);
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
        return null;
    }

    private void inspectGoogleFloatyForMic(AccessibilityNodeInfo root, long elapsed) {
        if (hasVisibleNodeWithId(root, ID_VOICE_WAVE)) {
            micConfirmedActive = true;
            lastPlaybackStatus = "mic_listening";
            performHaptic(this);
            return;
        }

        if (elapsed >= 220L && !micConfirmedActive) {
            if (hasVisibleNodeWithId(root, ID_MIC_BUTTON) || hasVisibleNodeWithId(root, ID_TEXT_QUERY)) {
                micAttemptCount++;
                if (tapNodeWithId(root, ID_MIC_BUTTON)) {
                    lastPlaybackStatus = "mic_tapped_event_" + micAttemptCount;
                }
            }
        }
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

    private boolean tapNodeWithId(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null) {
                        if (n.isVisibleToUser() && n.isEnabled()) {
                            Rect bounds = new Rect();
                            n.getBoundsInScreen(bounds);
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            n.recycle();
                            int cx = bounds.centerX();
                            int cy = bounds.centerY();
                            if (cx > 0 && cy > 0 && bounds.width() > 10 && bounds.height() > 10) {
                                tapCoordinates(cx, cy);
                            }
                            return true;
                        } else {
                            n.recycle();
                        }
                    }
                }
            }
            // Fallback search by contentDescription ("Микрофон" / "Microphone")
            AccessibilityNodeInfo micDesc = findNodeByContentDesc(root, "Микрофон");
            if (micDesc == null) {
                micDesc = findNodeByContentDesc(root, "Microphone");
            }
            if (micDesc != null) {
                Rect bounds = new Rect();
                micDesc.getBoundsInScreen(bounds);
                micDesc.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                micDesc.recycle();
                int cx = bounds.centerX();
                int cy = bounds.centerY();
                if (cx > 0 && cy > 0 && bounds.width() > 10 && bounds.height() > 10) {
                    tapCoordinates(cx, cy);
                }
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private AccessibilityNodeInfo findNodeByContentDesc(AccessibilityNodeInfo root, String query) {
        if (root == null) return null;
        try {
            CharSequence cd = root.getContentDescription();
            if (cd != null && cd.toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                if (root.isVisibleToUser()) {
                    return root;
                }
            }
            int childCount = root.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = root.getChild(i);
                if (child != null) {
                    AccessibilityNodeInfo match = findNodeByContentDesc(child, query);
                    if (match != null) {
                        if (match != child) {
                            child.recycle();
                        }
                        return match;
                    }
                    child.recycle();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean tapCoordinates(final int x, final int y) {
        if (x <= 0 || y <= 0) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Path clickPath = new Path();
                clickPath.moveTo(x, y);
                clickPath.lineTo(x, y + 2);
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(clickPath, 0, 40);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(stroke);
                return dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        lastPlaybackStatus = "gesture_completed";
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        lastPlaybackStatus = "gesture_cancelled";
                    }
                }, null);
            } catch (Throwable ignored) {
            }
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
                                        boolean clicked = tapNodeWithId(latestRoot, ID_PLAYBACK_ICON);
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
        if (ShizukuHelper.isBinderAlive()) {
            return true;
        }
        try {
            Uri uri = Uri.parse("content://moe.shizuku.privileged.api.shizuku");
            ContentResolver cr = context.getContentResolver();
            Bundle res = cr.call(uri, "getVersion", null, null);
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
        setupAudioRecordingMonitor();
        TriggerActivity.ensureSystemSettingsIfPermitted(this);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        voiceSessionStartTime = 0L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioManager != null && recordingCallback != null) {
            try {
                audioManager.unregisterAudioRecordingCallback(recordingCallback);
            } catch (Throwable ignored) {
            }
        }
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

        // Monitor Google FloatyActivity during active voice session for auto-TTS playback
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

        if (!matched && (pkg == null || pkg.isEmpty()) && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    CharSequence rootPkg = root.getPackageName();
                    if (rootPkg != null && isVivoCopilotPackage(rootPkg.toString())) {
                        matched = true;
                    }
                    root.recycle();
                }
            } catch (Throwable ignored) {
            }
        }

        if (matched) {
            lastAssistantEvent = pkg + "/" + cls;
            long now = SystemClock.elapsedRealtime();

            // 1. Debounce hardware button press:
            // A physical hold lasts ~1000-1400ms. Discard repeat events from the SAME hold!
            if (now - lastTriggerTime < 1600L) {
                return;
            }
            lastTriggerTime = now;
            assistantTriggerCount++;

            // 2. Multi-press check: Second deliberate hold within 5.5 seconds -> System Power Dialog
            if (isPrefEnabled(this, PREF_DOUBLE_POWER_MENU, true)
                    && lastGeminiOpenTime != 0L
                    && (now - lastGeminiOpenTime) >= 1600L
                    && (now - lastGeminiOpenTime) <= 5500L) {

                lastGeminiOpenTime = 0L;
                voiceSessionStartTime = 0L;
                handler.removeCallbacks(sessionWatcherRunnable);

                // Dismiss Copilot and open System Power Dialog
                performGlobalAction(GLOBAL_ACTION_BACK);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showSystemPowerDialog();
                    }
                }, 100L);
                return;
            }

            // 3. First press: launch Gemini
            lastGeminiOpenTime = now;
            // Dismiss Vivo's Chinese BlueLM Copilot / Jovi window
            performGlobalAction(GLOBAL_ACTION_BACK);

            // 140ms clean window buffer to ensure Copilot dismissed cleanly before Gemini appears
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
