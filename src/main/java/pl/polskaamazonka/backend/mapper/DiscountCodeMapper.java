package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.DiscountCodeDTO;
import pl.polskaamazonka.backend.dto.PublicDiscountCodeDTO;
import pl.polskaamazonka.backend.model.AffiliateCode;

public class DiscountCodeMapper {

    public static DiscountCodeDTO toDTO(AffiliateCode entity) {
        if (entity == null) {
            return null;
        }
        DiscountCodeDTO dto = new DiscountCodeDTO();
        dto.setId(entity.getId());
        dto.setPlatform(entity.getPlatform() != null ? entity.getPlatform().name() : null);
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
        dto.setPlatform(entity.getPlatform() != null ? entity.getPlatform().name() : null);
        dto.setCodeValue(entity.getCodeValue());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
