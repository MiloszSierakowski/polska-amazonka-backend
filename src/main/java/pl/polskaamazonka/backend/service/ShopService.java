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

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final CategoryService categoryService;
    private final ActivityLogService activityLogService;

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
        String slug = resolveSlugForName(dto.getName().trim(), null);
        String code = codeFromSlug(slug);
        Shop shop = new Shop();
        shop.setSlug(slug);
        shop.setCode(code);
        shop.setName(dto.getName().trim());
        shop.setShopUrl(normalizeOptionalText(dto.getShopUrl()));
        shop.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        shop.setColorCode(normalizeColorCode(dto.getColorCode()));
        Shop saved = shopRepository.save(shop);
        categoryService.createForShop(saved);
        activityLogService.logAction("UTWORZENIE_SKLEPU", "Dodano sklep: " + saved.getName() + " (ID: " + saved.getId() + ")");
        return ShopMapper.toDTO(saved);
    }

    @Transactional
    public ShopDTO update(Long id, ShopDTO dto) {
        validatePayload(dto);
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String slug = resolveSlugForName(dto.getName().trim(), id);
        String code = codeFromSlug(slug);
        String previousName = shop.getName();
        shop.setSlug(slug);
        shop.setCode(code);
        shop.setName(dto.getName().trim());
        shop.setShopUrl(normalizeOptionalText(dto.getShopUrl()));
        shop.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        shop.setColorCode(normalizeColorCode(dto.getColorCode()));
        Shop saved = shopRepository.save(shop);
        if (!saved.getName().equals(previousName)) {
            categoryService.syncNameWithShop(saved);
        }
        activityLogService.logAction("EDYCJA_SKLEPU", "Zaktualizowano sklep o ID: " + id + " (Nazwa: " + saved.getName() + ")");
        return ShopMapper.toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        activityLogService.logAction("USUNIĘCIE_SKLEPU", "Usunięto sklep: " + shop.getName() + " (ID: " + id + ")");
        categoryService.deleteLinkedToShop(shop);
        shopRepository.delete(shop);
    }

    private void validatePayload(ShopDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveSlugForName(String name, Long excludeId) {
        String baseSlug = slugFromName(name);
        String candidate = baseSlug;
        int suffix = 2;
        while (isSlugTaken(candidate, excludeId)) {
            candidate = baseSlug + suffix;
            suffix++;
            if (suffix > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, Long excludeId) {
        return shopRepository.findBySlug(slug)
                .map(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .orElse(false);
    }

    private String slugFromName(String name) {
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return slug;
    }

    private String codeFromSlug(String slug) {
        return slug.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeColorCode(String value) {
        if (value == null || value.isBlank()) {
            return "#64748B";
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (!trimmed.matches("#[0-9A-Fa-f]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
