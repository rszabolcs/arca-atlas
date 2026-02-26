package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.ArtifactUploadResponse;
import com.arcadigitalis.backend.storage.ArtifactService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/artifacts")
public class StorageController {

    private final ArtifactService artifactService;

    public StorageController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArtifactUploadResponse upload(
        @RequestParam String packageKey,
        @RequestParam String artifactType,
        @RequestParam String declaredHash,
        @RequestParam("file") MultipartFile file
    ) throws Exception {
        byte[] content = file.getBytes();
        return artifactService.pin(packageKey, artifactType, declaredHash, content);
    }

    @GetMapping("/{id}")
    public byte[] retrieve(@PathVariable UUID id) {
        return artifactService.retrieve(id);
    }
}
