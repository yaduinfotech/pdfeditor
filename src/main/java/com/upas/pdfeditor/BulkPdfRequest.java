package com.upas.pdfeditor;

import java.util.List;

public class BulkPdfRequest {

    private List<PdfJob> jobs;

    public List<PdfJob> getJobs() {
        return jobs;
    }

    public void setJobs(List<PdfJob> jobs) {
        this.jobs = jobs;
    }
}