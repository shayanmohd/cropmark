# Cropmark

Cropmark makes passport, visa and ID photos that measure themselves. Pick a document (8 are bundled,
each checked against its issuer's page: US passport, US visa DS-160, India passport applied for
abroad, India e-Visa, OCI card and PAN card, Canada visitor visa and China visa, or a custom size), take a photo with a live frame that shows where
crown, chin and eyes must fall, or choose one from the gallery. Cropmark finds the face, replaces the
background with clean hair edges where the issuer allows edits, solves the crop to the rule, runs twelve checks and writes either a
form file with exact pixels under a kilobyte cap or a 4x6, 5x7, A4 or Letter print sheet. It is for
applicants with a deadline and for counter operators who shoot every walk-in. It is a one-time
purchase with no account, ads or subscription.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=cropmark-upload.jks
storePassword=...
keyAlias=cropmark
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/cropmark/
  App.kt, MainActivity.kt   entry points; MainActivity forwards volume and remote keys and share intents
  core/                     plain Kotlin, no Android imports, JVM tested
    spec/                   DocSpec, SpecCatalog (specs.json), SpecMath, custom size rules
    crop/                   FaceGeometry, CropSolver, CrownFinder, FrameGuides
    matte/                  GuidedFilter, Decontaminate (edge clean-up, feather, compositing)
    check/                  ComplianceChecker, LiveGuide, PhotoStatsCalc
    sheet/                  SheetLayout (margins, gaps, edge-to-edge fallback, check ruler)
    jpeg/                   SizeSearch (quality and pixel search inside KB limits), JfifDensity
    backup/                 manifest codec and import planner
    review/                 ReviewPolicy
  ml/                       FaceAnalyzer (MediaPipe Face Landmarker), PersonSegmenter (Selfie Segmenter)
  camera/                   CameraController (CameraX preview, capture, analysis), ShutterBus
  render/                   PhotoRenderer, SheetRenderer (JPEG and PdfDocument)
  data/db, data/prefs       Room entities and DAOs, DataStore settings
  data/repo/                PhotoRepository, ExportRepository, BackupRepository, SpecRepository
  ui/                       one package per screen, components (SpecFrame, MmRuler, SizeDiagram), theme, nav
app/src/main/assets/        models/, specs/specs.json, privacy.txt, licences/
app/src/test/               JVM unit tests for everything in core/
store/                      Play listing copy, icon, feature graphic, screenshots
docs/                       privacy policy, landing page, font licences, demo portrait licence
```

## Bundled models and fonts

| File | Size | Source | Licence |
|---|---|---|---|
| `assets/models/face_landmarker.task` | 3,758,596 bytes | storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1 | Apache 2.0 |
| `assets/models/selfie_segmenter.tflite` | 249,537 bytes | storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest | Apache 2.0 |
| MediaPipe Tasks Vision 1.0.0 | library | google() Maven | Apache 2.0 |
| Sofia Sans Condensed (Medium, SemiBold) | fonts | Google Fonts | SIL OFL 1.1, `docs/OFL-SofiaSansCondensed.txt` |
| Public Sans (Regular, Medium, SemiBold) | fonts | Google Fonts | SIL OFL 1.1, `docs/OFL-PublicSans.txt` |

Models ship inside the APK and are never downloaded. ML Kit is not used because its transport library
merges network permissions; the manifest also removes INTERNET and ACCESS_NETWORK_STATE with
`tools:node="remove"`. The demo portrait in the store screenshots is CC0, see `docs/PORTRAIT-LICENCE.md`.

## Licence

Copyright SocialSure Private Limited.
