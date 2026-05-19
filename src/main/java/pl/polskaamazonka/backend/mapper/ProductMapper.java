package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.model.Product;

public class ProductMapper {

    public static ProductDTO toDTO(Product product) {
        if (product == null) return null;

        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        dto.setProductLinkId(
                product.getProductLink() != null && product.getProductLink().getId() != null
                        ? product.getProductLink().getId().longValue()
                        : null
        );
        dto.setProductLink(LinkMapper.toDTO(product.getProductLink()));
        return dto;
    }
}
