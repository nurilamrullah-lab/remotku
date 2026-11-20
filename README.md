# Remotku

Aplikasi Universal Remote Control Android (Kotlin, Jetpack Compose, Material 3, tema terang) dengan dukungan
remote Android TV via WiFi/LAN dan IR blaster, plus haptic feedback di setiap tombol.

## Fitur

### Smart TV (WiFi)
- **Auto-scan**: mDNS/DNS-SD (`_androidtvremote2._tcp`, `_googlecast._tcp`) + sapuan subnet (port 6466, 6467, 5555, 8009).
- **Input IP manual** untuk perangkat di subnet lain, dengan pilihan mode koneksi.
- **Android TV Remote v2** (protokol aplikasi Google TV): pairing kode 6 digit di layar TV, sertifikat klien disimpan
  permanen sehingga pairing cukup sekali.
- **ADB over network (5555)** sebagai alternatif untuk Android Box dengan *Network debugging* aktif.
- Remote: D-Pad + OK, Back/Home/Menu, Power, Mute, Volume ±, Channel ±, Input, Setelan.
- **Keyboard**: kirim teks ke kolom pencarian TV (IME batch edit, fallback ke key event).
- **Touchpad/Mouse**: mode ATV Remote → gestur geser jadi navigasi D-Pad; mode ADB → mouse virtual nyata via
  `uinput` (pointer tampil di TV), fallback ke tap di posisi kursor.
- **Quick launcher**: YouTube, Netflix, Vidio, Prime Video, Play Store.

### Inframerah (IR)
- Alur seperti ZaZa Remote: **Tambah remote** → kategori (TV, STB/Satelit, AC, Proyektor, OTT-Box, DVD/Blu-ray,
  Kipas Angin, Robot Cleaner, Air Purifier/Humidifier, Audio/Soundbar) → **pilih merek** (pencarian merek & model,
  bagian *Popular brands*, daftar A–Z dengan indeks huruf yang bisa diketuk/diseret) → **wizard pencocokan**
  (kirim Power untuk kode 1/N, jawab Ya/Tidak) → remote tersimpan di halaman utama.
- `ConsumerIrManager` untuk HP ber-IR blaster.
- Encoder (semantik alamat/perintah sama dengan Flipper Zero): NEC, NEC Extended (perintah 16-bit), Samsung32,
  RC-5/RC-5X, RC-6, Sony SIRC 12/15/20, Kaseikyo (Panasonic dkk.), RCA, Pioneer, Sharp, serta sinyal RAW.
- AC stateful (suhu, mode Auto/Cool/Dry/Fan/Heat, kecepatan kipas, swing): Daikin, Panasonic, Sharp, Gree, LG, Aux.
  Merek AC lain memakai tombol hasil rekaman (Power, Suhu ±, Mode, Fan, Swing, …).

### Database IR
- `app/src/main/assets/ir/<kategori>.json` — ±720 merek unik (TV 207, STB 168, Audio 147, AC 108, Kipas 102,
  DVD 89, Proyektor 77, Purifier 28, OTT 21, Robot 16), ±2.000 set kode; tiap merek punya beberapa set kode yang
  diurutkan dari kode Power yang paling banyak dipakai.
- Sumber: [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (lisensi CC0, termasuk konversi IRDB) +
  set DVB-T2 lama Remotku. Dibangun ulang dengan:
  ```bash
  git clone --depth 1 https://github.com/Lucaslhm/Flipper-IRDB /tmp/irdb
  python3 tools/irdb/build_ir_db.py /tmp/irdb
  ```
- Merek lokal yang tidak ada di database publik (mis. Polytron, Coocaa, Akari, Matrix Apple, Luby, Tanaka, Miyako,
  Cosmos) memakai **set kode universal**: kode Power yang paling banyak dipakai lintas merek di kategori itu
  (ditandai *Universal* di wizard). Daftarnya ada di `LOCAL_BRANDS` pada skrip generator.

## Arsitektur

MVVM + intent (MVI-style) per layar, repository pattern, DI manual (`AppContainer`).

```
app/src/main/java/com/remotku/app/
├── core/          haptics, encoder/decoder protobuf minimal
├── data/
│   ├── local/     Room (perangkat TV tersimpan)
│   ├── tv/        discovery (NSD + subnet), atv (pairing + remote v2), adb, TvRemoteRepository
│   └── ir/        encoder protokol, encoder AC, katalog per kategori, remote tersimpan, transmitter
├── domain/        model TV & IR
└── ui/            theme, komponen (RemoteButton, DPad, Trackpad), main, tv, ir
```

## Build

Butuh JDK 17 dan Android SDK 36.

```bash
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # unit test + screenshot (app/build/outputs/roborazzi)
```

minSdk 26 · targetSdk 36 · compileSdk 36 · AGP 8.13 · Kotlin 2.2 · Compose BOM 2025.11
