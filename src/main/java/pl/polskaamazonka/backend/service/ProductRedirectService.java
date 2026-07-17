package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductRedirectService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public String resolveRedirectUrl(Long productId) {
        Product product = productRepository.findByIdWithProductLink(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Link productLink = product.getProductLink();
        if (productLink == null || productLink.getUrl() == null || productLink.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return productLink.getUrl().trim();
    }
}
