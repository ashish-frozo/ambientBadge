# Noto Fonts for Multilingual Support

This directory contains Noto fonts used for multilingual prescription rendering:

- `NotoSans-Regular.ttf`: Base Latin script font
- `NotoSans-Bold.ttf`: Bold variant for headings
- `NotoSansDevanagari-Regular.ttf`: Hindi/Devanagari script support
- `NotoSansTelugu-Regular.ttf`: Telugu script support

## License

These fonts are licensed under the SIL Open Font License, Version 1.1.
See http://scripts.sil.org/OFL for details.

## Font Selection

The fonts were chosen for:
1. Complete character coverage for required scripts
2. Consistent visual style across languages
3. Excellent legibility at small sizes (prescription text)
4. Support for medical/pharmaceutical symbols

## Usage

The fonts are embedded in PDFs using iText's font embedding with IDENTITY_H encoding
to ensure proper rendering across all devices and printers.

## Font Versions

- Noto Sans: Version 2.013
- Noto Sans Devanagari: Version 2.003
- Noto Sans Telugu: Version 2.004

## Download Source

Fonts were downloaded from Google Fonts:
https://fonts.google.com/noto

## Implementation Notes

1. Font files are embedded in the APK and extracted to app private storage
2. Fonts are cached in memory after first load
3. All text uses appropriate font based on script detection
4. Fallback chain ensures no missing glyphs
