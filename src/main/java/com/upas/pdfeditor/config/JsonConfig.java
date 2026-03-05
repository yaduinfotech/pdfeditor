package com.upas.pdfeditor.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class JsonConfig {

        private Map<String, String> placeholders;

        private Map<String, String> formFields;

        private List<TextFieldConfig> textFields;

        private List<SignatureFieldConfig> signatureFields;

        private Boolean flattenAfterFill;
}