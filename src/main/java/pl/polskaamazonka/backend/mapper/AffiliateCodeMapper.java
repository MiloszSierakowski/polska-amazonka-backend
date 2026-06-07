package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;

public class AffiliateCodeMapper {

    public static AffiliateCodeDTO toDTO(AffiliateCode entity) {
        if (entity == null) {
            return null;
        }
        AffiliateCodeDTO dto = new AffiliateCodeDTO();
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
}
