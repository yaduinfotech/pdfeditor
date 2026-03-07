package com.yaduinfotech.pdfeditor.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaduinfotech.pdfeditor.BulkPdfRequest;
import com.yaduinfotech.pdfeditor.PdfJob;
import com.yaduinfotech.pdfeditor.PdfService;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AsyncPdfProcessor {

    private final JobStore jobStore;
    private final PdfService pdfService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Thread pool for parallel PDF processing
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
    );

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

            List<PdfJob> pdfJobs = request.getJobs();
            int totalJobs = pdfJobs.size();
            
            // Process PDFs in parallel
            List<CompletableFuture<ProcessedPdf>> futures = new ArrayList<>();
            
            for (int i = 0; i < totalJobs; i++) {
                final int index = i;
                final PdfJob pdfJob = pdfJobs.get(i);
                
                CompletableFuture<ProcessedPdf> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] pdfBytes = Base64.getDecoder().decode(pdfJob.getFileBase64());
                        String jsonConfig = mapper.writeValueAsString(pdfJob.getConfig());

                        byte[] result = pdfService.processStaticPdf(
                                new ByteArrayInputStream(pdfBytes),
                                jsonConfig
                        );

                        String name = pdfJob.getOutputFileName() != null ?
                                pdfJob.getOutputFileName() :
                                "file_" + index + ".pdf";

                        return new ProcessedPdf(name, result);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process PDF: " + e.getMessage(), e);
                    }
                }, executorService);
                
                futures.add(future);
            }

            // Wait for all to complete and write to zip
            try (ZipOutputStream zipOut =
                         new ZipOutputStream(new FileOutputStream(zipFile))) {

                int processed = 0;
                
                for (CompletableFuture<ProcessedPdf> future : futures) {
                    try {
                        ProcessedPdf processedPdf = future.get();
                        zipOut.putNextEntry(new ZipEntry(processedPdf.fileName));
                        zipOut.write(processedPdf.pdfBytes);
                        zipOut.closeEntry();
                        processed++;
                        job.setProcessedFiles(processed);
                    } catch (Exception e) {
                        job.setStatus(JobStatus.FAILED);
                        job.setErrorMessage("Error processing some PDFs: " + e.getMessage());
                        return;
                    }
                }
            }

            job.setStatus(JobStatus.COMPLETED);
            job.setDownloadUrl("/pdf/download/" + jobId);

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
        }
    }
    
    // Helper class to hold processed PDF data
    private static class ProcessedPdf {
        final String fileName;
        final byte[] pdfBytes;
        
        ProcessedPdf(String fileName, byte[] pdfBytes) {
            this.fileName = fileName;
            this.pdfBytes = pdfBytes;
        }
    }
}
