package com.yaduinfotech.pdfeditor;

import com.yaduinfotech.pdfeditor.jobs.AsyncPdfProcessor;
import com.yaduinfotech.pdfeditor.jobs.JobStore;
import com.yaduinfotech.pdfeditor.jobs.PdfJobStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.UUID;


@RestController
@RequestMapping("/pdf")
public class AsyncPdfController {

    private final AsyncPdfProcessor processor;
    private final JobStore jobStore;

    public AsyncPdfController(AsyncPdfProcessor processor, JobStore jobStore) {
        this.processor = processor;
        this.jobStore = jobStore;
    }

    @PostMapping("/bulk/async")
    public PdfJobStatus submitJob(@RequestBody BulkPdfRequest request) {

        String jobId = UUID.randomUUID().toString();

        PdfJobStatus status = new PdfJobStatus(jobId, request.getJobs().size());
        jobStore.save(status);

        processor.processJob(jobId, request);

        return status;
    }
    @GetMapping("/status/{jobId}")
    public PdfJobStatus getStatus(@PathVariable String jobId) {
        return jobStore.get(jobId);
    }
    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) throws IOException {

        File file = new File("output/" + jobId + ".zip");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=result.zip")
                .body(new FileSystemResource(file));
    }

}
