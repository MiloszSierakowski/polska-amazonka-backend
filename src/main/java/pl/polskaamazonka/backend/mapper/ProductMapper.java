package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.PublicVideoProductDTO;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.VideoProduct;

public class ProductMapper {

    public static ProductDTO toDTO(Product product) {
        if (product == null) return null;

        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        dto.setProductLinkId(
                product.getProductLink() != null ? product.getProductLink().getId() : null
        );
        dto.setProductLink(LinkMapper.toDTO(product.getProductLink()));
        dto.setTags(product.getTags() == null
                ? java.util.List.of()
                : product.getTags().stream().map(tag -> tag.getValue()).toList());
        return dto;
    }

    public static ProductDTO toDTO(VideoProduct videoProduct) {
        if (videoProduct == null) return null;

        ProductDTO dto = toDTO(videoProduct.getProduct());
        if (dto != null) {
            dto.setPromoCode(videoProduct.getPromoCode());
        }
        return dto;
    }

    public static PublicVideoProductDTO toPublicVideoDTO(VideoProduct videoProduct, boolean includePromoCode) {
        if (videoProduct == null || videoProduct.getProduct() == null) return null;

        Product product = videoProduct.getProduct();
        PublicVideoProductDTO dto = new PublicVideoProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        dto.setProductLinkId(product.getProductLink() != null ? product.getProductLink().getId() : null);
        dto.setProductLink(LinkMapper.toDTO(product.getProductLink()));
        dto.setPromoCode(includePromoCode ? videoProduct.getPromoCode() : null);
        return dto;
    }
}
