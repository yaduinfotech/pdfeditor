package com.yaduinfotech.pdfeditor;

import com.yaduinfotech.pdfeditor.jobs.AsyncPdfProcessor;
import com.yaduinfotech.pdfeditor.jobs.JobStore;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AsyncPdfControllerTest {

    @Test
    void rejectsPathTraversalJobIdentifiers() {
        AsyncPdfController controller = new AsyncPdfController(
                mock(AsyncPdfProcessor.class),
                mock(JobStore.class)
        );

        assertThrows(ResponseStatusException.class, () -> controller.download("../../etc/passwd"));
    }

    @Test
    void rejectsNonCanonicalUuidJobIdentifiers() {
        AsyncPdfController controller = new AsyncPdfController(
                mock(AsyncPdfProcessor.class),
                mock(JobStore.class)
        );

        assertThrows(ResponseStatusException.class, () -> controller.getStatus("1-1-1-1-1"));
    }
}
