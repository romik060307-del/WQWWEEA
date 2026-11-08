# ASERVER 1.5.1 — Android Server Companion

ASERVER is a standalone Android app for keeping **its own** background service active as consistently as Android permits, launching user-selected server apps, and checking local/public HTTP endpoints.

## What this project changes

This ZIP contains **ASERVER only**. It does not include, patch, rebuild, or replace any other server application.

## Fitur versi 1.5.0

- Foreground service dengan notifikasi persisten dan aksi `Cek sekarang` / `Hentikan`.
- `START_STICKY` agar Android dapat mencoba membuat ulang service bila proses dihentikan sistem.
- Partial CPU wakelock selama service aktif.
- Wi-Fi lock opsional agar koneksi Wi-Fi lebih terjaga saat layar mati.
- Pilih satu atau beberapa aplikasi yang terpasang, buka aplikasi pilihan, dan buka halaman Info Aplikasi Android untuk mengatur baterai/background aplikasi tersebut.
- Pemantauan HTTP lokal dan URL publik secara terpisah setiap 15 detik.
- Hanya respons HTTP 2xx yang dihitung ONLINE.
- Hasil monitor yang tidak diperbarui selama lebih dari 45 detik ditandai KEDALUWARSA, bukan tetap ditampilkan sebagai ONLINE.
- Pengaturan pengecualian optimasi baterai untuk ASERVER.
- Opsi mencoba mengaktifkan ASERVER setelah perangkat selesai boot atau setelah APK ASERVER diperbarui.

## Cara menggunakan

1. Instal APK ASERVER dan buka sekali.
2. Pilih aplikasi server yang ingin kamu gunakan pada daftar aplikasi.
3. Tekan `IZINKAN ASERVER TANPA OPTIMASI BATERAI` dan setujui dialog Android jika tersedia.
4. Pada baris setiap aplikasi target, buka `SETELAN` lalu atur penggunaan baterai/background ke opsi paling longgar yang tersedia di HP.
5. Isi URL lokal server pada bagian monitor. Alamat awal `http://127.0.0.1:8080/` adalah contoh untuk server pada port 8080. Ubah host, port, dan path sesuai server yang kamu pakai.
6. Isi URL publik HTTPS pada kolom tunnel jika ingin memeriksa akses publik. Kolom ini opsional.
7. Tekan `SIMPAN KEDUA ALAMAT MONITOR`, lalu `AKTIFKAN PENJAGAAN`.
8. Tekan `BUKA APLIKASI SERVER DIPILIH` untuk membuka aplikasi target dan mulai servernya dari aplikasi itu.

## Arti status

- `LAYANAN ASERVER MERESPONS`: service ASERVER masih memperbarui heartbeat internal.
- `SERVER ONLINE`: URL lokal yang dimasukkan mengembalikan HTTP 2xx pada pemeriksaan terakhir.
- `SERVER OFFLINE`: URL lokal gagal, timeout, atau mengembalikan status selain 2xx.
- `STATUS ... KEDALUWARSA`: tidak ada hasil pemeriksaan baru selama lebih dari 45 detik.
- `TUNNEL ONLINE/OFFLINE`: hasil pemeriksaan URL publik yang dimasukkan. Status ini terpisah dari server lokal.

## Batas teknis Android — penting

ASERVER hanya bisa menjaga proses dan service **ASERVER sendiri**. Pemilih aplikasi berfungsi untuk menyimpan pilihan, membuka aplikasi, dan membuka halaman pengaturan Android aplikasi tersebut. Android tidak menyediakan izin normal bagi aplikasi pihak ketiga untuk mengunci proses aplikasi lain, membaca seluruh status proses aplikasi lain secara bebas, atau memaksa aplikasi lain hidup terus-menerus.

Karena itu, ASERVER tidak dapat menjamin aplikasi target akan otomatis hidup kembali bila sistem atau pengelola daya pabrikan mematikannya. Untuk perlindungan paling kuat, aplikasi server target harus memiliki foreground service dan mekanisme pemulihannya sendiri. Source aplikasi target tidak diubah oleh proyek ini.

`START_STICKY`, boot receiver, wakelock, Wi-Fi lock, dan pengecualian baterai meningkatkan peluang ASERVER tetap aktif; semuanya tetap tunduk pada Android dan kebijakan pabrikan. `Paksa berhenti`, restart/kerusakan sistem, aplikasi dihapus, baterai habis, panas berlebih, atau pembatasan pabrikan dapat menghentikannya. Jangan menganggap notifikasi saja sebagai bukti uptime. Uji dengan layar mati dan periksa waktu pemeriksaan terakhir.

Wakelock dan Wi-Fi lock menggunakan baterai dan dapat meningkatkan suhu. Gunakan sumber daya stabil dan ventilasi yang baik; hentikan penjagaan bila perangkat terlalu panas.

## Build APK melalui GitHub Actions

1. Buat repository GitHub baru.
2. Unggah **seluruh isi folder ASERVER ini** ke root repository, termasuk folder `.github`.
3. Buka `Actions` → `Build ASERVER APK` → `Run workflow`.
4. Setelah workflow selesai, unduh artifact `ASERVER-debug-APK-v1.5.1`.
5. Ekstrak artifact untuk mendapatkan `app-debug.apk`.

Workflow memakai `android-actions/setup-android@v4` dan meminta hanya paket SDK yang diperlukan. Langkah Locate Android Gradle project juga menerima proyek yang diekstrak, berada di subfolder, atau ZIP source yang diunggah ke root repository. Ini menghindari permintaan paket legacy `tools` yang sudah dihapus dari repository Android SDK.

Jika APK baru ditandatangani dengan debug key GitHub runner yang berbeda, Android mungkin menolak pembaruan di atas instalasi lama. Simpan data penting terlebih dahulu; bila diminta, hapus ASERVER versi lama lalu instal APK baru dan atur pilihannya kembali.

## Build lokal

Buka folder ini dengan Android Studio, JDK 17, dan Android SDK Platform 35. Jalankan:

```bash
gradle assembleDebug
```

APK berada pada:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pemeriksaan source

Dari root folder ASERVER jalankan:

```bash
node tools/source-verify.mjs
```

Pemeriksaan statis tidak menggantikan kompilasi Gradle atau uji nyata pada HP.

## Detail proyek

- Package: `id.rk.aserver`
- Versi: `1.5.0` (`versionCode 7`)
- Minimum Android: 6.0 / API 23
- Compile / target SDK: 35
- Java: 17
- Foreground service type Android 14+: `specialUse`


## Vivo / Funtouch OS setup
For ASERVER, OHLI Social Server and RKM Tunnel Lite separately: allow background activity/background power use, enable Autostart where available in iManager, disable battery restriction for each app, and lock each app in Recents if that option exists. Exact labels vary by Funtouch OS version. Start OHLI and the tunnel manually after granting these permissions. ASERVER can monitor endpoints but Android does not let a companion app force-launch another app's foreground service from the background.
