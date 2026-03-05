package com.yaduinfotech.pdfeditor.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Main configuration class for PDF editing
 */
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "PDF configuration")
public class PdfProperties {

    @Getter
    @Setter
    private Boolean flattenAfterFill;
    
    @Getter
    @Setter
    private Map<String, List<String>> dropdownOptions;

    // Getters and Setters
    @Getter
    @Setter
    private List<TextFieldConfig> textFields;
    
    @Getter
    @Setter
    private List<SignatureFieldConfig> signatureFields;
    
    @Getter
    @Setter
    private Map<String, String> formFields;
    
    @Getter
    @Setter
    private Map<String, String> placeholders;

}
