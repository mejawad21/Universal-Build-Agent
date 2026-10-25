# JD PDF Pro — Android

A new, original offline-first PDF editor/reader, not an Adobe product.

## Version 0.1.0 — implemented
- Open PDF via Android document picker or share/open-with
- Paginated viewing and zoom, recent document list
- Search for selectable PDF text (not OCR)
- Merge selected PDFs while retaining vector data and searchable text
- Extract one PDF page, rotate one page
- Stamp editable typed text on one PDF page (not cryptographic signing)
- Convert images to PDF
- Save new PDF through Android's secure Storage Access Framework

## Important limits
- No OCR, highlighting/pen tools, cryptographic signatures, forms editing, PDF-to-Word/Excel, or true PDF compression yet.
- Scanned PDFs have no text layer for search.
- The built-in viewer renders one page at a time; high-resolution documents can be memory-intensive.
- Documents are processed on device; no network permission.
- Some password-protected/corrupt PDFs and exotic encodings may not work.
- Built with open-source PdfBox-Android (Apache 2.0) and Android framework APIs.
- APK built by GitHub Actions is a debug APK, not a Play Store release.
