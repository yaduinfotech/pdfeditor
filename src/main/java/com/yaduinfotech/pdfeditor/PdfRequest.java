package com.yaduinfotech.pdfeditor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yaduinfotech.pdfeditor.config.JsonConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfRequest {

    @NotBlank
    private String fileBase64;

    @NotNull
    @Valid
    private JsonConfig configJson;
    private boolean flattenAfterFill;

}