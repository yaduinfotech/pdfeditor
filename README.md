# PDF Editor

A Spring Boot application for processing and editing PDF documents with support for form fields, placeholders, text fields, and signature fields.

## Features

- **Placeholder Replacement** - Replace `{{placeholder}}` text in PDFs with custom values
- **Form Field Filling** - Fill existing AcroForm fields in PDF documents
- **Text Field Creation** - Add new interactive text fields at specific coordinates
- **Signature Field Creation** - Add signature fields for digital signing
- **PDF Flattening** - Flatten filled PDFs to make them non-editable
- **Single PDF Processing** - Process one PDF at a time via REST API or web interface
- **Batch Processing** - Process multiple PDFs asynchronously
- **Swagger Documentation** - Interactive API documentation available

## Technology Stack

- **Framework**: Spring Boot 3.4.0
- **Java Version**: 21
- **PDF Library**: Apache PDFBox 2.0.30
- **API Documentation**: SpringDoc OpenAPI 2.6.0
- **JSON Processing**: Jackson
- **Build Tool**: Maven

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+

### Build and Run

```bash
# Build the application
./mvnw clean package -DskipTests

# Run the application
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

### Using the Web Interface

1. Open `http://localhost:8080` in your browser
2. Upload a PDF file
3. Configure placeholders, text fields, signature fields, or form fields
4. Click "Process PDF" to download the processed file

### Using the API

#### Process PDF (Multipart)

```bash
curl -X POST http://localhost:8080/api/pdf/process \
  -F "file=@sample.pdf" \
  -F "config='{\"placeholders\":{\"name\":\"John Doe\"}}'" \
  -o processed.pdf
```

#### Process PDF (JSON with Base64)

```bash
curl -X POST http://localhost:8080/api/process-json \
  -H "Content-Type: application/json" \
  -d '{
    "fileBase64": "<base64-encoded-pdf>",
    "configJson": {
      "placeholders": {
        "name": "John Doe",
        "date": "2024-01-15"
      },
      "formFields": {
        "firstName": "John",
        "lastName": "Doe"
      },
      "flattenAfterFill": true
    }
  }' \
  -o processed.pdf
```

#### Batch Processing

```bash
# Submit batch job
curl -X POST http://localhost:8080/pdf/bulk/async \
  -H "Content-Type: application/json" \
  -d '{
    "jobs": [
      {
        "fileBase64": "<base64-encoded-pdf-1>",
        "config": {"placeholders": {"name": "John"}}
      },
      {
        "fileBase64": "<base64-encoded-pdf-2>",
        "config": {"placeholders": {"name": "Jane"}}
      }
    ]
  }'

# Check job status
curl http://localhost:8080/pdf/status/{jobId}

# Download result
curl http://localhost:8080/pdf/download/{jobId} -o result.zip
```

## API Documentation

Once the application is running, access the interactive API documentation at:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## Configuration

Configuration can be modified in `src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# Upload limits (200MB default)
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=200MB

# Logging
logging.level.com.yaduinfotech.pdfeditor=DEBUG
logging.file.name=logs/pdfeditor.log
```

## PDF Configuration Schema

### Complete Configuration Example

```json
{
  "placeholders": {
    "name": "John Doe",
    "email": "john@example.com",
    "date": "2024-01-15",
    "SIGNATURE": ""
  },
  "formFields": {
    "firstName": "John",
    "lastName": "Doe",
    "company": "Acme Corp"
  },
  "textFields": [
    {
      "name": "newField",
      "value": "Custom value",
      "page": 1,
      "x": 100,
      "y": 700,
      "width": 200,
      "height": 20
    }
  ],
  "signatureFields": [
    {
      "name": "signature",
      "page": 1,
      "x": 100,
      "y": 600,
      "width": 200,
      "height": 50
    }
  ],
  "flattenAfterFill": true
}
```

### Configuration Options

| Property | Type | Description |
|----------|------|-------------|
| `placeholders` | Map<String, String> | Key-value pairs for replacing `{{key}}` text in the PDF |
| `formFields` | Map<String, String> | Key-value pairs for filling existing AcroForm fields |
| `textFields` | List<TextFieldConfig> | New text fields to add |
| `signatureFields` | List<SignatureFieldConfig> | New signature fields to add |
| `flattenAfterFill` | Boolean | Flatten PDF after filling (makes it non-editable) |

### TextFieldConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | String | - | Field name (required) |
| `value` | String | "" | Field value |
| `page` | Integer | 1 | Page number (1-based) |
| `x` | Float | 0 | X position from left |
| `y` | Float | 0 | Y position from bottom |
| `width` | Float | 200 | Field width |
| `height` | Float | 20 | Field height |

### SignatureFieldConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | String | - | Field name (required) |
| `page` | Integer | 1 | Page number (1-based) |
| `x` | Float | 0 | X position from left |
| `y` | Float | 0 | Y position from bottom |
| `width` | Float | 200 | Field width |
| `height` | Float | 50 | Field height |

## Project Structure

```
pdfeditor/
├── src/main/
│   ├── java/com/yaduinfotech/pdfeditor/
│   │   ├── PdfEditorApplication.java    # Main application class
│   │   ├── PdfController.java           # REST controller for PDF processing
│   │   ├── AsyncPdfController.java      # REST controller for batch processing
│   │   ├── PdfService.java              # Core PDF processing logic
│   │   ├── config/                      # Configuration classes
│   │   ├── exception/                   # Exception handling
│   │   └── jobs/                        # Async job processing
│   └── resources/
│       ├── application.properties        # Application configuration
│       ├── static/                       # Web interface HTML files
│       │   ├── index.html               # Single PDF processor UI
│       │   ├── async-pdf.html           # Batch processor UI
│       │   └── job-status.html          # Job status UI
│       └── fonts/                        # Custom fonts (Arial)
├── pom.xml                              # Maven configuration
└── README.md                            # This file
```

## License

This project is proprietary software developed by YaduInfotech.

