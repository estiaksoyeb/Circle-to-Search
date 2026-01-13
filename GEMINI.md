# Circle To Search - Project Context

## Project Overview

**Circle To Search** is an open-source Android application that replicates the "Circle to Search" feature found on Pixel/Samsung devices, making it available for any Android device running Android 10+ (API 29+).

**Core Functionality:**
1.  **Trigger:** Activated via Accessibility Service gestures (e.g., long press on a handle), Quick Settings tile, or Assistant/Voice interaction.
2.  **Capture:** Takes a screenshot of the current screen.
3.  **Select:** Opens an overlay (`OverlayActivity`) where the user can draw a circle or scribble over the object of interest.
4.  **Search:** Crops the selected area and performs a reverse image search using multiple engines (Google Lens, Bing, Yandex, TinEye).

## Tech Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose (Material 3)
*   **Android APIs:**
    *   `AccessibilityService` (Gesture detection, global actions)
    *   `MediaProjection` / `takeScreenshot` (Screen capture)
    *   `QuickSettingsService` (QS Tile)
*   **Build System:** Gradle (Kotlin DSL)
*   **Minimum SDK:** 29 (Android 10)
*   **Target SDK:** 36 (Android 16 Preview/Beta)

## Architecture & Key Components

### 1. Services (Triggers)
The app relies heavily on background services to listen for user input overlaying other apps.
*   **`CircleToSearchAccessibilityService.kt`:** The core service. It manages floating overlay segments (triggers) on the screen edges. It handles gesture detection (tap, long press, swipe) to trigger the screenshot and other actions (Flashlight, Home, Split Screen, etc.).
*   **`CircleToSearchTileService.kt`:** Manages the Quick Settings tile for easy toggling.
*   **`AssistSessionService.kt` / `VoiceService`:** Allows the app to be set as the default Digital Assistant app.

### 2. UI Layers
*   **`OverlayActivity.kt`:** The transparent activity launched after a screenshot. It displays the screenshot and allows the user to draw/crop the selection.
*   **`MainActivity.kt`:** Configuration and settings UI (likely Jetpack Compose).
*   **`WebViewActivity.kt`** (or similar): Displays the search results from the provider.

### 3. Data Flow (Image Search)
Ref: `docs/image_search.md`
1.  **Capture:** `AccessibilityService` calls `takeScreenshot`.
2.  **Storage:** Result is stored in an in-memory `BitmapRepository`.
3.  **Crop:** User selection in `OverlayActivity` defines the crop rect.
4.  **Upload:** `ImageSearchUploader` (custom logic) uploads the cropped bitmap to the selected engine's endpoint (Google, Bing, Yandex, TinEye).
5.  **Result:** The response (redirect URL) is loaded in a WebView.

## Development & Build

### Prerequisites
*   JDK 17+ (Project uses Java 11 compatibility but AGP 8+ requires newer JDKs).
*   Android SDK 35/36.

### Build Commands
*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
*   **Run Tests:**
    ```bash
    ./gradlew testDebugUnitTest
    ```

### Key Directories
*   `app/src/main/java/com/akslabs/circletosearch/`: Main source code.
    *   `data/`: Data repositories and configuration managers.
    *   `ui/`: Compose UI components and themes.
    *   `utils/`: Helper classes.
*   `docs/`: Detailed feature documentation (e.g., `image_search.md`).

## Notes for Contributors
*   **Permissions:** The app requires sensitive permissions like `BIND_ACCESSIBILITY_SERVICE` and `QUERY_ALL_PACKAGES`.
*   **Style:** Follows standard Kotlin coding conventions and Jetpack Compose best practices.
