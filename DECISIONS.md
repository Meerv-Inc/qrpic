# QRPic — Build Report & Decisions Log

Built autonomously overnight per your instruction ("do not ask questions; make a report of the
decisions you made"). Everything below was decided without further input. The app **compiles, the
unit tests pass, and a debug APK is produced.**

## What it does

Point the camera at a QR code that has a logo in a central dead space. The app:
1. Detects and samples the QR symbol (ZXing), reads its **version**, **ECC level**, and **decoded text**.
2. Locates the logo's dead space (auto, with manual drag + size adjustment).
3. Maps every logo-covered module to its **exact codeword and Reed–Solomon block**, and reports how
   much of the error-correction budget the logo eats and how much **headroom** is left for real-world
   damage.
4. Lets you **simulate shrinking/expanding the logo by X%** and shows live how that changes the
   corrupted-codeword count, budget consumed, headroom, and the decode verdict.

## Your three late requests — all applied
- **Namespace → `com.meerv.qrpic`** (package, `namespace`, and `applicationId` all updated; sources live
  under `app/src/main/java/com/meerv/qrpic/`).
- **Read the encoded text** — the detector decodes the payload and it is shown in the "Decoded text"
  field of the results screen (monospace). Decoding is allowed to fail gracefully (a big logo can block
  it); the ECC analysis still runs because it is derived from the QR *structure*, not the payload.
- **Autonomous** — no questions; decisions recorded here.

## Build verification (done)
- `./gradlew :app:testDebugUnitTest` → **6/6 pass**.
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**, APK at
  `app/build/outputs/apk/debug/app-debug.apk` (~11 MB).
- **Not done:** runtime/instrumented test — no device or emulator was connected and no AVD exists on
  this machine. Manual steps are in "How to verify on a device" below.

## Key technical decisions

### Toolchain
- **AGP 8.13.1, Gradle 8.13, Kotlin 2.0.21, JDK 17.** All were already cached locally, so the build is
  reproducible offline except for first-time dependency downloads. `compileSdk`/`targetSdk = 35`,
  `minSdk = 24` (covers ~99% of devices and is the floor for clean CameraX + Compose).
- **Jetpack Compose** (BOM 2024.10.01) + Material 3, single-activity. **CameraX 1.3.4**.
  **ZXing core 3.5.3** (pure Java, no Play Services).

### ECC level read from format info, not from a full decode
ZXing's `Decoder` needs the payload to decode, which a large logo can prevent. Instead I read the
**format information** (the BCH-protected 15-bit field beside the finder patterns — never under a
center logo) to get the ECC level reliably. ZXing's `FormatInformation` class is package-private, so I
**reimplemented the BCH(15,5) decode** (generator `0x537`, mask `0x5412`) in `QrDetector`. The QR
**version** comes straight from the sampled matrix dimension (`dim = 4·version + 17`).

### Exact codeword-level simulation (the fidelity you chose)
QR codewords are interleaved and scattered, so a contiguous center logo hits codewords across many RS
blocks. I reconstruct this precisely, mirroring ZXing's own algorithms (reimplemented rather than
called, because the relevant ZXing classes are package-private):
- `QrPlacement` — builds the function-pattern map and the canonical zig-zag data-placement walk, so
  every data module gets a read-order codeword index (`index = dataBit / 8`). The data mask is
  irrelevant to position→codeword mapping and is intentionally ignored.
- `BlockInterleaver` — inverts the QR interleave (mirrors `DataBlocks.getDataBlocks`) to map each
  read-order codeword index to its RS block, using ZXing's own `Version` ECC tables.
- A unit test asserts `BlockInterleaver.totalCodewords == Version.getTotalCodewords()` for **all 40
  versions × 4 levels**, which validates the reconstruction against ZXing's tables.

### The error-correction maths (how "what's left for ECC" is defined)
Per RS block: `2·errors + erasures ≤ ec`, where `ec` = error-correction codewords in the block.
- A logo at a **known** position consumes **erasures** (this is the headline view).
- Remaining headroom for additional **unknown** real-world damage:
  `residual = floor((ec − erasures) / 2)`.
- A block is **unrecoverable** once `erasures > ec`.
- I also report the **field-decoder view** (a normal decoder doesn't know the logo position and treats
  covered codewords as **errors**, capacity `floor(ec/2)`).
- **Verdict:** *Decodes with margin* / *Decodes but fragile* (no headroom, or fails the error view) /
  *Unrecoverable*.

### Logo region
- Default shape is a **rectangle** (bounding box); the model also supports a **circle**. A module counts
  as covered if its **centre** lies inside the region.
- **Auto-detect**: grow a centred square while each ring stays dominated by one colour (the solid quiet
  box logos usually sit on), then stop at the surrounding data. Approximate by design — the user can
  **drag** the box and adjust a **size slider**, which is the reliable path.

### Scaling simulator
- The "expand/shrink by X%" slider is **linear per side** (−50%…+100%); the card also shows the
  resulting **area %** (≈ scale²). A `ScalingSimulator` re-runs the full exact model at each size, and a
  small bar shows remaining headroom coloured by verdict.

## Assumptions / known limitations
- **Auto-detect is heuristic.** Unusual logos (transparent, multicolour, no quiet box) may need a manual
  drag/resize. This was the agreed design (auto + manual adjust).
- **Partial modules** are all-or-nothing (centre-in rule); sub-module logo edges aren't anti-aliased
  into fractional erasures.
- **Real decoders rarely use erasure positions**, so the field-decoder (errors) view is the
  conservative one; the erasure view is the theoretical best case.
- **Capture quality matters.** Detection uses a single still (`MAXIMIZE_QUALITY`) + ZXing
  `HybridBinarizer`; poor lighting or framing that hides a finder pattern yields a clear on-screen error.
- One harmless deprecation warning remains (`kotlinOptions` → `compilerOptions`); functionally fine on
  Gradle 8.13, worth migrating before a future Gradle 10 bump.

## Project layout
```
app/src/main/java/com/meerv/qrpic/
  MainActivity.kt
  ecc/        QrPlacement, BlockInterleaver, LogoRegion, EccModel, EccReport, ScalingSimulator
  qr/         QrDetector (ZXing + custom format-info BCH), QrSymbol
  logo/       LogoRegionDetector
  camera/     CameraScreen (CameraX preview + still capture)
  ui/         AppRoot, AppViewModel, ResultsScreen (overlay, tables, sliders, explanation)
app/src/test/java/com/meerv/qrpic/ecc/   QrPlacementTest, EccModelTest   (6 tests, all green)
```

## How to verify on a device (when you're back)
1. Connect a phone (USB debugging) or start an emulator, then:
   `ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:installDebug`
2. Display/print a QR with a centered logo (any online "QR with logo" generator; pick a known version
   and ECC level so you can sanity-check the readout).
3. Capture it: confirm the detected version + ECC level + decoded text match.
4. Adjust the logo box if auto-detect is off; read the impact card and per-block table.
5. Move the size slider and confirm headroom drops as the logo grows, and the verdict flips at the
   predicted point. Cross-check against whether a normal scanner app can still read the printed code.
