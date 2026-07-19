package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.AdminProductSearchResultDTO;
import pl.polskaamazonka.backend.mapper.LinkMapper;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.VideoProductRepository;
import pl.polskaamazonka.backend.repository.VideoRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 25;

    private final ProductRepository productRepository;
    private final VideoProductRepository videoProductRepository;
    private final VideoRepository videoRepository;

    @Transactional(readOnly = true)
    public List<AdminProductSearchResultDTO> search(
            String rawQuery,
            Long videoId,
            Integer requestedPage,
            Integer requestedLimit
    ) {
        if (videoId == null || !videoRepository.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Film nie istnieje.");
        }
        String query = rawQuery == null ? "" : rawQuery.trim();
        int page = requestedPage == null ? 0 : Math.max(0, requestedPage);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<Long> ids = productRepository.searchAdminProductIds(query, PageRequest.of(page, limit));
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productsById = new HashMap<>();
        productRepository.findByIdIn(ids).forEach(product -> productsById.put(product.getId(), product));
        Set<Long> assignedIds = new HashSet<>(videoProductRepository.findProductIdsAssignedToVideo(videoId, ids));

        return ids.stream()
                .map(productsById::get)
                .filter(product -> product != null)
                .map(product -> toResult(product, assignedIds.contains(product.getId())))
                .toList();
    }

    private AdminProductSearchResultDTO toResult(Product product, boolean alreadyAssigned) {
        Link link = product.getProductLink();
        AdminProductSearchResultDTO dto = new AdminProductSearchResultDTO();
        dto.setProductId(product.getId());
        dto.setName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        dto.setTags(product.getTags() == null
                ? List.of()
                : product.getTags().stream().map(tag -> tag.getValue()).toList());
        dto.setProductLink(LinkMapper.toDTO(link));
        dto.setIsBroken(link != null ? link.getIsBroken() : null);
        dto.setNeedsReview(link != null ? link.getNeedsReview() : null);
        dto.setIsActive(link != null ? link.getIsActive() : null);
        dto.setLastCheckedAt(link != null ? link.getLastCheckedAt() : null);
        dto.setAlreadyAssigned(alreadyAssigned);
        return dto;
    }
}
