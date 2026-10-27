package com.kaiserdev.geminitrigger;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";
    public static final int SHIZUKU_REQ_CODE = 8801;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface AutoConfigCallback {
        void onProgress(String message);
        void onComplete(boolean success, String message);
    }

    public static class ExecResult {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";

        public boolean isSuccess() {
            return exitCode == 0;
        }

        @Override
        public String toString() {
            return "ExecResult{code=" + exitCode + ", stdout='" + stdout + "', stderr='" + stderr + "'}";
        }
    }

    public static boolean isShizukuInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!isBinderAlive()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (isBinderAlive()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to request Shizuku permission", t);
        }
    }

    public static ExecResult exec(String command) {
        ExecResult res = new ExecResult();
        if (!isBinderAlive()) {
            res.exitCode = -1;
            res.stderr = "Сервер Shizuku не подключен или не запущен";
            return res;
        }
        if (!hasPermission()) {
            res.exitCode = -2;
            res.stderr = "Нет разрешения Shizuku";
            return res;
        }
        try {
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) {
                res.exitCode = -3;
                res.stderr = "IShizukuService binder is null";
                return res;
            }
            IRemoteProcess process = service.newProcess(new String[]{"sh", "-c", command}, null, null);
            if (process == null) {
                res.exitCode = -4;
                res.stderr = "Не удалось создать удаленный процесс Shizuku";
                return res;
            }

            final StringBuilder outSb = new StringBuilder();
            final StringBuilder errSb = new StringBuilder();

            Thread tOut = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(process.getInputStream());
                         BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            outSb.append(line).append("\n");
                        }
                    } catch (Throwable ignored) {}
                }
            });

            Thread tErr = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(process.getErrorStream());
                         BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            errSb.append(line).append("\n");
                        }
                    } catch (Throwable ignored) {}
                }
            });

            tOut.start();
            tErr.start();

            res.exitCode = process.waitFor();
            tOut.join(3000);
            tErr.join(3000);

            res.stdout = outSb.toString().trim();
            res.stderr = errSb.toString().trim();
            Log.d(TAG, "exec [" + command + "] -> " + res);
            return res;
        } catch (Throwable t) {
            Log.e(TAG, "exec error for [" + command + "]", t);
            res.exitCode = -5;
            res.stderr = t.getMessage();
            return res;
        }
    }

    public static void runAutoConfigure(final Context context, final AutoConfigCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                if (!isBinderAlive() || !hasPermission()) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onComplete(false, "Сначала предоставьте разрешение Shizuku");
                            }
                        }
                    });
                    return;
                }

                try {
                    postProgress(callback, "Выдача WRITE_SECURE_SETTINGS...");
                    exec("pm grant com.kaiserdev.geminitrigger android.permission.WRITE_SECURE_SETTINGS");

                    postProgress(callback, "Разрешение поверх других приложений...");
                    exec("cmd appops set com.kaiserdev.geminitrigger SYSTEM_ALERT_WINDOW allow");

                    postProgress(callback, "Отключение ограничений энергосбережения...");
                    exec("dumpsys deviceidle whitelist +com.kaiserdev.geminitrigger");

                    postProgress(callback, "Включение службы Спец. возможностей...");
                    ExecResult currA11y = exec("settings get secure enabled_accessibility_services");
                    String a11yVal = currA11y.stdout != null ? currA11y.stdout.trim() : "";
                    String targetService = "com.kaiserdev.geminitrigger/com.kaiserdev.geminitrigger.GeminiPowerService";
                    if (!a11yVal.contains(targetService)) {
                        String newVal;
                        if (a11yVal.isEmpty() || "null".equals(a11yVal)) {
                            newVal = targetService;
                        } else {
                            newVal = a11yVal + ":" + targetService;
                        }
                        exec("settings put secure enabled_accessibility_services " + newVal);
                    }
                    exec("settings put secure accessibility_enabled 1");

                    postProgress(callback, "Настройка удержания питания OriginOS...");
                    exec("settings put system vivo_jovi_power_wakeup_switch 1");
                    exec("settings put secure vivo_jovi_power_wakeup_switch 1");
                    exec("settings put global power_button_long_press 5");

                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            TriggerActivity.ensureSystemSettingsIfPermitted(context);
                            if (callback != null) {
                                callback.onComplete(true, "Все системные настройки, спец. возможности и кнопка питания успешно сконфигурированы!");
                            }
                        }
                    });
                } catch (final Throwable t) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onComplete(false, "Ошибка авто-настройки: " + t.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    private static void postProgress(final AutoConfigCallback callback, final String message) {
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onProgress(message);
                }
            }
        });
    }
}
