package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.PackageStatusResponse;
import com.arcadigitalis.backend.policy.PackageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/packages")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping("/{packageKey}/status")
    public PackageStatusResponse getStatus(@PathVariable String packageKey) {
        return packageService.getPackageView(packageKey);
    }
}
