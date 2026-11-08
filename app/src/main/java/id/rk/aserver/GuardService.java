package id.rk.aserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-started companion service. It keeps ASERVER's process awake and
 * performs a real HTTP health check against the configured local server URL.
 * It does NOT and cannot pin another app's process in Android.
 */
public class GuardService extends Service {
    public static final String ACTION_STOP = "id.rk.aserver.action.STOP_GUARD";
    public static final String ACTION_CHECK_NOW = "id.rk.aserver.action.CHECK_NOW";

    private static final String CHANNEL_ID = "aserver_guard_channel";
    private static final int NOTIFICATION_ID = 4101;
    private static final long CHECK_INTERVAL_MS = 15_000L;

    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;
    private Handler handler;
    private ExecutorService monitorExecutor;
    private final AtomicBoolean checkRunning = new AtomicBoolean(false);
    private volatile boolean stopping = false;

    private final Runnable monitorRunnable = this::runHealthCheck;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        monitorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ASERVER-HealthCheck");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopping = true;
            AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_ACTIVE, false).apply();
            stopMonitor();
            releaseLocks();
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        stopping = false;
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            AppPrefs.get(this).edit()
                    .putBoolean(AppPrefs.KEY_ACTIVE, true)
                    .putLong(AppPrefs.KEY_HEARTBEAT, System.currentTimeMillis())
                    .apply();
            acquireLocks();
            if (ACTION_CHECK_NOW.equals(action)) {
                runHealthCheck();
            } else {
                scheduleNextCheck(1_000L);
            }
        } catch (Exception error) {
            AppPrefs.get(this).edit()
                    .putBoolean(AppPrefs.KEY_ACTIVE, false)
                    .putString(AppPrefs.KEY_LAST_ERROR,
                            error.getClass().getSimpleName() + ": " + safeMessage(error))
                    .apply();
            stopMonitor();
            releaseLocks();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    private void acquireLocks() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                if (cpuLock == null) {
                    cpuLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            getPackageName() + ":ASERVER_CPU");
                    cpuLock.setReferenceCounted(false);
                }
                if (!cpuLock.isHeld()) cpuLock.acquire();
            }
        } catch (Exception error) {
            AppPrefs.get(this).edit().putString(AppPrefs.KEY_LAST_ERROR,
                    "CPU wakelock: " + safeMessage(error)).apply();
        }

        boolean keepWifi = AppPrefs.get(this).getBoolean(AppPrefs.KEY_WIFI_LOCK, true);
        if (keepWifi) {
            try {
                WifiManager wifiManager = (WifiManager) getApplicationContext()
                        .getSystemService(WIFI_SERVICE);
                if (wifiManager != null) {
                    if (wifiLock == null) {
                        wifiLock = wifiManager.createWifiLock(
                                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                                getPackageName() + ":ASERVER_WIFI");
                        wifiLock.setReferenceCounted(false);
                    }
                    if (!wifiLock.isHeld()) wifiLock.acquire();
                }
            } catch (Exception error) {
                AppPrefs.get(this).edit().putString(AppPrefs.KEY_LAST_ERROR,
                        "Wi-Fi lock: " + safeMessage(error)).apply();
            }
        } else if (wifiLock != null && wifiLock.isHeld()) {
            try { wifiLock.release(); } catch (Exception ignored) { }
        }
    }

    private void scheduleNextCheck(long delayMs) {
        if (handler == null) return;
        handler.removeCallbacks(monitorRunnable);
        handler.postDelayed(monitorRunnable, delayMs);
    }

    private void runHealthCheck() {
        if (stopping || monitorExecutor == null || !checkRunning.compareAndSet(false, true)) return;
        // Retry acquiring the Wi-Fi lock in case Wi-Fi was disabled during boot
        // or became available only after the foreground service had started.
        acquireLocks();
        try {
        monitorExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            SharedPreferences prefs = null;
            try {
                prefs = AppPrefs.get(this);
                String endpoint = prefs.getString(AppPrefs.KEY_MONITOR_URL,
                        AppPrefs.DEFAULT_MONITOR_URL);
                endpoint = endpoint == null ? "" : endpoint.trim();

                if (endpoint.isEmpty()) {
                    prefs.edit()
                            .putBoolean(AppPrefs.KEY_SERVER_ALIVE, false)
                            .putLong(AppPrefs.KEY_LAST_CHECK, now)
                            .putString(AppPrefs.KEY_LAST_ERROR, "Alamat monitor belum diisi")
                            .putLong(AppPrefs.KEY_HEARTBEAT, now)
                            .apply();
                } else {
                    checkEndpoint(endpoint, now, prefs, false);
                }

                String tunnelEndpoint = prefs.getString(AppPrefs.KEY_TUNNEL_URL, "");
                tunnelEndpoint = tunnelEndpoint == null ? "" : tunnelEndpoint.trim();
                if (tunnelEndpoint.isEmpty()) {
                    prefs.edit()
                            .putBoolean(AppPrefs.KEY_TUNNEL_ALIVE, false)
                            .putLong(AppPrefs.KEY_TUNNEL_LAST_CHECK, now)
                            .putInt(AppPrefs.KEY_TUNNEL_HTTP_CODE, 0)
                            .putString(AppPrefs.KEY_TUNNEL_LAST_ERROR, "Alamat tunnel publik belum diisi")
                            .putLong(AppPrefs.KEY_HEARTBEAT, now)
                            .apply();
                } else {
                    checkEndpoint(tunnelEndpoint, System.currentTimeMillis(), prefs, true);
                }
            } catch (Exception error) {
                if (prefs != null) {
                    prefs.edit()
                            .putBoolean(AppPrefs.KEY_SERVER_ALIVE, false)
                            .putLong(AppPrefs.KEY_LAST_CHECK, now)
                            .putString(AppPrefs.KEY_LAST_ERROR,
                                    error.getClass().getSimpleName() + ": " + safeMessage(error))
                            .putLong(AppPrefs.KEY_HEARTBEAT, now)
                            .apply();
                }
            } finally {
                checkRunning.set(false);
                if (!stopping && handler != null) {
                    handler.post(() -> {
                        if (stopping) return;
                        refreshNotification();
                        scheduleNextCheck(CHECK_INTERVAL_MS);
                    });
                }
            }
        });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            checkRunning.set(false);
        }
    }

    private void checkEndpoint(String endpoint, long now, android.content.SharedPreferences prefs,
                               boolean tunnel) {
        String aliveKey = tunnel ? AppPrefs.KEY_TUNNEL_ALIVE : AppPrefs.KEY_SERVER_ALIVE;
        String checkKey = tunnel ? AppPrefs.KEY_TUNNEL_LAST_CHECK : AppPrefs.KEY_LAST_CHECK;
        String codeKey = tunnel ? AppPrefs.KEY_TUNNEL_HTTP_CODE : AppPrefs.KEY_LAST_HTTP_CODE;
        String errorKey = tunnel ? AppPrefs.KEY_TUNNEL_LAST_ERROR : AppPrefs.KEY_LAST_ERROR;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new IllegalArgumentException("URL harus memakai http:// atau https://");
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(4_000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache, no-store");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("User-Agent", "ASERVER-Health-Monitor/1.4");

            int code = connection.getResponseCode();
            boolean online = code >= 200 && code < 300;
            prefs.edit()
                    .putBoolean(aliveKey, online)
                    .putLong(checkKey, now)
                    .putInt(codeKey, code)
                    .putString(errorKey, online ? "" : "HTTP " + code + " (endpoint tidak mengembalikan sukses)")
                    .putLong(AppPrefs.KEY_HEARTBEAT, now)
                    .apply();
        } catch (Exception error) {
            prefs.edit()
                    .putBoolean(aliveKey, false)
                    .putLong(checkKey, now)
                    .putInt(codeKey, 0)
                    .putString(errorKey, error.getClass().getSimpleName() + ": " + safeMessage(error))
                    .putLong(AppPrefs.KEY_HEARTBEAT, now)
                    .apply();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void refreshNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        } else {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 100, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, GuardService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 101, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent checkIntent = new Intent(this, GuardService.class);
        checkIntent.setAction(ACTION_CHECK_NOW);
        PendingIntent checkPending = PendingIntent.getService(this, 102, checkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String endpoint = AppPrefs.get(this).getString(AppPrefs.KEY_MONITOR_URL,
                AppPrefs.DEFAULT_MONITOR_URL);
        boolean online = AppPrefs.get(this).getBoolean(AppPrefs.KEY_SERVER_ALIVE, false);
        int code = AppPrefs.get(this).getInt(AppPrefs.KEY_LAST_HTTP_CODE, 0);
        long lastCheck = AppPrefs.get(this).getLong(AppPrefs.KEY_LAST_CHECK, 0L);
        String error = AppPrefs.get(this).getString(AppPrefs.KEY_LAST_ERROR, "");
        String tunnelEndpoint = AppPrefs.get(this).getString(AppPrefs.KEY_TUNNEL_URL, "");
        boolean tunnelOnline = AppPrefs.get(this).getBoolean(AppPrefs.KEY_TUNNEL_ALIVE, false);
        int tunnelCode = AppPrefs.get(this).getInt(AppPrefs.KEY_TUNNEL_HTTP_CODE, 0);
        String tunnelError = AppPrefs.get(this).getString(AppPrefs.KEY_TUNNEL_LAST_ERROR, "");
        String tunnelLine = tunnelEndpoint == null || tunnelEndpoint.trim().isEmpty()
                ? "TUNNEL BELUM DIATUR"
                : (tunnelOnline ? "TUNNEL ONLINE • HTTP " + tunnelCode : "TUNNEL OFFLINE");
        String status;
        if (lastCheck == 0L) status = "Menyiapkan pemeriksaan server lokal…";
        else if (System.currentTimeMillis() - lastCheck > 45_000L) status = "STATUS SERVER LOKAL KEDALUWARSA";
        else if (online) status = "SERVER LOKAL ONLINE • HTTP " + code;
        else status = "SERVER LOKAL OFFLINE • Periksa alamat server";
        String detail = status + "\n" + tunnelLine + "\nMonitor lokal: " + endpoint
                + (tunnelEndpoint == null || tunnelEndpoint.trim().isEmpty() ? "" : "\nMonitor tunnel: " + tunnelEndpoint)
                + (error == null || error.trim().isEmpty() ? "" : "\nDetail lokal: " + error)
                + (tunnelError == null || tunnelError.trim().isEmpty() ? "" : "\nDetail tunnel: " + tunnelError)
                + "\nASERVER memantau koneksi, tetapi tidak dapat mengunci proses APK lain.";

        builder.setSmallIcon(R.drawable.ic_aserver)
                .setContentTitle("ASERVER aktif — penjagaan berjalan")
                .setContentText(status)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_popup_sync, "Cek sekarang", checkPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan", stopPending);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Penjagaan ASERVER", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Status penjagaan dan pemeriksaan server lokal ASERVER");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "tidak ada detail";
        return message.length() > 150 ? message.substring(0, 150) : message;
    }

    private void stopMonitor() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }
    }

    private void releaseLocks() {
        try {
            if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        } catch (Exception ignored) { }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        stopMonitor();
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
