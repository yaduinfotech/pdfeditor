package com.upas.pdfeditor.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextFieldConfig {

    private String name;
    private String value;
    private float x;
    private float y;
    private int page;
    private float width;
    private float height;
    private int fontSize;
    private String fontColor;

    public TextFieldConfig() {
        this.fontSize = 12;
        this.fontColor = "#000000";
    }
}