package com.yaduinfotech.pdfeditor.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for signature fields to be added to PDF
 */
public class SignatureFieldConfig {

    // Getters and Setters
    @Getter
    private String name;
    @Getter
    private float x;
    @Getter
    private float y;
    @Setter
    @Getter
    private int page;
    @Getter
    private float width;
    @Getter
    private float height;

    // Default constructor
    public SignatureFieldConfig() {
        this.page = 1;
    }

}
