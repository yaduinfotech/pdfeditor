package com.yaduinfotech.pdfeditor;

import com.yaduinfotech.pdfeditor.config.PdfProperties;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PdfJob {

    private String fileBase64;
    private PdfProperties config;
    private String outputFileName;

}