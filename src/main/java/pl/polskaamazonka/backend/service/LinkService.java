package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final LinkRepository linkRepository;
    private final ProductRepository productRepository;

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
    public LinkDTO getSocialLinkById(Long id) {
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        return LinkMapper.toDTO(link);
    }

    @Transactional
    public LinkDTO createSocialLink(SaveLinkRequest request) {
        validateSocialPayload(request);
        Link link = new Link();
        link.setUrl(request.getUrl().trim());
        link.setType(SOCIAL_LINK_TYPE);
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        link.setIsBroken(Boolean.FALSE);
        Link saved = linkRepository.save(link);
        return LinkMapper.toDTO(saved);
    }

    @Transactional
    public LinkDTO updateSocialLink(Long id, SaveLinkRequest request) {
        validateSocialPayload(request);
        Link link = linkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureSocialLink(link);
        link.setUrl(request.getUrl().trim());
        link.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
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
        linkRepository.delete(link);
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
}
