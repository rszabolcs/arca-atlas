package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.lit.AccTemplateBuilder;
import com.arcadigitalis.backend.lit.ManifestValidator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/lit")
public class LitController {

    private final AccTemplateBuilder accTemplateBuilder;
    private final ManifestValidator manifestValidator;

    public LitController(AccTemplateBuilder accTemplateBuilder, ManifestValidator manifestValidator) {
        this.accTemplateBuilder = accTemplateBuilder;
        this.manifestValidator = manifestValidator;
    }

    @GetMapping("/acc-template")
    public ObjectNode getAccTemplate(
        @RequestParam Long chainId,
        @RequestParam String proxyAddress,
        @RequestParam String packageKey,
        @RequestParam String beneficiaryAddress
    ) {
        return accTemplateBuilder.buildAccTemplate(chainId, proxyAddress, packageKey, beneficiaryAddress);
    }

    @PostMapping("/validate-manifest")
    public Map<String, Object> validateManifest(@RequestBody String manifestJson) {
        try {
            manifestValidator.validate(manifestJson);
            return Map.of("valid", true);
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }
}
