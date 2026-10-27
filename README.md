# Gemini Dual Web for Android

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg?logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An open-source Android client that embeds the official [Gemini](https://gemini.google.com) web interface while running **two completely isolated Google accounts side-by-side**. 

No account switching, no relogging, and no losing chat states.

---

## Why Gemini Dual Web?

The official Gemini app restricts you to one active Google session at a time, requiring menu digging to switch between personal and work/school accounts. Running multiple accounts in standard mobile browsers often causes session collisions and accidental refreshes.

**Gemini Dual Web** solves this by leveraging modern Android WebKit multi-profile sandboxing, keeping both sessions live in memory with dedicated power-user enhancements.

---

## Features

### Core Multi-Account Architecture
* **True Session Isolation:** Uses `androidx.webkit.ProfileStore` (WebKit multi-profile) to ensure Account 1 and Account 2 maintain distinct cookie jars, cache, and LocalStorage partitions.
* **Instant In-Memory Switching:** Both WebViews remain mounted in memory (`View.VISIBLE` / `View.GONE`). Switching tabs via the bottom bar is instantaneous—no reloading or losing draft prompts.
* **Smart Back Navigation:** Hardware back gestures navigate Gemini's web history stack rather than abruptly exiting the app.

### Native & Web Quality-of-Life
* **App Promo Suppression:** Auto-dismisses the persistent *"Do more with the Gemini app"* bottom sheet and top banner promotions before they interrupt the screen.
* **No Scroll Refresh Conflicts:** Pull-to-refresh is safely decoupled from the chat window, preventing accidental page reloads when scrolling up through long conversation histories.
* **Draggable Floating Action Button (FAB):** A freely movable action button that saves its $(X, Y)$ screen coordinates across app restarts.
* **Prompt Vault:** Tap the FAB to open a bottom sheet dialog with reusable system prompt templates that paste directly into Gemini's active input box.
* **Hardware Support:** Full camera and file picker access for uploading images/documents, plus microphone permission support for voice dictation.

### Customization & Settings
* **AMOLED Pitch Black Mode:** Overrides default backgrounds to pure `#000000` for OLED battery savings.
* **Accent Color Presets:** Customize the UI accent color (Gemini Blue, Cyber Emerald, Pure White).
* **FAB Size & Visibility Controls:** Switch between Mini, Normal, or Large button sizing, or disable the FAB entirely.
* **Remote Web Debugging:** Inspect the mobile WebViews live via PC using Chrome DevTools (`chrome://inspect`) over USB.

---

## Technical Details

| Component | Implementation |
|---|---|
| **Language** | Kotlin |
| **Minimum SDK** | Android 8.0 (API 26) / Recommended API 28+ |
| **Session Engine** | `androidx.webkit:webkit:1.12.0+` (`ProfileStore`) |
| **User-Agent** | Sanitized Chrome Mobile (strips `; wv` to avoid Google `403: disallowed_useragent`) |
| **CI/CD** | Automated headless compilation via GitHub Actions |

---

## Download & Installation

### Option 1: Download Pre-built APK (GitHub Actions)
1. Navigate to the **[Actions](../../actions)** tab in this repository.
2. Click on the latest successful build run on the `main` branch.
3. Scroll down to the **Artifacts** section at the bottom.
4. Download the `GeminiDualWeb-Debug-APK` zip archive, extract `app-debug.apk`, and install it on your device.

### Option 2: Build from Source
To compile the APK locally using the Gradle wrapper:

```bash
# Clone the repository
git clone [https://github.com/](https://github.com/)<YOUR-USERNAME>/gemini-dual-web.git
cd gemini-dual-web

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
