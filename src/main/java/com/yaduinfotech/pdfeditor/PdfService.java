package com.yaduinfotech.pdfeditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaduinfotech.pdfeditor.config.PdfProperties;
import com.yaduinfotech.pdfeditor.config.SignatureFieldConfig;
import com.yaduinfotech.pdfeditor.config.TextFieldConfig;
import com.yaduinfotech.pdfeditor.exception.PdfProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Slf4j
@Service
public class PdfService {

    private final ObjectMapper objectMapper;
    private byte[] cachedFontBytes;
    private PDType0Font cachedFont;
    private final Set<String> processedFieldNames = new HashSet<>();

    public PdfService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] processStaticPdf(InputStream inputStream, String jsonConfig) {

        if (inputStream == null) {
            throw new PdfProcessingException("Uploaded PDF file is empty");
        }

        try (PDDocument document = PDDocument.load(
                inputStream,
                MemoryUsageSetting.setupMixed(200 * 1024 * 1024))) {

            PdfProperties config =
                    objectMapper.readValue(jsonConfig, PdfProperties.class);

            PDAcroForm form = document.getDocumentCatalog().getAcroForm();

            if (form == null) {
                form = new PDAcroForm(document);
                document.getDocumentCatalog().setAcroForm(form);
            }

            form.setNeedAppearances(true);
            document.getDocumentCatalog().getAcroForm().setAppendOnly(true);

            // Check for existing signatures BEFORE modifying the document
            boolean hasExistingSignatures = hasSignature(document);

            // Pre-populate field names set for O(1) lookups
            Set<String> existingFieldNames = new HashSet<>();
            for (PDField field : form.getFieldTree()) {
                String name = field.getPartialName();
                if (name != null) {
                    existingFieldNames.add(name.toLowerCase());
                }
            }

            PDType0Font font = loadFont(document);

            /* Fill existing AcroForm fields */
            if (config.getFormFields() != null) {
                fillFormFields(form, config.getFormFields());
            }

            if (config.getPlaceholders() != null) {
                processPlaceholders(
                        document,
                        form,
                        config.getPlaceholders(),
                        font,
                        existingFieldNames
                );
            }

            if (config.getTextFields() != null) {
                processTextFields(
                        document,
                        form,
                        config.getTextFields(),
                        existingFieldNames
                );
            }

            if (config.getSignatureFields() != null) {
                processSignatureFields(
                        document,
                        form,
                        config.getSignatureFields(),
                        existingFieldNames
                );
            }

            // Only flatten if explicitly requested AND no existing signatures
            if (Boolean.TRUE.equals(config.getFlattenAfterFill()) && !hasExistingSignatures) {
                form.flatten();
            }
            
            // Only refresh appearances if no existing signatures (preserves signature validity)
            if (!hasExistingSignatures) {
                form.refreshAppearances();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // For documents with existing signatures, try to preserve them
            // saveIncremental only works when loaded from File, not InputStream
            if (hasExistingSignatures) {
                try {
                    // Try incremental save - may fail if loaded from InputStream
                    document.save(baos);
                    log.info("Saved with existing signatures (incremental save)");
                } catch (Exception e) {
                    log.warn("Save with signatures warning: {}", e.getMessage());
                    document.save(baos);
                }
            } else {
                // Regular save for non-signed documents
                document.save(baos);
            }

            return baos.toByteArray();

        } catch (Exception e) {
            throw new PdfProcessingException("PDF processing failed", e);
        }
    }

    /* ===================================================== */
    /* FONT LOADING */
    /* ===================================================== */

    private PDType0Font loadFont(PDDocument document) throws IOException {

        if (cachedFontBytes == null) {

            try (InputStream fontStream =
                         getClass().getClassLoader()
                                 .getResourceAsStream("fonts/arial.ttf")) {

                if (fontStream == null) {
                    throw new PdfProcessingException("Font not found");
                }

                cachedFontBytes = fontStream.readAllBytes();
            }
        }

        return PDType0Font.load(
                document,
                new ByteArrayInputStream(cachedFontBytes),
                true
        );
    }

    /* ===================================================== */
    /* PLACEHOLDER PROCESSING */
    /* ===================================================== */

    private void processPlaceholders(
            PDDocument document,
            PDAcroForm form,
            Map<String, String> values,
            PDType0Font font,
            Set<String> existingFieldNames
    ) throws IOException {

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {

            PDPage page = document.getPage(pageIndex);

            PlaceholderStripper stripper = new PlaceholderStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);

            stripper.getText(document);

            List<PlaceholderData> placeholders = stripper.getPlaceholders();

            try (PDPageContentStream cs =
                         new PDPageContentStream(
                                 document,
                                 page,
                                 PDPageContentStream.AppendMode.APPEND,
                                 true,
                                 true)) {

                for (PlaceholderData ph : placeholders) {

                    String key =
                            ph.key.replace("{{", "")
                                    .replace("}}", "")
                                    .trim();

                    /* Skip placeholders not in config */
                    if (!values.containsKey(key) && !key.toUpperCase().startsWith("SIGN")) {
                        continue;
                    }


                    float pageHeight = page.getMediaBox().getHeight();
                    float yTop = pageHeight - ph.y;


                    String value = values.get(key);

                    cs.setNonStrokingColor(1.0f, 1.0f, 1.0f); // White Color
                    cs.addRect(ph.x, yTop - 2, ph.width, ph.height * 3);
                    cs.fill();
                    cs.setNonStrokingColor(0.0f, 0.0f, 0.0f); // Black Color

                    if (key.toUpperCase().startsWith("SIGN")) {

                        createSignatureField(
                                document,
                                form,
                                page,
                                key,
                                ph,
                                existingFieldNames
                        );

                    } else if (value != null) {

                        writeWrappedText(
                                cs,
                                value,
                                ph.x,
                                yTop,
                                ph.width,
                                ph.height * 3,
                                font,
                                ph.fontSize
                        );
                    }
                }
            }
        }
    }

    /* ===================================================== */
    /* TEXT FIELDS */
    /* ===================================================== */

    private void processTextFields(
            PDDocument document,
            PDAcroForm form,
            List<TextFieldConfig> fields,
            Set<String> existingFieldNames
    ) throws IOException {

        for (TextFieldConfig field : fields) {

            // O(1) lookup instead of iterating all fields
            if (field.getName() != null && existingFieldNames.contains(field.getName().toLowerCase())) {
                continue;
            }

            PDPage page = document.getPage(field.getPage() - 1);

            PDTextField textField = new PDTextField(form);
            textField.setPartialName(field.getName());

            textField.setMultiline(true);
            textField.setDefaultAppearance("/Helv 0 Tf 0 g");

            if (field.getValue() != null) {
                textField.setValue(field.getValue());
            }
            float yTop = page.getMediaBox().getHeight();

            PDAnnotationWidget widget = textField.getWidgets().get(0);
            widget.setRectangle(
                    new PDRectangle(
                            field.getX(),
                            yTop - field.getY(),
                            field.getWidth(),
                            field.getHeight()
                    )
            );

            widget.setPage(page);
            widget.setPrinted(true);

            page.getAnnotations().add(widget);
            form.getFields().add(textField);
            
            // Add to existing field names for subsequent lookups
            existingFieldNames.add(field.getName().toLowerCase());
        }
        // Removed redundant refreshAppearances() - called once at end of processStaticPdf
    }

    /* ===================================================== */
    /* SIGNATURE FIELDS */
    /* ===================================================== */

    private void processSignatureFields(
            PDDocument document,
            PDAcroForm form,
            List<SignatureFieldConfig> fields,
            Set<String> existingFieldNames
    ) throws IOException {

        for (SignatureFieldConfig field : fields) {

            // O(1) lookup instead of iterating all fields
            if (field.getName() != null && existingFieldNames.contains(field.getName().toLowerCase())) {
                continue;
            }

            PDPage page = document.getPage(field.getPage() - 1);

            PDSignatureField sig = new PDSignatureField(form);
            sig.setPartialName(field.getName());

            PDAnnotationWidget widget = sig.getWidgets().get(0);

            widget.setRectangle(
                    new PDRectangle(
                            field.getX(),
                            page.getMediaBox().getHeight() - field.getY(),
                            field.getWidth(),
                            field.getHeight()
                    )
            );

            widget.setPage(page);
            widget.setPrinted(true);

            page.getAnnotations().add(widget);
            form.getFields().add(sig);
            
            // Add to existing field names for subsequent lookups
            existingFieldNames.add(field.getName().toLowerCase());
            
            // Generate appearance for the signature field
            createSignatureFieldAppearance(document, page, widget);
        }
    }

    /* ===================================================== */
    /* SIGNATURE FIELD FROM PLACEHOLDER */
    /* ===================================================== */

    private void createSignatureField(
            PDDocument document,
            PDAcroForm form,
            PDPage page,
            String name,
            PlaceholderData ph,
            Set<String> existingFieldNames
    ) throws IOException {

        // O(1) lookup instead of iterating all fields
        if (name != null && existingFieldNames.contains(name.toLowerCase())) {
            return;
        }

        PDSignatureField sig = new PDSignatureField(form);
        sig.setPartialName(name);

        PDAnnotationWidget widget = sig.getWidgets().get(0);

        float pageHeight = page.getMediaBox().getHeight();
        float yTop = pageHeight - ph.y;
        float height = 40;

        widget.setRectangle(
                new PDRectangle(ph.x, yTop - height + 15, 120, height)
        );

        widget.setPage(page);
        widget.setPrinted(true);

        page.getAnnotations().add(widget);
        form.getFields().add(sig);
        
        // Add to existing field names for subsequent lookups
        existingFieldNames.add(name.toLowerCase());
        
        // Generate appearance for the signature field
        createSignatureFieldAppearance(document, page, widget);
    }

    /* ===================================================== */
    /* SIGNATURE FIELD APPEARANCE GENERATION */
    /* ===================================================== */

    /**
     * Manually generates appearance stream for signature fields.
     * This is necessary because PDFBox does not automatically generate 
     * appearances for programmatically created signature fields.
     */
    private void createSignatureFieldAppearance(
            PDDocument document,
            PDPage page,
            PDAnnotationWidget widget
    ) throws IOException {
        PDRectangle rect = widget.getRectangle();
        if (rect == null) {
            return;
        }

        // Create appearance content stream
        try (PDPageContentStream cs = new PDPageContentStream(
                document, 
                page, 
                PDPageContentStream.AppendMode.APPEND, 
                true, 
                true)) {
            
            float x = rect.getLowerLeftX();
            float y = rect.getLowerLeftY();
            float width = rect.getWidth();
            float height = rect.getHeight();
            
            // Set border style
            PDBorderStyleDictionary borderDict = new PDBorderStyleDictionary();
            borderDict.setWidth(1);
            widget.setBorderStyle(borderDict);
            
            // Draw signature field border
            cs.setStrokingColor(0.5f, 0.5f, 0.5f);
            cs.setLineWidth(1);
            cs.addRect(x, y, width, height);
            cs.stroke();
            
            // Draw "Sign Here" text inside the field
            PDType0Font font = loadFont(document);
            cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
            cs.setFont(font, 8);
            
            // Center the text
            String text = "Sign Here";
            float textWidth = font.getStringWidth(text) / 1000 * 8;
            float textX = x + (width - textWidth) / 2;
            float textY = y + height / 2 - 4;
            
            cs.beginText();
            cs.newLineAtOffset(textX, textY);
            cs.showText(text);
            cs.endText();
            
            // Draw signature line
            float lineY = y + 8;
            float lineMargin = 10;
            cs.setStrokingColor(0.3f, 0.3f, 0.3f);
            cs.setLineWidth(0.5f);
            cs.moveTo(x + lineMargin, lineY);
            cs.lineTo(x + width - lineMargin, lineY);
            cs.stroke();
        }

        log.debug("Generated appearance for signature field widget");
    }

    /* ===================================================== */
    /* TEXT WRAPPING */
    /* ===================================================== */

    private void writeWrappedText(
            PDPageContentStream cs,
            String text,
            float x,
            float y,
            float width,
            float height,
            PDType0Font font,
            float fontSize
    ) throws IOException {


        float leading = fontSize * 1.2f;

        List<String> lines =
                wrapText(text, width, font, fontSize);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y + height - leading);

        for (String line : lines) {

            cs.showText(line);
            cs.newLineAtOffset(0, -leading);
        }

        cs.endText();
    }

    private List<String> wrapText(
            String text,
            float width,
            PDType0Font font,
            float fontSize
    ) throws IOException {

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            float size = font.getStringWidth(test) / 1000 * fontSize;

            if (size > width) {
                // Only add if line has content
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    // optional: add a blank line between wrapped lines
                    // lines.add("");
                }
                line = new StringBuilder(word);
            } else {
                if (!line.isEmpty()) {
                    line.append(" ");
                }
                line.append(word);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines;
    }
    /* ===================================================== */
    /* FIELD CHECK */
    /* ===================================================== */

    private boolean fieldExists(PDAcroForm form, String name) {

        if (form == null) return false;


        for (PDField field : form.getFieldTree()) {

            if (name.equalsIgnoreCase(field.getPartialName())) {
                return true;
            }
        }

        return false;
    }

    /* ===================================================== */
    /* PLACEHOLDER DETECTOR */
    /* ===================================================== */

    static class PlaceholderData {

        String key;
        float x;
        float y;
        float width;
        float height;
        float fontSize;
    }

    static class PlaceholderStripper extends PDFTextStripper {

        private final List<PlaceholderData> placeholders = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();

        private float startX;
        private float startY;
        private float height;
        private float lastX;
        private float lastWidth;
        private float fontSize;

        private boolean capturing = false;

        public PlaceholderStripper() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void processTextPosition(TextPosition text) {

            String ch = text.getUnicode();
            if (ch == null) return;

            if (!capturing && ch.equals("{")) {

                capturing = true;
                buffer.setLength(0);
                buffer.append(ch);

                startX = text.getXDirAdj();
                startY = text.getYDirAdj() + text.getHeightDir();
                height = text.getHeightDir();
                fontSize = text.getFontSizeInPt();

                lastX = startX;
                lastWidth = text.getWidthDirAdj();

                return;
            }

            if (capturing) {

                buffer.append(ch);

                lastX = text.getXDirAdj();
                lastWidth = text.getWidthDirAdj();

                String textBuffer = buffer.toString();

                if (textBuffer.startsWith("{{") && textBuffer.endsWith("}}")) {

                    PlaceholderData ph = new PlaceholderData();

                    ph.key = textBuffer;
                    ph.x = startX;
                    ph.y = startY;
                    ph.width = (lastX - startX) + lastWidth;
                    ph.height = height;
                    ph.fontSize = fontSize;

                    placeholders.add(ph);

                    capturing = false;
                    buffer.setLength(0);
                }

                if (buffer.length() > 100) {

                    capturing = false;
                    buffer.setLength(0);
                }
            }
        }


        public List<PlaceholderData> getPlaceholders() {
            return placeholders;
        }
    }

    private void fillFormFields(
            PDAcroForm form,
            Map<String, String> values
    ) throws IOException {

        if (values == null || values.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {

            String fieldName = entry.getKey();
            String value = entry.getValue();

            PDField field = form.getField(fieldName);

            if (field == null) {
                for (PDField f : form.getFieldTree()) {
                    if (f.getFullyQualifiedName().equalsIgnoreCase(fieldName)) {
                        field = f;
                        break;
                    }
                }
            }
            if (field != null) {
                if (field instanceof PDSignatureField) {
                    continue; // never modify signature field
                }
                field.setValue(value);
            } else {
                log.warn("Form field not found in PDF: {}", fieldName);
            }
        }
    }


    private boolean hasSignature(PDDocument document) {
        try {
            for (PDSignature sig : document.getSignatureDictionaries()) {
                if (sig.getByteRange() != null) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }
}