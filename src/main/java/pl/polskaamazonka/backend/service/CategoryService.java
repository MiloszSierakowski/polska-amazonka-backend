package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.CategoryDTO;
import pl.polskaamazonka.backend.mapper.CategoryMapper;
import pl.polskaamazonka.backend.model.Category;
import pl.polskaamazonka.backend.repository.CategoryRepository;
import pl.polskaamazonka.backend.repository.VideoCategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final VideoCategoryRepository videoCategoryRepository;
    private final CategoryFileStorageService categoryFileStorageService;

    @Transactional(readOnly = true)
    public List<CategoryDTO> getAll() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDTO getById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryMapper::toDTO)
                .orElse(null);
    }

    @Transactional
    public CategoryDTO create(String name, MultipartFile imageFile) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Category category = new Category();
        category.setName(name.trim());
        if (imageFile != null && !imageFile.isEmpty()) {
            category.setImageUrl(categoryFileStorageService.store(imageFile));
        }
        Category saved = categoryRepository.save(category);
        return CategoryMapper.toDTO(saved);
    }

    @Transactional
    public CategoryDTO update(Long id, String name, MultipartFile imageFile) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        category.setName(name.trim());
        if (imageFile != null && !imageFile.isEmpty()) {
            String previousImageUrl = category.getImageUrl();
            String newImageUrl = categoryFileStorageService.store(imageFile);
            category.setImageUrl(newImageUrl);
            categoryFileStorageService.deleteByPublicUrl(previousImageUrl);
        }
        Category saved = categoryRepository.save(category);
        return CategoryMapper.toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String imageUrl = category.getImageUrl();
        videoCategoryRepository.deleteByCategory_Id(id);
        categoryRepository.delete(category);
        categoryRepository.flush();
        categoryFileStorageService.deleteByPublicUrl(imageUrl);
    }
}
