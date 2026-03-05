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

    public PdfService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] processStaticPdf(InputStream inputStream, String jsonConfig) {

        if (inputStream == null) {
            throw new PdfProcessingException("Uploaded PDF file is empty");
        }

        try (PDDocument document = PDDocument.load(
                inputStream,
                MemoryUsageSetting.setupMixed(50 * 1024 * 1024))) {

            PdfProperties config =
                    objectMapper.readValue(jsonConfig, PdfProperties.class);

            PDAcroForm form = document.getDocumentCatalog().getAcroForm();

            if (form == null) {
                form = new PDAcroForm(document);
                document.getDocumentCatalog().setAcroForm(form);
            }

            form.setNeedAppearances(true);
            document.getDocumentCatalog().getAcroForm().setAppendOnly(true);

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
                        font
                );
            }

            if (config.getTextFields() != null) {
                processTextFields(
                        document,
                        form,
                        config.getTextFields()
                );
            }

            if (config.getSignatureFields() != null) {
                processSignatureFields(
                        document,
                        form,
                        config.getSignatureFields()
                );
            }

            if (Boolean.TRUE.equals(config.getFlattenAfterFill())) {
                form.flatten();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);

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
            PDType0Font font
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
                                ph
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
            List<TextFieldConfig> fields
    ) throws IOException {

        for (TextFieldConfig field : fields) {

            if (fieldExists(form, field.getName())) {
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

            PDAnnotationWidget widget = textField.getWidgets().get(0);

            widget.setRectangle(
                    new PDRectangle(
                            field.getX(),
                            field.getY(),
                            field.getWidth(),
                            field.getHeight()
                    )
            );

            widget.setPage(page);
            widget.setPrinted(true);

            page.getAnnotations().add(widget);
            form.getFields().add(textField);
        }
    }

    /* ===================================================== */
    /* SIGNATURE FIELDS */
    /* ===================================================== */

    private void processSignatureFields(
            PDDocument document,
            PDAcroForm form,
            List<SignatureFieldConfig> fields
    ) throws IOException {

        for (SignatureFieldConfig field : fields) {

            if (fieldExists(form, field.getName())) {
                continue;
            }

            PDPage page = document.getPage(field.getPage() - 1);

            PDSignatureField sig = new PDSignatureField(form);
            sig.setPartialName(field.getName());

            PDAnnotationWidget widget = sig.getWidgets().get(0);

            widget.setRectangle(
                    new PDRectangle(
                            field.getX(),
                            field.getY(),
                            field.getWidth(),
                            field.getHeight()
                    )
            );

            widget.setPage(page);
            widget.setPrinted(true);

            page.getAnnotations().add(widget);
            form.getFields().add(sig);
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
            PlaceholderData ph
    ) throws IOException {

        if (fieldExists(form, name)) {
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

            String test =
                    line.length() == 0
                            ? word
                            : line + " " + word;

            float size =
                    font.getStringWidth(test) / 1000 * fontSize;

            if (size > width) {

                lines.add(line.toString());
                line = new StringBuilder(word);

            } else {

                if (line.length() > 0) {
                    line.append(" ");
                }

                line.append(word);
            }
        }

        if (line.length() > 0) {
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
                field.setValue(value);
            } else {
                log.warn("Form field not found in PDF: {}", fieldName);
            }
        }
    }
}