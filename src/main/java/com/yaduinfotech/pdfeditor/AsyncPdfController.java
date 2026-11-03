package com.yaduinfotech.pdfeditor;

import com.yaduinfotech.pdfeditor.jobs.AsyncPdfProcessor;
import com.yaduinfotech.pdfeditor.jobs.JobStore;
import com.yaduinfotech.pdfeditor.jobs.PdfJobStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        if (request == null || request.getJobs() == null || request.getJobs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one PDF job is required");
        }

        String jobId = UUID.randomUUID().toString();
        PdfJobStatus status = new PdfJobStatus(jobId, request.getJobs().size());
        jobStore.save(status);
        processor.processJob(jobId, request);
        return status;
    }

    @GetMapping("/status/{jobId}")
    public PdfJobStatus getStatus(@PathVariable String jobId) {
        PdfJobStatus status = jobStore.get(validateJobId(jobId));
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return status;
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) throws IOException {
        String canonicalJobId = validateJobId(jobId);
        Path outputDirectory = Paths.get("output").toAbsolutePath().normalize();
        Path file = outputDirectory.resolve(canonicalJobId + ".zip").normalize();

        if (!file.startsWith(outputDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job ID");
        }
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job output not found");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=result.zip")
                .body(new FileSystemResource(file));
    }

    private String validateJobId(String jobId) {
        try {
            UUID parsed = UUID.fromString(jobId);
            String canonical = parsed.toString();
            if (!canonical.equalsIgnoreCase(jobId)) {
                throw new IllegalArgumentException("Job ID must be a canonical UUID");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job ID", exception);
        }
    }
}
