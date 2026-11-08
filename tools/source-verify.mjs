import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
let passed = 0;
function check(condition, message) {
  if (!condition) throw new Error(`FAIL: ${message}`);
  passed += 1;
  console.log(`PASS ${passed}: ${message}`);
}

const gradle = read('app/build.gradle');
const manifest = read('app/src/main/AndroidManifest.xml');
const service = read('app/src/main/java/id/rk/aserver/GuardService.java');
const activity = read('app/src/main/java/id/rk/aserver/MainActivity.java');
const prefs = read('app/src/main/java/id/rk/aserver/AppPrefs.java');
const boot = read('app/src/main/java/id/rk/aserver/BootReceiver.java');
const workflow = read('.github/workflows/android.yml');

check(gradle.includes("versionName '1.5.1'") && gradle.includes('versionCode 7'), 'identitas versi ASERVER 1.5.1 / versionCode 7');
check(manifest.includes('android:foregroundServiceType="specialUse"'), 'foreground service specialUse dideklarasikan');
check(manifest.includes('FOREGROUND_SERVICE_SPECIAL_USE') && manifest.includes('android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE'), 'izin dan alasan specialUse tersedia');
check(manifest.includes('android.permission.WAKE_LOCK') && service.includes('PARTIAL_WAKE_LOCK'), 'CPU wakelock digunakan oleh service');
check(manifest.includes('android.permission.ACCESS_WIFI_STATE') && service.includes('createWifiLock'), 'opsi Wi-Fi lock tersedia');
check(service.includes('START_STICKY') && boot.includes('ACTION_BOOT_COMPLETED'), 'service sticky dan pemulihan boot tersedia');
check(activity.includes('ASERVER tetap bisa menjalankan penjagaannya sendiri tanpa memilih aplikasi lain') && !activity.includes('Pilih minimal satu aplikasi server dahulu'), 'penjagaan ASERVER dapat dimulai tanpa aplikasi target');
check(service.includes('ACTION_CHECK_NOW') && service.includes('ACTION_STOP'), 'aksi notifikasi cek dan hentikan tersedia');
check(service.includes('CHECK_INTERVAL_MS = 15_000L'), 'health check dijalankan tiap 15 detik');
check(service.includes('code >= 200 && code < 300'), 'hanya respons HTTP 2xx dihitung online');
check(service.includes('import android.content.SharedPreferences;') && service.includes('SharedPreferences prefs = null;') && !service.includes('AppPrefs prefs = null;'), 'health monitor memakai tipe SharedPreferences yang benar');
check(service.includes('KEY_TUNNEL_ALIVE') && service.includes('KEY_SERVER_ALIVE'), 'status lokal dan tunnel disimpan terpisah');
check(activity.includes('KEY_TUNNEL_URL') && activity.includes('KEY_MONITOR_URL'), 'UI menyediakan dua alamat monitor');
check(activity.includes('isIgnoringBatteryOptimizations') && activity.includes('ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS'), 'status dan permintaan pengecualian baterai nyata');
check(prefs.includes('http://127.0.0.1:8080/'), 'endpoint default memakai alamat port lokal yang dapat diedit');
check(activity.includes('KEDALUWARSA') && service.includes('KEDALUWARSA'), 'hasil monitor lama ditandai kedaluwarsa di layar dan notifikasi');
check(activity.includes('aplikasi target secara opsional'), 'pemilih aplikasi target bersifat opsional dan generik');
check(workflow.includes('assembleDebug') && workflow.includes('actions/upload-artifact@v4'), 'workflow membangun dan mengunggah APK');

console.log(`\nLulus ${passed} pemeriksaan statis ASERVER 1.5.1. Build Gradle penuh dan tes perangkat nyata tetap wajib.`);
