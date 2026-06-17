# QRPic — QR Logo Error-Correction Analyzer

An Android app (Kotlin / Jetpack Compose) that photographs a QR code with a logo
in its central "dead space" and computes **exactly how much of the QR code's
Reed–Solomon error-correction budget the logo consumes** — and how much headroom
is left for real-world damage (dirt, glare, print defects, bad scans).

It then lets you **simulate shrinking or expanding the logo by X%** and shows
live how that moves the code between "decodes with margin", "fragile", and
"unrecoverable".

## What it does

1. **Detects & samples** the QR symbol from the camera (ZXing), reading its
   version, ECC level, and decoded text. The ECC level is read from the
   BCH-protected format information, so analysis works even when a large logo
   prevents the payload from decoding.
2. **Locates the logo's dead space** (auto-detect, with drag + size adjustment),
   overlaid on a straightened, module-aligned crop of the captured photo.
3. **Maps every covered module to its exact codeword and Reed–Solomon block**,
   reporting the budget consumed and the residual headroom per block.
4. **Simulates logo scaling** and explains the effect on what's left for ECC.

## The error-correction maths

Per block, Reed–Solomon satisfies `2·errors + erasures ≤ ec`, where `ec` is the
number of error-correction codewords in that block.

- A logo at a **known** position consumes **erasures**.
- Remaining headroom for additional **unknown** damage:
  `residual = floor((ec − erasures) / 2)`.
- A block is **unrecoverable** once `erasures > ec`.

QRPic reports both the erasure view (best case, position known) and the
field-decoder view (a normal scanner treats covered codewords as errors,
capacity `floor(ec/2)`).

## Build

```bash
# Debug
ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:assembleDebug

# Unit tests (validate the ECC engine against ZXing's version tables)
ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:testDebugUnitTest

# Release (requires keystore.properties + a keystore; see below)
ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:assembleRelease
```

A prebuilt signed release APK is included at
[`release/QRPic-v1.0-release.apk`](release/QRPic-v1.0-release.apk).

### Release signing

Release signing is configured only when a `keystore.properties` file exists at
the project root (it and the `*.jks` keystore are intentionally **not** checked
in — this repository is public). Create your own:

```bash
keytool -genkeypair -v -keystore qrpic-release.jks -alias qrpic \
  -keyalg RSA -keysize 2048 -validity 10000
```

```properties
# keystore.properties
storeFile=qrpic-release.jks
storePassword=…
keyAlias=qrpic
keyPassword=…
```

Without it, the project still builds debug and an unsigned release.

- **Stack:** Kotlin 2.0.21, AGP 8.13.1, Gradle 8.13, Jetpack Compose (Material 3),
  CameraX 1.3.4, ZXing core 3.5.3. minSdk 24 / compileSdk 35.

## License

QRPic is licensed under the [MIT License](LICENSE).
Third-party components and their licenses are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). The QR engine is
**ZXing** (Apache-2.0).
