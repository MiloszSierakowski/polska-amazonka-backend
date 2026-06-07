package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.ShopDTO;
import pl.polskaamazonka.backend.model.Shop;

public class ShopMapper {

    public static ShopDTO toDTO(Shop entity) {
        if (entity == null) {
            return null;
        }
        ShopDTO dto = new ShopDTO();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setShopUrl(entity.getShopUrl());
        dto.setIsActive(entity.getIsActive());
        dto.setColorCode(entity.getColorCode());
        return dto;
    }
}
