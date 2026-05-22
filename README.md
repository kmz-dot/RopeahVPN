# RopeahVPN

Android VPN client built on Xray-core. Greenfield project, source-built, designed to comply with Play Store / Play Protect policies.

## Status

Functional. Xray-core (`libv2ray.aar`) and hev-socks5-tunnel (`libhev-socks5-tunnel.so` per ABI) are wired up. Tap connect and the app starts a real VPN tunnel to whichever server is active.

What's missing before this is shippable: a privacy policy URL, release signing, app icon, and a real Xray config for any servers you want to ship (the parser accepts `vmess:// vless:// trojan:// ss://` URIs).

## Build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- JDK 21
- Android SDK with platform 35 and build-tools 35.0.0
- Point Gradle at your SDK by creating `local.properties` with `sdk.dir=/path/to/Android/Sdk`
- Gradle is bundled via the wrapper — do not install separately

### Release signing

`app/build.gradle.kts` reads release-signing config from a `keystore.properties` file at the repo root (gitignored). To produce a signed release build, create one with:

```
storeFile=your-release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Debug builds don't need this.

## Rebuilding the native libraries

`app/libs/libv2ray.aar` and `app/src/main/jniLibs/*/libhev-socks5-tunnel.so` are committed binaries. They were built from source as follows. You only need to rebuild them if you want to upgrade Xray-core or hev-socks5-tunnel.

### `libv2ray.aar` — Xray-core via gomobile

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/28.2.13676358
export PATH=/path/to/go-toolchain/bin:$HOME/go/bin:$PATH

go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

git clone https://github.com/2dust/AndroidLibXrayLite
cd AndroidLibXrayLite
go mod tidy
gomobile bind -v -target=android/arm64,android/arm,android/amd64 -androidapi 24 -ldflags='-s -w' ./
cp libv2ray.aar /path/to/RopeahVPN/app/libs/
```

The exported Java surface lives in package `libv2ray` (classes `Libv2ray`, `CoreController`, `CoreCallbackHandler`, `ProcessFinder`). See `XrayController.kt` for how it's called.

### `libhev-socks5-tunnel.so` — hev-socks5-tunnel via ndk-build

```bash
mkdir hev-build && cd hev-build
git clone --recursive https://github.com/heiher/hev-socks5-tunnel jni
# Lower APP_PLATFORM to match our minSdk and pin JNI class name to ours:
sed -i 's/APP_PLATFORM := android-29/APP_PLATFORM := android-24/' jni/Application.mk
sed -i 's|APP_CFLAGS := -O3|APP_CFLAGS := -O3 -DPKGNAME=com/ropeah/vpn/vpn -DCLSNAME=Tun2SocksNative|' jni/Application.mk

$ANDROID_NDK_HOME/ndk-build -j$(nproc)
# Outputs: libs/{arm64-v8a,armeabi-v7a,x86_64}/libhev-socks5-tunnel.so
```

Then copy the .so files into `app/src/main/jniLibs/<ABI>/`.

The native methods are bound to `com.ropeah.vpn.vpn.Tun2SocksNative` (see `Tun2SocksNative.kt`). The `PKGNAME`/`CLSNAME` defines above pin that registration — if you rename the Kotlin class or move it to a different package, you must rebuild the .so.

## Play Store compliance checklist

Before publishing, you still need to:

- [ ] Host a privacy policy at a public URL (Play Store requires this for any app declaring `VpnService`).
- [ ] Add real release signing config (currently debug-signed).
- [ ] Replace placeholder icon in `res/mipmap-anydpi-v26/ic_launcher.xml`.
- [ ] Fill in `app_name`, descriptions, and screenshots for the Play listing.
- [ ] Test on a real device with `targetSdk = 35` to confirm foreground-service-type `systemExempted` works.

## Project structure

```
kotlin/com/ropeah/vpn/
  RopeahApp.kt            Application class
  MainActivity.kt         single-activity Compose host
  ui/                     Compose screens + theme
  vpn/                    VpnService + Xray/tun2socks wrappers
  data/                   server config model + DataStore repo + URI parser
  util/                   foreground notification helper
```

## License

GPL-3.0. See [LICENSE](LICENSE). This project depends on Xray-core (via AndroidLibXrayLite) and inherits its GPL-3.0 obligations.
