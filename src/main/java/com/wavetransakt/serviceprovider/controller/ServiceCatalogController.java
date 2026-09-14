package com.wavetransakt.serviceprovider.controller;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Category;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyRequest;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailRequest;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailResponse;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassSmileClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassVerificationClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final VtpassCatalogClient vtpassCatalogClient;
    private final VtpassVerificationClient vtpassVerificationClient;
    private final VtpassSmileClient vtpassSmileClient;

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> categories() {
        return ResponseEntity.ok(vtpassCatalogClient.getCategories());
    }

    @GetMapping("/providers")
    public ResponseEntity<List<Provider>> providers(
            @RequestParam String identifier
    ) {
        return ResponseEntity.ok(vtpassCatalogClient.getProviders(identifier));
    }

    @GetMapping("/variations")
    public ResponseEntity<VariationList> variations(
            @RequestParam String serviceId
    ) {
        return ResponseEntity.ok(vtpassCatalogClient.getVariations(serviceId));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(
            @Valid @RequestBody VerifyRequest request
    ) {
        return ResponseEntity.ok(
                vtpassVerificationClient.verify(
                        request.serviceKind(),
                        request.serviceId(),
                        request.customerReference(),
                        request.option()
                )
        );
    }

    @PostMapping("/internet/smile/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifySmileEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        return ResponseEntity.ok(vtpassSmileClient.verifyEmail(request.email()));
    }
}
