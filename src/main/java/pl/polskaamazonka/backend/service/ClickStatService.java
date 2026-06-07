package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.dto.ClickStatAggregationDTO;
import pl.polskaamazonka.backend.model.ClickStat;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.repository.ClickStatRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClickStatService {

    private static final String ENTITY_TYPE_PRODUCT = "product";
    private static final String ENTITY_TYPE_SHOP = "shop";

    private final ClickStatRepository clickStatRepository;
    private final ProductRepository productRepository;
    private final ShopUrlMatcherService shopUrlMatcherService;

    public List<ClickStat> getAll() {
        return clickStatRepository.findAll();
    }

    public ClickStat getById(Long id) {
        return clickStatRepository.findById(id).orElse(null);
    }

    @Transactional
    public void recordClick(String entityType, Long entityId) {
        persistClick(entityType, entityId);

        if (!isProductEntityType(entityType)) {
            return;
        }

        productRepository.findByIdWithProductLink(entityId).ifPresent(product -> {
            String productUrl = resolveProductUrl(product);
            if (productUrl == null) {
                return;
            }
            shopUrlMatcherService.detectShopFromUrl(productUrl)
                    .map(Shop::getId)
                    .ifPresent(shopId -> persistClick(ENTITY_TYPE_SHOP, shopId));
        });
    }

    public List<ClickStatAggregationDTO> getAggregatedStats(Instant from, Instant to) {
        return clickStatRepository.countGroupedByEntityBetween(from, to).stream()
                .map(row -> new ClickStatAggregationDTO(
                        row.getEntityType(),
                        row.getEntityId(),
                        row.getClickCount()
                ))
                .toList();
    }

    private void persistClick(String entityType, Long entityId) {
        ClickStat clickStat = new ClickStat();
        clickStat.setEntityType(entityType);
        clickStat.setEntityId(entityId);
        clickStat.setClickedAt(Instant.now());
        clickStatRepository.save(clickStat);
    }

    private boolean isProductEntityType(String entityType) {
        return entityType != null
                && ENTITY_TYPE_PRODUCT.equals(entityType.trim().toLowerCase(Locale.ROOT));
    }

    private String resolveProductUrl(Product product) {
        if (product == null || product.getProductLink() == null) {
            return null;
        }
        String url = product.getProductLink().getUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }
}
