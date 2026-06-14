package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.PublicProductDto;
import pl.polskaamazonka.backend.mapper.ProductMapper;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.ProductRepository;

import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int PUBLIC_SEARCH_LIMIT = 25;

    private final ProductRepository productRepository;

    public List<ProductDTO> getAll() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    public ProductDTO getById(Long id) {
        return productRepository.findById(id)
                .map(ProductMapper::toDTO)
                .orElse(null);
    }

    public List<PublicProductDto> searchPublic(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return productRepository.searchPublicByName(
                        search.trim(),
                        PageRequest.of(0, PUBLIC_SEARCH_LIMIT)
                )
                .stream()
                .map(this::toPublicDto)
                .toList();
    }

    private PublicProductDto toPublicDto(Product product) {
        PublicProductDto dto = new PublicProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        return dto;
    }
}
