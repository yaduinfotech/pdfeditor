package com.yaduinfotech.pdfeditor.jobs;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class PdfJobStatus {

    @Getter
    @Setter
    private String jobId;
    @Getter
    @Setter
    private JobStatus status;
    @Getter
    @Setter
    private int totalFiles;
    @Getter
    @Setter
    private int processedFiles;
    @Getter
    @Setter
    private String downloadUrl;
    @Getter
    @Setter
    private String errorMessage;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Getter
    @Setter
    private LocalDateTime createdAt;

    public PdfJobStatus(String jobId, int totalFiles) {
        this.jobId = jobId;
        this.totalFiles = totalFiles;
        this.status = JobStatus.QUEUED;
        this.createdAt = LocalDateTime.now();
        this.processedFiles = 0;
    }
}
