package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.DiscountCodeDTO;
import pl.polskaamazonka.backend.dto.PublicDiscountCodeDTO;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;

public class DiscountCodeMapper {

    public static DiscountCodeDTO toDTO(AffiliateCode entity) {
        if (entity == null) {
            return null;
        }
        DiscountCodeDTO dto = new DiscountCodeDTO();
        dto.setId(entity.getId());
        Shop shop = entity.getShop();
        if (shop != null) {
            dto.setShopId(shop.getId());
            dto.setShopName(shop.getName());
            dto.setShopSlug(shop.getSlug());
        }
        dto.setCodeValue(entity.getCodeValue());
        dto.setDescription(entity.getDescription());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public static PublicDiscountCodeDTO toPublicDTO(AffiliateCode entity) {
        if (entity == null) {
            return null;
        }
        PublicDiscountCodeDTO dto = new PublicDiscountCodeDTO();
        dto.setId(entity.getId());
        Shop shop = entity.getShop();
        if (shop != null) {
            dto.setShopId(shop.getId());
            dto.setShopName(shop.getName());
            dto.setShopSlug(shop.getSlug());
        }
        dto.setCodeValue(entity.getCodeValue());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
