# FManager

A modern, high-performance Android archive manager and file browser built to outperform legacy archivers with a native Material 3 interface, multi-threaded native compression engines, and full system path access.

## 🚀 Key Features Planned

* **Modern Material 3 UI:** Built entirely with Jetpack Compose, supporting fluid gestures, multi-tab browsing, and dark mode.
* **Native Multi-threaded Engine:** Powered by compiled C/C++ libraries (`libarchive`, `7-zip`, `libunrar`) via Android NDK for maximum extraction speeds.
* **Direct `/Android/data` Access:** Integrated **Shizuku API** support to navigate restricted system directories on Android 11+ without root.
* **In-Archive Virtual Mounting:** Preview images, text, and media inside archives without extracting the full container to disk.
* **Checksum & Recovery:** Integrity verification (MD5, SHA-256, BLAKE3) with PAR2 volume repair support.

## 🛠️ Architecture & Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Async Operations:** Kotlin Coroutines & Flow
* **Native Layer:** C/C++ via Android NDK (CMake)
* **Target SDK:** Android 14 (API 34) | **Min SDK:** Android 8.0 (API 26)

