package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.dto.SaveLinkRequest;
import pl.polskaamazonka.backend.mapper.LinkMapper;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkService {

    public static final String SOCIAL_LINK_TYPE = "social";
    private static final int MAX_ACTIVE_SOCIAL_LINKS = 4;

    private final LinkRepository linkRepository;
    private final ProductRepository productRepository;
    private final SocialLinkImageStorageService socialLinkImageStorageService;

    @Transactional(readOnly = true)
    public List<LinkDTO> getAll() {
        return linkRepository.findAll().stream()
                .map(LinkMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkDTO getById(Long id) {
        return linkRepository.findById(id)
                .map(LinkMapper::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LinkDTO> getByType(String type) {
        return linkRepository.findByType(type).stream()
                .map(LinkMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkDTO> getPublicByType(String type) {
        if (SOCIAL_LINK_TYPE.equals(type)) {
            return linkRepository.findByTypeAndIsActiveTrueOrderByDisplayOrderAscIdAsc(type).stream()
                    .map(LinkMapper::toDTO)
                    .toList();
        }
        return getByType(type);
    }

    @Transactional(readOnly = true)
    public List<LinkDTO> getSocialLinksForAdmin() {
        return linkRepository.findByTypeOrderByDisplayOrderAscIdAsc(SOCIAL_LINK_TYPE).stream()
                .map(LinkMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkDTO getSocialLinkById(Long id) {
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        return LinkMapper.toDTO(link);
    }

    @Transactional
    public LinkDTO createSocialLink(SaveLinkRequest request) {
        validateSocialPayload(request);
        ensureActiveSocialLimit(request.getIsActive(), null);
        Link link = new Link();
        link.setUrl(request.getUrl().trim());
        link.setType(SOCIAL_LINK_TYPE);
        link.setImagePath(blankToNull(request.getImagePath()));
        link.setDisplayOrder(resolveDisplayOrder(request.getDisplayOrder()));
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        link.setIsBroken(Boolean.FALSE);
        Link saved = linkRepository.save(link);
        return LinkMapper.toDTO(saved);
    }

    @Transactional
    public LinkDTO createSocialLink(String url, Boolean isActive, MultipartFile imageFile) {
        SaveLinkRequest request = new SaveLinkRequest();
        request.setUrl(url);
        request.setIsActive(isActive);
        validateSocialPayload(request);
        ensureActiveSocialLimit(request.getIsActive(), null);
        Link link = new Link();
        link.setUrl(request.getUrl().trim());
        link.setType(SOCIAL_LINK_TYPE);
        link.setDisplayOrder(nextSocialDisplayOrder());
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        link.setIsBroken(Boolean.FALSE);
        if (imageFile != null && !imageFile.isEmpty()) {
            link.setImagePath(socialLinkImageStorageService.store(imageFile));
        }
        Link saved = linkRepository.save(link);
        return LinkMapper.toDTO(saved);
    }

    @Transactional
    public LinkDTO updateSocialLink(Long id, SaveLinkRequest request) {
        validateSocialPayload(request);
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        ensureActiveSocialLimit(request.getIsActive(), id);
        link.setUrl(request.getUrl().trim());
        link.setImagePath(blankToNull(request.getImagePath()));
        link.setDisplayOrder(resolveDisplayOrder(request.getDisplayOrder(), link.getDisplayOrder()));
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        Link saved = linkRepository.save(link);
        return LinkMapper.toDTO(saved);
    }

    @Transactional
    public LinkDTO updateSocialLink(Long id, String url, Boolean isActive, MultipartFile imageFile) {
        SaveLinkRequest request = new SaveLinkRequest();
        request.setUrl(url);
        request.setIsActive(isActive);
        validateSocialPayload(request);
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        ensureActiveSocialLimit(request.getIsActive(), id);
        link.setUrl(request.getUrl().trim());
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        if (imageFile != null && !imageFile.isEmpty()) {
            String previousImagePath = link.getImagePath();
            link.setImagePath(socialLinkImageStorageService.store(imageFile));
            socialLinkImageStorageService.deleteByPublicUrl(previousImagePath);
        }
        Link saved = linkRepository.save(link);
        return LinkMapper.toDTO(saved);
    }

    @Transactional
    public void deleteSocialLink(Long id) {
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        if (productRepository.countByProductLink_Id(id) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nie można usunąć linku powiązanego z produktem."
            );
        }
        socialLinkImageStorageService.deleteByPublicUrl(link.getImagePath());
        linkRepository.delete(link);
    }

    @Transactional
    public void reorderSocialLinks(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        List<Link> links = linkRepository.findAllById(orderedIds);
        if (links.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowa lista linków.");
        }
        links.forEach(this::ensureSocialLink);
        for (int index = 0; index < orderedIds.size(); index++) {
            Long id = orderedIds.get(index);
            long displayOrder = index;
            links.stream()
                    .filter(link -> link.getId().equals(id))
                    .findFirst()
                    .ifPresent(link -> link.setDisplayOrder(displayOrder));
        }
        linkRepository.saveAll(links);
    }

    private void validateSocialPayload(SaveLinkRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak danych linku.");
        }
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adres URL jest wymagany.");
        }
        String url = request.getUrl().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Adres URL musi zaczynać się od http:// lub https://"
            );
        }
    }

    private void ensureSocialLink(Link link) {
        if (!SOCIAL_LINK_TYPE.equals(link.getType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operacja dotyczy wyłącznie linków typu social."
            );
        }
    }

    private void ensureActiveSocialLimit(Boolean requestedActive, Long excludedId) {
        boolean active = requestedActive == null || requestedActive;
        if (!active) {
            return;
        }
        long activeCount = excludedId == null
                ? linkRepository.countByTypeAndIsActiveTrue(SOCIAL_LINK_TYPE)
                : linkRepository.countByTypeAndIsActiveTrueAndIdNot(SOCIAL_LINK_TYPE, excludedId);
        if (activeCount >= MAX_ACTIVE_SOCIAL_LINKS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Można włączyć maksymalnie 4 linki social media."
            );
        }
    }

    private Long nextSocialDisplayOrder() {
        return linkRepository.findByTypeOrderByDisplayOrderAscIdAsc(SOCIAL_LINK_TYPE).stream()
                .map(Link::getDisplayOrder)
                .filter(order -> order != null)
                .max(Long::compareTo)
                .map(order -> order + 1)
                .orElse(0L);
    }

    private Long resolveDisplayOrder(Long requestedDisplayOrder) {
        return requestedDisplayOrder != null ? requestedDisplayOrder : nextSocialDisplayOrder();
    }

    private Long resolveDisplayOrder(Long requestedDisplayOrder, Long currentDisplayOrder) {
        if (requestedDisplayOrder != null) {
            return requestedDisplayOrder;
        }
        return currentDisplayOrder != null ? currentDisplayOrder : 0L;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
