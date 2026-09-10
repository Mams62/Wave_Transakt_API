package com.wavetransakt.serviceprovider.controller;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Category;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final VtpassCatalogClient vtpassCatalogClient;

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> categories() {
        return ResponseEntity.ok(
                vtpassCatalogClient.getCategories()
        );
    }

    @GetMapping("/providers")
    public ResponseEntity<List<Provider>> providers(
            @RequestParam String identifier
    ) {
        return ResponseEntity.ok(
                vtpassCatalogClient.getProviders(identifier)
        );
    }

    @GetMapping("/variations")
    public ResponseEntity<VariationList> variations(
            @RequestParam String serviceId
    ) {
        return ResponseEntity.ok(
                vtpassCatalogClient.getVariations(serviceId)
        );
    }
}
