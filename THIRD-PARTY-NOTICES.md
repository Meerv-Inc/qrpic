# Third-Party Notices

QRPic is released under the [MIT License](LICENSE). It builds on the following
open-source components, each under its own license. We gratefully acknowledge
their authors.

## QR detection & error-correction tables — ZXing ("Zebra Crossing")

- Project: https://github.com/zxing/zxing
- License: Apache License 2.0
- Copyright (c) ZXing authors

QRPic uses ZXing's `core` library to detect and sample QR symbols from the
camera image and for the canonical QR `Version` / error-correction block tables
(ISO/IEC 18004). QRPic's error-correction analyzer (module placement walk,
codeword interleaving inversion, and Reed–Solomon budget maths in the
`com.meerv.qrpic.ecc` package) is original work that *mirrors* the algorithms
described by the QR standard and implemented in ZXing, re-expressed in Kotlin so
each module can be attributed to its exact codeword and block.

A copy of the Apache License 2.0 is available at:
https://www.apache.org/licenses/LICENSE-2.0

## Android platform & Jetpack libraries

- AndroidX Core, Activity, Lifecycle, and Jetpack Compose (UI, Foundation,
  Material 3) — Apache License 2.0 — https://developer.android.com/jetpack
- AndroidX CameraX (camera-core, camera-camera2, camera-lifecycle,
  camera-view) — Apache License 2.0
- Kotlin standard library & Kotlin Coroutines — Apache License 2.0 —
  https://github.com/JetBrains/kotlin

All of the above are © their respective authors and licensed under the
Apache License 2.0.
