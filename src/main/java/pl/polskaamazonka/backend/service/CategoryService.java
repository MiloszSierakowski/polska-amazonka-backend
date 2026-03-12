package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.CategoryDTO;
import pl.polskaamazonka.backend.mapper.CategoryMapper;
import pl.polskaamazonka.backend.repository.CategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> getAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryMapper::toDTO)
                .toList();
    }

    public CategoryDTO getById(Integer id) {
        return categoryRepository.findById(id)
                .map(CategoryMapper::toDTO)
                .orElse(null);
    }
}
