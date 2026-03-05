package com.upas.pdfeditor;

import com.upas.pdfeditor.config.PdfProperties;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PdfJob {

    private String fileBase64;
    private PdfProperties config;
    private String outputFileName;

}