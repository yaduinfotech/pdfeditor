package com.yaduinfotech.pdfeditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaduinfotech.pdfeditor.config.JsonConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "PDF Processing", description = "API endpoints for processing PDF documents")
public class PdfController {
    @Autowired
    private PdfService pdfService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Operation(summary = "Process PDF file", description = "Upload a PDF file with configuration to process placeholders, text fields, and signature fields")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "PDF processed successfully", 
                     content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(value = "/pdf/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processPdf(
            @Parameter(description = "PDF file to process", required = true) 
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "JSON configuration string", required = true) 
            @RequestParam("config") String jsonConfig) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }
        if (jsonConfig == null || jsonConfig.isBlank()) {
            throw new IllegalArgumentException("Configuration is required");
        }

        byte[] processed = pdfService.processStaticPdf(
                file.getInputStream(),
                jsonConfig
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=processed.pdf")
                .contentType(MediaType.parseMediaType("application/pdf"))
                .body(processed);
    }

    @Operation(summary = "Process PDF from JSON", description = "Process a PDF file provided as Base64 encoded string with configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "PDF processed successfully",
                     content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/process-json")
    public ResponseEntity<byte[]> processJson(@Valid @RequestBody PdfRequest request) {
        try {
            JsonConfig config = request.getConfigJson();
            String jsonConfig = objectMapper.writeValueAsString(config);
            // Validate input
            if (request.getFileBase64() == null || request.getFileBase64().isBlank()) {
                throw new IllegalArgumentException("PDF fileBase64 is required");
            }
            if (request.getConfigJson() == null) {
                throw new IllegalArgumentException("Configuration configJson is required");
            }

            byte[] pdfBytes = Base64.getDecoder().decode(request.getFileBase64());

            byte[] processed = pdfService.processStaticPdf(
                    new ByteArrayInputStream(pdfBytes),
                    jsonConfig
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .body(processed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
