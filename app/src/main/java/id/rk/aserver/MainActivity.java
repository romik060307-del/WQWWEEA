package id.rk.aserver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_POST_NOTIFICATIONS = 74;
    private static final int BG = Color.rgb(242, 246, 244);
    private static final int INK = Color.rgb(21, 43, 39);
    private static final int MUTED = Color.rgb(99, 117, 111);
    private static final int GREEN = Color.rgb(20, 133, 108);
    private static final int PALE = Color.rgb(224, 239, 233);

    private LinearLayout appList;
    private TextView serviceStatus;
    private TextView selectedSummary;
    private TextView monitorStatus;
    private TextView tunnelStatus;
    private TextView batteryStatus;
    private EditText monitorUrlInput;
    private EditText tunnelUrlInput;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable uiRefresh = new Runnable() {
        @Override public void run() {
            updateServiceStatus();
            uiHandler.postDelayed(this, 3_000L);
        }
    };
    private final List<LaunchableApp> apps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 22, 19));
        getWindow().setNavigationBarColor(Color.rgb(8, 22, 19));
        getWindow().getDecorView().setSystemUiVisibility(0);
        selected.addAll(AppPrefs.selectedPackages(this));
        buildScreen();
        loadLaunchableApps();
        updateServiceStatus();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        uiHandler.removeCallbacks(uiRefresh);
        uiHandler.post(uiRefresh);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(uiRefresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(root);

        TextView eyebrow = text("RK • ANDROID SERVER GUARD", 12, GREEN, true);
        eyebrow.setLetterSpacing(0.10f);
        root.addView(eyebrow, matchWrap());

        TextView title = text("ASERVER", 34, INK, true);
        title.setPadding(0, dp(7), 0, 0);
        root.addView(title, matchWrap());

        TextView intro = text("Pendamping server Android untuk menjaga layanan ASERVER tetap aktif dan memantau koneksi server pilihan.", 15, MUTED, false);
        intro.setPadding(0, dp(5), 0, dp(20));
        root.addView(intro, matchWrap());

        LinearLayout statusCard = card();
        root.addView(statusCard, matchWrap());
        statusCard.addView(text("STATUS PENJAGAAN", 12, GREEN, true), matchWrap());
        serviceStatus = text("Memeriksa layanan…", 20, INK, true);
        serviceStatus.setPadding(0, dp(10), 0, dp(5));
        statusCard.addView(serviceStatus, matchWrap());
        statusCard.addView(text("ASERVER memakai foreground service dan wakelock. Status ini menunjukkan layanan ASERVER, bukan jaminan proses APK lain tidak akan dihentikan Android.", 13, MUTED, false), matchWrap());

        LinearLayout appsCard = card();
        LinearLayout.LayoutParams appsParams = matchWrap();
        appsParams.topMargin = dp(16);
        root.addView(appsCard, appsParams);
        appsCard.addView(text("1. PILIH APLIKASI SERVER", 16, INK, true), matchWrap());
        TextView appsHelp = text("Pilih aplikasi target secara opsional. ASERVER tetap bisa menjalankan penjagaannya sendiri tanpa memilih aplikasi lain. Tombol BUKA menjalankan aplikasi target; SETELAN membuka pengaturan Android aplikasi tersebut.", 13, MUTED, false);
        appsHelp.setPadding(0, dp(7), 0, dp(12));
        appsCard.addView(appsHelp, matchWrap());

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        appsCard.addView(appList, matchWrap());

        Button refresh = button("MUAT ULANG DAFTAR APLIKASI", false);
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(12);
        appsCard.addView(refresh, refreshParams);
        refresh.setOnClickListener(v -> loadLaunchableApps());

        selectedSummary = text("Belum ada aplikasi yang dipilih.", 13, MUTED, false);
        selectedSummary.setPadding(0, dp(12), 0, 0);
        appsCard.addView(selectedSummary, matchWrap());

        LinearLayout actionsCard = card();
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        root.addView(actionsCard, actionsParams);
        actionsCard.addView(text("2. KONTROL SERVER", 16, INK, true), matchWrap());
        TextView actionsHelp = text("Aktifkan penjagaan terlebih dahulu. Setelah itu buka aplikasi server yang dipilih agar server di dalam APK tersebut mulai berjalan.", 13, MUTED, false);
        actionsHelp.setPadding(0, dp(7), 0, dp(14));
        actionsCard.addView(actionsHelp, matchWrap());

        Button start = button("AKTIFKAN PENJAGAAN", true);
        actionsCard.addView(start, matchWrap());
        start.setOnClickListener(v -> startGuard());

        Button open = button("BUKA APLIKASI SERVER DIPILIH", false);
        LinearLayout.LayoutParams openParams = matchWrap();
        openParams.topMargin = dp(9);
        actionsCard.addView(open, openParams);
        open.setOnClickListener(v -> openSelectedApps());

        Button stop = button("HENTIKAN PENJAGAAN", false);
        LinearLayout.LayoutParams stopParams = matchWrap();
        stopParams.topMargin = dp(9);
        actionsCard.addView(stop, stopParams);
        stop.setOnClickListener(v -> stopGuard());

        LinearLayout monitorCard = card();
        LinearLayout.LayoutParams monitorParams = matchWrap();
        monitorParams.topMargin = dp(16);
        root.addView(monitorCard, monitorParams);
        monitorCard.addView(text("3. MONITOR SERVER SEBENARNYA", 16, INK, true), matchWrap());
        TextView monitorHelp = text("Masukkan alamat HTTP lokal milik server. ASERVER mengujinya setiap 15 detik. Alamat awal memakai port 8080; ubah jika server kamu memakai IP, port, atau jalur lain.", 13, MUTED, false);
        monitorHelp.setPadding(0, dp(7), 0, dp(10));
        monitorCard.addView(monitorHelp, matchWrap());

        monitorUrlInput = new EditText(this);
        monitorUrlInput.setSingleLine(true);
        monitorUrlInput.setTextSize(14);
        monitorUrlInput.setTextColor(INK);
        monitorUrlInput.setHintTextColor(MUTED);
        monitorUrlInput.setHint("http://127.0.0.1:8080/health");
        monitorUrlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        monitorUrlInput.setText(AppPrefs.get(this).getString(
                AppPrefs.KEY_MONITOR_URL, AppPrefs.DEFAULT_MONITOR_URL));
        monitorUrlInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        monitorUrlInput.setBackground(roundRect(Color.rgb(246, 250, 248), 10,
                Color.rgb(221, 231, 226), 1));
        monitorCard.addView(monitorUrlInput, matchWrap());

        TextView tunnelHelp = text("ALAMAT TUNNEL PUBLIK (OPSIONAL)", 12, GREEN, true);
        LinearLayout.LayoutParams tunnelHelpParams = matchWrap();
        tunnelHelpParams.topMargin = dp(14);
        monitorCard.addView(tunnelHelp, tunnelHelpParams);
        tunnelUrlInput = new EditText(this);
        tunnelUrlInput.setSingleLine(true);
        tunnelUrlInput.setTextSize(14);
        tunnelUrlInput.setTextColor(INK);
        tunnelUrlInput.setHintTextColor(MUTED);
        tunnelUrlInput.setHint("https://nama-tunnel.trycloudflare.com/health");
        tunnelUrlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        tunnelUrlInput.setText(AppPrefs.get(this).getString(AppPrefs.KEY_TUNNEL_URL, ""));
        tunnelUrlInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        tunnelUrlInput.setBackground(roundRect(Color.rgb(246, 250, 248), 10,
                Color.rgb(221, 231, 226), 1));
        monitorCard.addView(tunnelUrlInput, matchWrap());
        TextView tunnelNote = text("Opsional: masukkan URL HTTPS publik yang digunakan untuk mengakses server melalui tunnel. Gunakan jalur pemeriksaan yang benar untuk server kamu; kosongkan bila tidak memakai tunnel.", 12, MUTED, false);
        tunnelNote.setPadding(0, dp(7), 0, dp(6));
        monitorCard.addView(tunnelNote, matchWrap());

        Button saveMonitor = button("SIMPAN KEDUA ALAMAT MONITOR", true);
        LinearLayout.LayoutParams saveMonitorParams = matchWrap();
        saveMonitorParams.topMargin = dp(9);
        monitorCard.addView(saveMonitor, saveMonitorParams);
        saveMonitor.setOnClickListener(v -> saveMonitorUrl());

        monitorStatus = text("Status server belum diperiksa.", 14, MUTED, true);
        monitorStatus.setPadding(0, dp(12), 0, dp(8));
        monitorCard.addView(monitorStatus, matchWrap());
        tunnelStatus = text("TUNNEL PUBLIK BELUM DIATUR.", 13, MUTED, true);
        tunnelStatus.setPadding(0, dp(5), 0, dp(8));
        monitorCard.addView(tunnelStatus, matchWrap());

        Button checkNow = button("CEK SERVER SEKARANG", false);
        monitorCard.addView(checkNow, matchWrap());
        checkNow.setOnClickListener(v -> checkServerNow());

        TextView monitorNote = text("SERVER LOKAL menguji alamat di atas. TUNNEL PUBLIK menguji jalur HTTPS dari internet. ONLINE hanya bila endpoint mengembalikan HTTP 2xx. Monitor mendeteksi gangguan, bukan memaksa aplikasi lain menyala kembali.", 12, MUTED, false);
        monitorNote.setPadding(0, dp(10), 0, 0);
        monitorCard.addView(monitorNote, matchWrap());

        LinearLayout optionsCard = card();
        LinearLayout.LayoutParams optionsParams = matchWrap();
        optionsParams.topMargin = dp(16);
        root.addView(optionsCard, optionsParams);
        optionsCard.addView(text("4. PENGATURAN LATAR BELAKANG", 16, INK, true), matchWrap());

        CheckBox wifi = checkBox("Pertahankan koneksi Wi‑Fi saat layar mati", true);
        wifi.setChecked(AppPrefs.get(this).getBoolean(AppPrefs.KEY_WIFI_LOCK, true));
        optionsCard.addView(wifi, matchWrap());
        wifi.setOnCheckedChangeListener((buttonView, checked) ->
                AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_WIFI_LOCK, checked).apply());

        CheckBox boot = checkBox("Aktifkan ASERVER lagi setelah HP dinyalakan", true);
        boot.setChecked(AppPrefs.get(this).getBoolean(AppPrefs.KEY_AUTO_START, true));
        optionsCard.addView(boot, matchWrap());
        boot.setOnCheckedChangeListener((buttonView, checked) ->
                AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_AUTO_START, checked).apply());

        batteryStatus = text(batteryOptimizationText(), 13, MUTED, false);
        batteryStatus.setPadding(0, dp(10), 0, dp(10));
        optionsCard.addView(batteryStatus, matchWrap());

        Button battery = button("IZINKAN ASERVER TANPA OPTIMASI BATERAI", true);
        optionsCard.addView(battery, matchWrap());
        battery.setOnClickListener(v -> requestBatteryExemption());

        Button batterySettings = button("BUKA PENGATURAN OPTIMASI BATERAI", false);
        LinearLayout.LayoutParams batterySettingsParams = matchWrap();
        batterySettingsParams.topMargin = dp(8);
        optionsCard.addView(batterySettings, batterySettingsParams);
        batterySettings.setOnClickListener(v -> openBatterySettings());

        Button vivoGuide = button("PANDUAN KHUSUS HP VIVO", false);
        LinearLayout.LayoutParams vivoParams = matchWrap();
        vivoParams.topMargin = dp(8);
        optionsCard.addView(vivoGuide, vivoParams);
        vivoGuide.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Pengaturan Vivo agar server tidak mudah berhenti")
                .setMessage("Lakukan untuk ASERVER, OHLI Social Server, dan RKM Tunnel Lite satu per satu:\n\n1. Buka Pengaturan > Baterai > Penggunaan daya latar belakang / Manajemen konsumsi daya.\n2. Pilih aplikasi > izinkan aktivitas/penggunaan daya latar belakang atau pilih tanpa batasan (nama menu dapat berbeda menurut Funtouch OS).\n3. Buka iManager > Manajemen aplikasi > Autostart, lalu izinkan aplikasi jika menu tersedia.\n4. Buka Info aplikasi > Baterai dan aktifkan Izinkan aktivitas latar belakang.\n5. Jangan tekan Paksa berhenti. Kunci aplikasi di layar Aplikasi terbaru bila tersedia.\n6. Matikan Penghemat baterai saat server dipakai.\n\nSetelah itu mulai server OHLI dan tunnel secara manual. ASERVER tidak dapat menyalakan proses APK lain dari latar belakang karena pembatasan Android.")
                .setPositiveButton("BUKA SETELAN ASERVER", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                    catch (Exception ignored) { openBatterySettings(); }
                }).setNegativeButton("TUTUP", null).show());

        LinearLayout infoCard = card();
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.topMargin = dp(16);
        root.addView(infoCard, infoParams);
        infoCard.addView(text("PENTING — BATAS ANDROID", 15, INK, true), matchWrap());
        TextView limitation = text(
                "ASERVER menjaga prosesnya sendiri dengan foreground service, CPU wakelock, dan opsi Wi-Fi lock. ASERVER juga dapat membuka aplikasi target dan memeriksa endpoint server. Android tidak menyediakan izin umum bagi aplikasi pendamping untuk mengunci proses APK lain atau menjamin APK lain tidak dihentikan. Atur kebijakan baterai untuk ASERVER dan setiap aplikasi target. Paksa berhenti, kehabisan memori, Doze, suhu, atau kebijakan merek HP masih dapat menghentikan layanan.",
                13, MUTED, false);
        limitation.setPadding(0, dp(9), 0, 0);
        infoCard.addView(limitation, matchWrap());

        TextView footer = text("ASERVER 1.5.1 • Vivo: aktifkan Autostart dan izinkan aktivitas latar belakang untuk setiap APK.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(18), dp(8), dp(8));
        root.addView(footer, matchWrap());

        setContentView(scroll);
    }

    private void loadLaunchableApps() {
        apps.clear();
        appList.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(launcher, 0);
        Set<String> seen = new HashSet<>();

        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName) || !seen.add(packageName)) continue;
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                CharSequence label = pm.getApplicationLabel(appInfo);
                Intent launch = pm.getLaunchIntentForPackage(packageName);
                if (launch == null) continue;
                apps.add(new LaunchableApp(packageName, label == null ? packageName : label.toString()));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        Collections.sort(apps, Comparator
                .comparingInt((LaunchableApp item) -> selected.contains(item.packageName) ? 0 : 1)
                .thenComparing(item -> item.label.toLowerCase()));

        if (apps.isEmpty()) {
            appList.addView(text("Belum ditemukan aplikasi pihak ketiga yang bisa dibuka. Pasang aplikasi server terlebih dahulu, lalu muat ulang daftar.", 14, MUTED, false), matchWrap());
        } else {
            for (LaunchableApp app : apps) addAppRow(app);
        }
        updateSelectionSummary();
    }

    private void addAppRow(LaunchableApp app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setBackground(roundRect(Color.rgb(246, 250, 248), 12, Color.rgb(229, 237, 233), 1));
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.bottomMargin = dp(8);
        appList.addView(row, rowParams);

        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        check.setChecked(selected.contains(app.packageName));
        row.addView(check, new LinearLayout.LayoutParams(dp(36), dp(44)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(5), 0, dp(5), 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(app.label, 14, INK, true), matchWrap());
        labels.addView(text(app.packageName, 10, MUTED, false), matchWrap());

        Button launch = new Button(this);
        launch.setText("BUKA");
        launch.setTextSize(10);
        launch.setTextColor(GREEN);
        launch.setAllCaps(false);
        launch.setMinWidth(0);
        launch.setMinimumWidth(0);
        launch.setPadding(dp(7), 0, dp(7), 0);
        row.addView(launch, new LinearLayout.LayoutParams(dp(62), dp(42)));
        launch.setOnClickListener(v -> launchApp(app.packageName));

        Button settings = new Button(this);
        settings.setText("SETELAN");
        settings.setTextSize(9);
        settings.setTextColor(INK);
        settings.setAllCaps(false);
        settings.setMinWidth(0);
        settings.setMinimumWidth(0);
        settings.setPadding(dp(3), 0, dp(3), 0);
        row.addView(settings, new LinearLayout.LayoutParams(dp(64), dp(42)));
        settings.setOnClickListener(v -> openTargetAppSettings(app.packageName));

        check.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) selected.add(app.packageName);
            else selected.remove(app.packageName);
            AppPrefs.saveSelectedPackages(this, selected);
            updateSelectionSummary();
        });
    }

    private void saveMonitorUrl() {
        String value = monitorUrlInput == null ? "" : monitorUrlInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Alamat monitor tidak boleh kosong.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            Toast.makeText(this, "Alamat harus diawali http:// atau https://", Toast.LENGTH_LONG).show();
            return;
        }
        String tunnel = tunnelUrlInput == null ? "" : tunnelUrlInput.getText().toString().trim();
        if (!tunnel.isEmpty() && !tunnel.startsWith("http://") && !tunnel.startsWith("https://")) {
            Toast.makeText(this, "Alamat tunnel harus diawali https:// atau http://", Toast.LENGTH_LONG).show();
            return;
        }
        AppPrefs.get(this).edit()
                .putString(AppPrefs.KEY_MONITOR_URL, value)
                .putString(AppPrefs.KEY_TUNNEL_URL, tunnel)
                .apply();
        Toast.makeText(this, "Alamat server lokal dan tunnel disimpan.", Toast.LENGTH_SHORT).show();
        checkServerNow();
    }

    private void checkServerNow() {
        if (!AppPrefs.get(this).getBoolean(AppPrefs.KEY_ACTIVE, false)) {
            Toast.makeText(this, "Aktifkan penjagaan dahulu agar monitor berjalan.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(this, GuardService.class);
            intent.setAction(GuardService.ACTION_CHECK_NOW);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            Toast.makeText(this, "Pemeriksaan server dimulai.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Tidak dapat memeriksa: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void openTargetAppSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Setelan aplikasi tidak dapat dibuka.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startGuard() {
        // ASERVER's own guardian is independent of whether a target app is selected.
        AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_ACTIVE, true).apply();
        try {
            Intent service = new Intent(this, GuardService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            Toast.makeText(this, "Penjagaan ASERVER diaktifkan.", Toast.LENGTH_SHORT).show();
            updateServiceStatus();
        } catch (Exception error) {
            AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_ACTIVE, false).apply();
            Toast.makeText(this, "Android menolak layanan latar depan: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            updateServiceStatus();
        }
    }

    private void stopGuard() {
        AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_ACTIVE, false).apply();
        stopService(new Intent(this, GuardService.class));
        updateServiceStatus();
        Toast.makeText(this, "Penjagaan ASERVER dihentikan.", Toast.LENGTH_SHORT).show();
    }

    private void openSelectedApps() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Pilih aplikasi server dahulu.", Toast.LENGTH_SHORT).show();
            return;
        }
        int opened = 0;
        for (LaunchableApp app : apps) {
            if (selected.contains(app.packageName) && launchApp(app.packageName)) opened++;
        }
        if (opened == 0) Toast.makeText(this, "Aplikasi yang dipilih tidak ditemukan atau tidak bisa dibuka.", Toast.LENGTH_LONG).show();
        else Toast.makeText(this, "Perintah buka dikirim ke " + opened + " aplikasi.", Toast.LENGTH_SHORT).show();
    }

    private boolean launchApp(String packageName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "Pengaturan ini tidak diperlukan pada versi Android ini.", Toast.LENGTH_SHORT).show();
            return;
        }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && power.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "ASERVER sudah dikecualikan dari optimasi baterai.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            openBatterySettings();
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    private String batteryOptimizationText() {
        if (Build.VERSION.SDK_INT < 23) return "Android versi ini tidak memakai Doze.";
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean exempt = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        return exempt
                ? "Status baterai ASERVER: sudah dikecualikan dari optimasi baterai. Periksa pengaturan baterai setiap aplikasi target secara terpisah."
                : "Status baterai ASERVER: masih dapat dibatasi oleh Doze. Minta pengecualian di bawah, lalu periksa pengaturan baterai aplikasi target.";
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    private void updateServiceStatus() {
        if (serviceStatus == null) return;
        boolean active = AppPrefs.get(this).getBoolean(AppPrefs.KEY_ACTIVE, false);
        long heartbeat = AppPrefs.get(this).getLong(AppPrefs.KEY_HEARTBEAT, 0L);
        boolean heartbeatFresh = heartbeat > 0L
                && System.currentTimeMillis() - heartbeat < 45_000L;
        if (!active) {
            serviceStatus.setText("○ PENJAGAAN NONAKTIF");
            serviceStatus.setTextColor(MUTED);
        } else if (heartbeatFresh) {
            serviceStatus.setText("● LAYANAN ASERVER MERESPONS");
            serviceStatus.setTextColor(GREEN);
        } else {
            serviceStatus.setText("! STATUS LAYANAN PERLU DIPERIKSA");
            serviceStatus.setTextColor(Color.rgb(176, 105, 28));
        }

        if (batteryStatus != null) batteryStatus.setText(batteryOptimizationText());

        if (monitorStatus != null) {
            boolean online = AppPrefs.get(this).getBoolean(AppPrefs.KEY_SERVER_ALIVE, false);
            long checkedAt = AppPrefs.get(this).getLong(AppPrefs.KEY_LAST_CHECK, 0L);
            int code = AppPrefs.get(this).getInt(AppPrefs.KEY_LAST_HTTP_CODE, 0);
            String error = AppPrefs.get(this).getString(AppPrefs.KEY_LAST_ERROR, "");
            if (!active || checkedAt == 0L) {
                monitorStatus.setText("SERVER BELUM DIPERIKSA");
                monitorStatus.setTextColor(MUTED);
            } else if (System.currentTimeMillis() - checkedAt > 45_000L) {
                monitorStatus.setText("! STATUS PEMERIKSAAN KEDALUWARSA\nBelum ada respons baru dalam 45 detik.");
                monitorStatus.setTextColor(Color.rgb(176, 105, 28));
            } else if (online) {
                monitorStatus.setText("● SERVER ONLINE  •  HTTP " + code + "\nPemeriksaan terakhir: " + android.text.format.DateFormat.format("HH:mm:ss", checkedAt));
                monitorStatus.setTextColor(GREEN);
            } else {
                monitorStatus.setText("● SERVER OFFLINE / TIDAK MERESPONS\n" + (error == null ? "Tidak ada detail" : error)
                        + "\nPemeriksaan terakhir: " + android.text.format.DateFormat.format("HH:mm:ss", checkedAt));
                monitorStatus.setTextColor(Color.rgb(176, 75, 56));
            }
        }
        if (tunnelStatus != null) {
            String tunnelUrl = AppPrefs.get(this).getString(AppPrefs.KEY_TUNNEL_URL, "");
            boolean tunnelOnline = AppPrefs.get(this).getBoolean(AppPrefs.KEY_TUNNEL_ALIVE, false);
            long tunnelChecked = AppPrefs.get(this).getLong(AppPrefs.KEY_TUNNEL_LAST_CHECK, 0L);
            int tunnelCode = AppPrefs.get(this).getInt(AppPrefs.KEY_TUNNEL_HTTP_CODE, 0);
            String tunnelError = AppPrefs.get(this).getString(AppPrefs.KEY_TUNNEL_LAST_ERROR, "");
            if (tunnelUrl == null || tunnelUrl.trim().isEmpty()) {
                tunnelStatus.setText("TUNNEL PUBLIK BELUM DIATUR.");
                tunnelStatus.setTextColor(MUTED);
            } else if (!active || tunnelChecked == 0L) {
                tunnelStatus.setText("TUNNEL PUBLIK BELUM DIPERIKSA.");
                tunnelStatus.setTextColor(MUTED);
            } else if (System.currentTimeMillis() - tunnelChecked > 45_000L) {
                tunnelStatus.setText("! STATUS TUNNEL KEDALUWARSA\nBelum ada respons baru dalam 45 detik.");
                tunnelStatus.setTextColor(Color.rgb(176, 105, 28));
            } else if (tunnelOnline) {
                tunnelStatus.setText("● TUNNEL ONLINE • HTTP " + tunnelCode + "\n" + tunnelUrl);
                tunnelStatus.setTextColor(GREEN);
            } else {
                tunnelStatus.setText("● TUNNEL OFFLINE / TIDAK MERESPONS\n"
                        + (tunnelError == null ? "Tidak ada detail" : tunnelError));
                tunnelStatus.setTextColor(Color.rgb(176, 75, 56));
            }
        }
    }

    private void updateSelectionSummary() {
        if (selectedSummary == null) return;
        int installedSelected = 0;
        for (LaunchableApp app : apps) if (selected.contains(app.packageName)) installedSelected++;
        selectedSummary.setText(installedSelected == 0
                ? "Belum ada aplikasi yang dipilih."
                : installedSelected + " aplikasi dipilih. Tombol BUKA menjalankan aplikasi; SETELAN membuka halaman pengaturan Android aplikasi tersebut.");
    }

    private CheckBox checkBox(String label, boolean bold) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextSize(13);
        box.setTextColor(INK);
        box.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        box.setPadding(0, dp(7), 0, dp(7));
        return box;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setPadding(dp(12), dp(9), dp(12), dp(9));
        button.setBackground(roundRect(primary ? GREEN : PALE, 12, Color.TRANSPARENT, 0));
        button.setMinHeight(dp(52));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(17), dp(16), dp(17));
        card.setBackground(roundRect(Color.WHITE, 16, Color.rgb(231, 238, 234), 1));
        return card;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (strokeWidth > 0) shape.setStroke(dp(strokeWidth), strokeColor);
        return shape;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class LaunchableApp {
        final String packageName;
        final String label;
        LaunchableApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }
}
