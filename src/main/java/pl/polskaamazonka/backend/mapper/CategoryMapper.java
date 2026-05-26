package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.config.UploadPaths;
import pl.polskaamazonka.backend.dto.CategoryDTO;
import pl.polskaamazonka.backend.model.Category;

public class CategoryMapper {

    public static CategoryDTO toDTO(Category category) {
        if (category == null) return null;

        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setImageUrl(UploadPaths.normalizeCategoryImageUrl(category.getImageUrl()));
        return dto;
    }
}
