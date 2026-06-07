package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopUrlMatcherService {

    private final ShopRepository shopRepository;

    public Optional<Shop> detectShopFromUrl(String productUrl) {
        if (productUrl == null || productUrl.isBlank()) {
            return Optional.empty();
        }

        String normalizedUrl = productUrl.trim().toLowerCase(Locale.ROOT);
        List<Shop> activeShops = shopRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .sorted(Comparator.comparingInt((Shop shop) -> shop.getSlug().length()).reversed())
                .toList();

        for (Shop shop : activeShops) {
            if (urlMatchesShop(normalizedUrl, shop)) {
                return Optional.of(shop);
            }
        }

        return Optional.empty();
    }

    private boolean urlMatchesShop(String url, Shop shop) {
        String slug = shop.getSlug();
        if (slug != null && !slug.isBlank() && url.contains(slug.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }

        String code = shop.getCode();
        if (code != null && !code.isBlank() && url.contains(code.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }

        String name = shop.getName();
        if (name == null || name.isBlank()) {
            return false;
        }

        String nameKey = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return !nameKey.isEmpty() && url.contains(nameKey);
    }
}
