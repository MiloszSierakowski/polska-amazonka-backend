package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.CategoryDTO;
import pl.polskaamazonka.backend.exception.ShopCategoryDeletionException;
import pl.polskaamazonka.backend.mapper.CategoryMapper;
import pl.polskaamazonka.backend.model.Category;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.repository.CategoryRepository;
import pl.polskaamazonka.backend.repository.VideoCategoryRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String SHOP_CATEGORY_DELETION_MESSAGE =
            "Nie można usunąć kategorii powiązanej ze sklepem. Usuń najpierw sklep.";

    private final CategoryRepository categoryRepository;
    private final VideoCategoryRepository videoCategoryRepository;
    private final CategoryFileStorageService categoryFileStorageService;

    @Transactional(readOnly = true)
    public List<CategoryDTO> getAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
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
        category.setDisplayOrder(nextDisplayOrder());
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
    public void reorder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Set<Long> uniqueIds = new HashSet<>(orderedIds);
        if (uniqueIds.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        long categoryCount = categoryRepository.count();
        if (orderedIds.size() != categoryCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<Category> categories = categoryRepository.findAllById(orderedIds);
        if (categories.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Map<Long, Category> byId = new HashMap<>();
        for (Category category : categories) {
            byId.put(category.getId(), category);
        }
        for (int index = 0; index < orderedIds.size(); index++) {
            Category category = byId.get(orderedIds.get(index));
            if (category == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            category.setDisplayOrder((long) index);
        }
        categoryRepository.saveAll(categories);
    }

    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (category.getShop() != null) {
            throw new ShopCategoryDeletionException(SHOP_CATEGORY_DELETION_MESSAGE);
        }
        performDelete(category);
    }

    @Transactional
    public void deleteLinkedToShop(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        categoryRepository.findByShop_Id(shop.getId())
                .ifPresent(this::performDelete);
    }

    @Transactional
    public Category createForShop(Shop shop) {
        Category category = new Category();
        category.setName(shop.getName());
        category.setShop(shop);
        category.setDisplayOrder(nextDisplayOrder());
        return categoryRepository.save(category);
    }

    @Transactional
    public void syncNameWithShop(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        categoryRepository.findByShop_Id(shop.getId()).ifPresent(category -> {
            category.setName(shop.getName());
            categoryRepository.save(category);
        });
    }

    private Long nextDisplayOrder() {
        return categoryRepository.findTopByOrderByDisplayOrderDesc()
                .map(category -> category.getDisplayOrder() + 1)
                .orElse(0L);
    }

    private void performDelete(Category category) {
        String imageUrl = category.getImageUrl();
        videoCategoryRepository.deleteByCategory_Id(category.getId());
        categoryRepository.delete(category);
        categoryRepository.flush();
        categoryFileStorageService.deleteByPublicUrl(imageUrl);
    }
}
