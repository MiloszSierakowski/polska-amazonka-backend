package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.ShopDTO;
import pl.polskaamazonka.backend.mapper.ShopMapper;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public List<ShopDTO> getAll() {
        return shopRepository.findAllByOrderByNameAsc().stream()
                .map(ShopMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopDTO> getAllActive() {
        return shopRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(ShopMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopDTO getById(Long id) {
        return shopRepository.findById(id)
                .map(ShopMapper::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ShopDTO create(ShopDTO dto) {
        validatePayload(dto);
        String slug = normalizeSlug(dto.getSlug());
        String code = normalizeCode(dto.getCode());
        ensureUniqueSlug(slug, null);
        ensureUniqueCode(code, null);
        Shop shop = new Shop();
        shop.setSlug(slug);
        shop.setCode(code);
        shop.setName(dto.getName().trim());
        shop.setShopUrl(normalizeOptionalText(dto.getShopUrl()));
        shop.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        Shop saved = shopRepository.save(shop);
        categoryService.createForShop(saved);
        return ShopMapper.toDTO(saved);
    }

    @Transactional
    public ShopDTO update(Long id, ShopDTO dto) {
        validatePayload(dto);
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String slug = normalizeSlug(dto.getSlug());
        String code = normalizeCode(dto.getCode());
        ensureUniqueSlug(slug, id);
        ensureUniqueCode(code, id);
        String previousName = shop.getName();
        shop.setSlug(slug);
        shop.setCode(code);
        shop.setName(dto.getName().trim());
        shop.setShopUrl(normalizeOptionalText(dto.getShopUrl()));
        shop.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        Shop saved = shopRepository.save(shop);
        if (!saved.getName().equals(previousName)) {
            categoryService.syncNameWithShop(saved);
        }
        return ShopMapper.toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        categoryService.deleteLinkedToShop(shop);
        shopRepository.delete(shop);
    }

    private void validatePayload(ShopDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getSlug() == null || dto.getSlug().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureUniqueSlug(String slug, Long excludeId) {
        shopRepository.findBySlug(slug).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        });
    }

    private void ensureUniqueCode(String code, Long excludeId) {
        shopRepository.findByCode(code).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        });
    }

    private String normalizeSlug(String value) {
        return value.trim().toLowerCase();
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
