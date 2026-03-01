package com.arcadigitalis.backend.api.dto;

/**
 * Response for GET /acc-template — wraps the generated ACC JSON.
 */
public record AccTemplateResponse(Object accJson) {}
