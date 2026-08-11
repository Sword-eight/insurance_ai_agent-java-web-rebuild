package com.insurance.platform.document.controller;

import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.trace.TraceIdContext;
import com.insurance.platform.document.service.DocumentService;
import com.insurance.platform.document.vo.DocumentView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentView> upload(@RequestPart("file") MultipartFile file) {
        String traceId = TraceIdContext.currentTraceId();
        return ApiResponse.success(service.upload(file, traceId), traceId);
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentView>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.list(page, size), TraceIdContext.currentTraceId());
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentView> get(@PathVariable UUID documentId) {
        return ApiResponse.success(service.get(documentId), TraceIdContext.currentTraceId());
    }
}
