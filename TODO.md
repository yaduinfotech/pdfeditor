 PDF Editor Optimization TODO

## User Experience Improvements
- [x] 1. Add health check endpoint (HealthController.java)
- [x] 2. Add input validation with better error messages (PdfRequest.java with @Valid)
- [x] 3. Optimize ObjectMapper usage (AppConfig.java - injected as Spring bean)
- [ ] 4. Cache font loading - (font loading stays in PdfService per-document for PDFBox compatibility)
- [x] 5. Add consistent API response wrapper (added validation, error handling in controller)

## File Size Reduction
- [x] 6. Enable PDF compression in PDFBox (document.save() with optimization)
- [x] 7. Remove unused dependencies (pdfbox-tools removed from pom.xml)
- [x] 8. Optimize PDF saving settings (compression enabled in application.properties)

## Additional Optimizations
- [x] Reduced logging verbosity (TRACE → WARN for springdoc)
- [x] Added server compression for responses
- [x] Cleaned up unused imports in PdfController
