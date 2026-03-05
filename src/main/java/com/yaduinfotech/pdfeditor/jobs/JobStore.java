package com.yaduinfotech.pdfeditor.jobs;

import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStore {

    private final ConcurrentHashMap<String, PdfJobStatus> jobs = new ConcurrentHashMap<>();

    public void save(PdfJobStatus job) {
        jobs.put(job.getJobId(), job);
    }

    public PdfJobStatus get(String jobId) {
        return jobs.get(jobId);
    }

    public Collection<PdfJobStatus> getAll() {
        return jobs.values();
    }
}
