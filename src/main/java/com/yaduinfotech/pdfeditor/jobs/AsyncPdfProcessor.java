package com.yaduinfotech.pdfeditor.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaduinfotech.pdfeditor.BulkPdfRequest;
import com.yaduinfotech.pdfeditor.PdfJob;
import com.yaduinfotech.pdfeditor.PdfService;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AsyncPdfProcessor {

    private final JobStore jobStore;
    private final PdfService pdfService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AsyncPdfProcessor(JobStore jobStore, PdfService pdfService) {
        this.jobStore = jobStore;
        this.pdfService = pdfService;
    }

    @Async
    public void processJob(String jobId, BulkPdfRequest request) {

        PdfJobStatus job = jobStore.get(jobId);

        try {
            job.setStatus(JobStatus.PROCESSING);

            File zipFile = new File("output/" + jobId + ".zip");
            zipFile.getParentFile().mkdirs();

            try (ZipOutputStream zipOut =
                         new ZipOutputStream(new FileOutputStream(zipFile))) {

                int processed = 0;

                for (PdfJob pdfJob : request.getJobs()) {

                    byte[] pdfBytes = Base64.getDecoder().decode(pdfJob.getFileBase64());
                    String jsonConfig = mapper.writeValueAsString(pdfJob.getConfig());

                    byte[] result = pdfService.processStaticPdf(
                            new ByteArrayInputStream(pdfBytes),
                            jsonConfig
                    );

                    String name = pdfJob.getOutputFileName() != null ?
                            pdfJob.getOutputFileName() :
                            "file_" + processed + ".pdf";

                    zipOut.putNextEntry(new ZipEntry(name));
                    zipOut.write(result);
                    zipOut.closeEntry();

                    processed++;
                    job.setProcessedFiles(processed);
                }
            }

            job.setStatus(JobStatus.COMPLETED);
            job.setDownloadUrl("/pdf/download/" + jobId);

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
        }
    }
}