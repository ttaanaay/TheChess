# Piece templates (theme: "default")

BoardRecognizer needs one cropped screenshot per piece type + empty square,
taken from the exact chess app/board theme you'll be streaming, so it can
template-match each of the 64 squares.

Add these 14 PNG files in this folder (crop tightly to a single square,
same zoom level the app will run at):

- wP.png, wN.png, wB.png, wR.png, wQ.png, wK.png   (white pieces)
- bP.png, bN.png, bB.png, bR.png, bQ.png, bK.png   (black pieces)
- empty_light.png, empty_dark.png                   (empty light/dark squares)

The app currently has NO recognition accuracy until these are added —
ScreenCaptureService silently no-ops if this folder doesn't contain them
(see `PieceTemplates.loadFromAssets` / the `templates` null-check in
ScreenCaptureService.captureOnce()).

Tip: take one full-board screenshot from the target app, crop the 14 needed
squares out of it with any image editor, and drop them in here with the exact
filenames above.
