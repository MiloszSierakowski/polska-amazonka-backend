package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.PublicProductDto;
import pl.polskaamazonka.backend.service.ProductRedirectService;
import pl.polskaamazonka.backend.service.ProductService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/public/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductRedirectService productRedirectService;
    private final ProductService productService;

    @GetMapping("/search")
    public List<PublicProductDto> search(@RequestParam String search) {
        return productService.searchPublic(search);
    }

    @GetMapping("/{productId}/redirect")
    public ResponseEntity<Void> redirect(@PathVariable Long productId) {
        String targetUrl = productRedirectService.resolveRedirectUrl(productId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .build();
    }
}
