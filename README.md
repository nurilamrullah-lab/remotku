# Remotku

Aplikasi Universal Remote Control Android (Kotlin, Jetpack Compose, Material 3 Dark AMOLED) dengan dukungan
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
- `ConsumerIrManager` untuk HP ber-IR blaster.
- Encoder: NEC / NEC Extended (38 kHz), Samsung32, RC-5, RC-6 (mode 0, toggle bit), Sony SIRC 12/15/20, Sharp.
- AC stateful (suhu, mode Auto/Cool/Dry/Fan/Heat, kecepatan kipas, swing): Daikin, Panasonic, Sharp, Gree, LG, Aux.
- Database profil di `app/src/main/assets/ir_profiles.json`:
  - TV: Polytron, Samsung, LG, TCL, Coocaa, Sharp, Xiaomi TV, Akari (+ Sony)
  - STB DVB-T2: Matrix Apple (Merah/Silver), Luby, Evercoss, Tanaka, Polytron PDV, Advance
  - AC: Daikin, Panasonic, Sharp, Gree, LG, Aux

> Kode Samsung, LG, Sony, dan Sharp Aquos berasal dari Flipper-IRDB. Merek lokal (STB DVB-T2, Polytron, TCL, Coocaa,
> Xiaomi, Akari) memakai set kode generik dengan beberapa varian ("Kode A/B") yang bisa dicoba bergantian; profil
> tersebut ditandai *kode generik* di aplikasi. Layout byte AC mengikuti format yang didokumentasikan IRremoteESP8266.

## Arsitektur

MVVM + intent (MVI-style) per layar, repository pattern, DI manual (`AppContainer`).

```
app/src/main/java/com/remotku/app/
├── core/          haptics, encoder/decoder protobuf minimal
├── data/
│   ├── local/     Room (perangkat TV tersimpan)
│   ├── tv/        discovery (NSD + subnet), atv (pairing + remote v2), adb, TvRemoteRepository
│   └── ir/        encoder protokol, encoder AC, database profil, transmitter
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
