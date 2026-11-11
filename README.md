# DocScanner

![Android CI](https://github.com/manushadilan/DocScanner/actions/workflows/gradle-publish.yml/badge.svg)

A CamScanner-style document scanner for Android, built with Kotlin and Jetpack Compose. Point the camera at a page and it detects the edges, flattens the perspective, cleans up the image and saves it, all on-device with no account or network needed. Export multi-page PDFs when you're done.

> **Status:** early-stage reference implementation. The core pipeline is complete, but it hasn't been broadly tested across devices yet. Issues and PRs are welcome.

## Features

- **Guided scan flow**: capture, adjust edges, enhance, save. Repeat with **Add page** for multi-page documents
- **Flexible crop editor**: 4×4 grid, free corner handles, edge handles that move a whole side, drag-to-move the entire shape, a magnifier loupe, plus Rotate, Auto-detect and Full page
- **Perspective correction** that flattens a skewed photo into a clean rectangle
- **Enhancement filters**: Magic Color (shadow removal), Grayscale, Black & White (adaptive threshold), plus brightness and contrast sliders
- **Grid page manager**: thumbnails with page numbers, reorder and delete
- **One-tap Save as PDF**: a large labelled button exports all pages with Android's native `PdfDocument`, saved through the system file picker (no storage permissions)
- Adaptive app icon with Android 13 themed-icon support

## Download

Every push builds an APK with GitHub Actions.

- **Latest build:** open the **Actions** tab, pick the newest successful run, and download the `DocScanner-debug-apk` artifact (unzip it to get the `.apk`).
- **Releases:** pushing a tag such as `v1.0.0` publishes a GitHub Release with the APK attached.

To install, copy the APK to your phone and open it. Android will ask you to allow installs from your browser or file manager. Note that this is a *debug-signed* build meant for testing.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Camera | CameraX 1.3.4 |
| Image processing | OpenCV 4.9.0 (Maven Central) |
| PDF | `android.graphics.pdf.PdfDocument` |
| SDK | minSdk 26, targetSdk 35 |
| Build | AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17 |

## Getting started

**Requirements:** Android Studio Ladybug (2024.2) or newer, JDK 17, and a physical device or emulator with a camera running Android 8.0+.

```bash
git clone https://github.com/<your-username>/DocScanner.git
cd DocScanner
```

Open the folder in Android Studio, let Gradle sync, and run the `app` configuration. Grant the camera permission when prompted.

Command-line build (needs Gradle 8.9+ or a generated wrapper):

```bash
gradle wrapper --gradle-version 8.9   # once, to generate ./gradlew
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
```

## CI / releases

The workflow in `.github/workflows/android.yml`:

| Trigger | What happens |
|---|---|
| Push to `main`/`master`, pull request, or manual run | Builds the debug APK and uploads it as a workflow artifact (kept 30 days) |
| Push a tag starting with `v` | Also creates a GitHub Release with the APK attached |

```bash
git tag v1.0.0 && git push origin v1.0.0
```

It installs Gradle 8.9 and JDK 17 itself, so no wrapper files are required. For a Play Store-ready signed release build, add a signing config and keystore secrets to the workflow.

## How it works

```
Pages ──► Capture ──► Crop ──► Enhance ──► Pages
 (grid)   (CameraX)   (OpenCV)  (filters)   (Add page / Save as PDF)
```

1. **Capture**: `CameraCapture` binds the CameraX preview and still capture, with a 3×3 grid to help you line up the page.
2. **Detect and crop**: `DocumentScanner.detectQuad()` finds the largest convex four-corner contour on a downscaled copy; `warp()` applies the perspective transform. You can then fine-tune the corners; if nothing is found, the whole frame is selected.
3. **Enhance**: `ImageFilters.apply()` runs the chosen filter. The preview uses a small copy; the full-resolution pass runs on save.
4. **Export**: `PdfExporter.export()` streams pages into a PDF one bitmap at a time to keep memory low.

## Project structure

```
.github/workflows/android.yml   # CI: build APK, publish releases
app/src/main/
├── AndroidManifest.xml
├── res/                         # Adaptive launcher icon (vector)
└── java/com/example/docscanner/
    ├── App.kt                   # Initializes OpenCV
    ├── MainActivity.kt          # Theme + NavHost
    ├── camera/CameraCapture.kt  # CameraX preview, grid, shutter
    ├── scanner/
    │   ├── DocumentScanner.kt   # Edge detection + perspective warp
    │   └── ImageFilters.kt      # Magic Color / Gray / B&W / brightness+contrast
    ├── ui/
    │   ├── Screens.kt           # Capture, Crop, Enhance and Pages screens
    │   └── CropOverlay.kt       # Grid, handles and loupe crop editor
    ├── data/DocumentViewModel.kt # Pages + in-progress scan state
    └── pdf/PdfExporter.kt       # Multi-page PDF writer
```

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Capture documents |

PDFs are saved via the Storage Access Framework, so no storage permission is required.

## Known limitations / roadmap

- [ ] Pages live in app-private storage and aren't restored after the app is restarted
- [ ] No re-crop or re-filter for an already saved page
- [ ] Drag-and-drop reordering (currently move buttons)
- [ ] Live edge-outline overlay on the camera preview
- [ ] Import from gallery
- [ ] OCR / searchable PDFs
- [ ] Unit and instrumented tests

## Contributing

1. Fork the repo and create a feature branch
2. Keep changes focused and follow existing Kotlin style
3. Open a pull request describing what changed and how you tested it

## License

Add a `LICENSE` file (MIT or Apache-2.0 are common choices) and update this section.

## Acknowledgements

[OpenCV](https://opencv.org/) · [CameraX](https://developer.android.com/training/camerax) · [Jetpack Compose](https://developer.android.com/jetpack/compose)
