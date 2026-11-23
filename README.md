# ImReal

Satu momen per hari: kamera belakang dan depan sekaligus, tanpa login. Data hanya di perangkat.

## Build tanpa Android Studio (GitHub Actions)
1. Buat repo baru di GitHub, lalu upload seluruh isi folder ini (termasuk folder `.github`).
   Paling mudah dengan git atau GitHub Desktop, karena folder berawalan titik sering tersembunyi di upload web.
2. Buka tab **Actions**. Workflow *Build APK* jalan otomatis setiap push ke `main` (atau klik *Run workflow*).
3. Setelah hijau, buka run tersebut, unduh artifact **ImReal-apk**, ekstrak, lalu pasang `ImReal.apk` di HP
   (izinkan "Install unknown apps").
4. Untuk rilis: `git tag v1.0 && git push --tags`, APK otomatis terlampir di halaman Releases.

APK ditandatangani dengan `app/debug.keystore` yang ada di repo, jadi versi baru bisa menimpa versi lama tanpa uninstall.
Untuk penggunaan pribadi ini cukup. Jangan pakai keystore ini untuk publikasi ke Play Store.

## Fitur
- Beranda: slider 10 momen terakhir, bagikan, mood dan catatan
- Kamera: foto atau video (3 detik belakang lalu 3 detik depan), Simpan atau Ulangi
- Kalender bulanan, detail, bagikan sebagai satu gambar gabungan, hapus
- Pengingat harian di jam acak 09.00–21.00 (tidak muncul bila momen hari ini sudah ada)
- Kunci aplikasi dengan sidik jari atau PIN HP (terkunci lagi setelah 30 detik di latar belakang)
- Backup dan restore ke file zip
- Widget layar utama: momen hari ini (saat kunci aktif widget tidak menampilkan foto)

## Struktur kode (app/src/main/java/app/imreal)
`Data.kt` penyimpanan dan backup, `Camera.kt` CameraX, `Home.kt`, `Calendar.kt`, `Components.kt`,
`Share.kt` gambar gabungan, `Reminder.kt` WorkManager, `Lock.kt` BiometricPrompt, `Widget.kt`, `Settings.kt`, `MainActivity.kt`
