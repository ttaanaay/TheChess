# Chess Bubble Analyzer

Android overlay app (Kotlin) that watches a chess app on-screen, recognizes
each move via screen-capture + template matching, evaluates it with a chess
engine, and shows a floating bubble with the move + quality label
(Excellent / Great / Good / Miss / Blunder / ...) — built for use while
streaming/commentating.

## How it works

```
MediaProjection screen capture (every ~0.7s)
        │
        ▼
crop board region using saved 4-corner calibration
        │
        ▼
BoardRecognizer: template-match each of 64 squares → placement array
        │
        ▼
MoveResolver: brute-force match against legal moves from the last known
              position (com.chessbubble.chess.MoveGen) → validated SAN move
        │
        ▼
ChessEngine.evaluateCp(before) vs evaluateCp(after) → centipawn loss
        │
        ▼
MoveQuality.fromCentipawnLoss() → Excellent/Great/Good/Miss/Blunder
        │
        ▼
Broadcast → OverlayService draws it in the floating bubble
```

## What's implemented vs. stubbed

| Component | Status |
|---|---|
| Floating bubble overlay (`overlay/OverlayService.kt`) | ✅ working |
| Board corner calibration UI (`ui/BoardCalibrationActivity.kt`) | ✅ working |
| Screen capture via MediaProjection (`capture/ScreenCaptureService.kt`) | ✅ working |
| Full legal chess move generation + SAN (`chess/MoveGen.kt`) | ✅ working (castling, en passant, promotion, check/mate suffixes) |
| Move resolution from vision snapshot (`chess/MoveResolver.kt`) | ✅ working |
| Move-quality thresholds (`model/MoveQuality.kt`) | ✅ working, tune freely |
| Piece recognition (`vision/BoardRecognizer.kt`) | ⚠️ template-matching algorithm is implemented, but you must **supply 14 template PNGs** for your chess app's theme — see `app/src/main/assets/templates/default/README.md`. Until then, recognition silently no-ops. |
| Chess engine (`engine/ChessEngine.kt`) | ⚠️ `StubEngine` is wired in by default (always returns 0). Swap in a real Stockfish build — see the KDoc in that file for two integration paths (JNI vs. bundled CLI binary). |

## Building the APK

### Via GitHub Actions (recommended)
1. Push this repo to GitHub.
2. The workflow at `.github/workflows/build.yml` runs automatically on every
   push to `main` (or trigger it manually from the Actions tab).
3. Download the built APK from the workflow run's **Artifacts** section
   (`app-debug-apk`).

### Locally
This repo does not include the Gradle wrapper binary (`gradle-wrapper.jar`)
since it's a binary file. To build locally:
```bash
gradle wrapper --gradle-version 8.7   # generates gradlew + gradlew.bat once
./gradlew assembleDebug
```
(Requires Gradle 8.7+ and JDK 17 already installed to run that first command.)

## Setup on-device (once installed)
1. Open the app → **ตั้งค่าตำแหน่งกระดาน** (calibrate board) → grant screen
   capture permission for the one-time preview frame → drag the 4 green
   handles onto the corners of the chess board as shown in your chess app →
   confirm.
2. **เริ่มวิเคราะห์** (start) → grant the "แสดงทับแอปอื่น" (draw over other
   apps) permission if prompted, then grant screen capture again (this time
   for the live analysis session).
3. Open your chess app; the bubble appears top-right and updates after each
   recognized move.

## Next steps to make this fully real
1. **Add piece template images** for the exact chess app/theme you stream
   (see the README inside `assets/templates/default/`).
2. **Wire in a real Stockfish engine** — replace `StubEngine()` with
   `StockfishJniEngine()` in `ScreenCaptureService.onCreate()` once you've
   added a native build (NDK) or a bundled CLI binary driven via
   `ProcessBuilder`.
3. Tune `MoveQuality.fromCentipawnLoss()` thresholds to taste.
4. Consider persisting `sanHistory` from `GameStateTracker` to export a PGN
   after the stream ends.
