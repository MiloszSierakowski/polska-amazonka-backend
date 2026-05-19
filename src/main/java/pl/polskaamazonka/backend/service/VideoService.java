package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.mapper.VideoMapper;
import pl.polskaamazonka.backend.model.Video;
import pl.polskaamazonka.backend.repository.VideoRepository;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<VideoDTO> getAll(Long categoryId) {
        List<Video> videos = categoryId == null
                ? videoRepository.findAllByOrderByCreatedAtDesc()
                : videoRepository.findAllByCategoryId(categoryId);
        return videos.stream()
                .map(VideoMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public VideoDTO getById(Long id) {
        return videoRepository.findById(id)
                .map(VideoMapper::toDTO)
                .orElse(null);
    }

    public VideoDTO create(VideoDTO dto) {
        if (dto.getTiktokUrl() == null || dto.getTiktokUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String thumbnailUrl = fetchThumbnailUrlFromTikTokOembed(dto.getTiktokUrl());
        Video video = new Video();
        video.setTiktokUrl(dto.getTiktokUrl());
        video.setLocalMp4Url(dto.getLocalMp4Url());
        video.setPreviewImageUrl(thumbnailUrl);
        video.setTitle(dto.getTitle());
        video.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        video.setCreatedAt(Instant.now());
        Video saved = videoRepository.save(video);
        return VideoMapper.toDTO(saved);
    }

    private String fetchThumbnailUrlFromTikTokOembed(String tiktokUrl) {
        try {
            URI uri = UriComponentsBuilder.fromUriString("https://www.tiktok.com/oembed")
                    .queryParam("url", tiktokUrl)
                    .build()
                    .encode()
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "PolskaAmazonka/1.0");
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode thumbnail = root.get("thumbnail_url");
            if (thumbnail == null || thumbnail.isNull() || thumbnail.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            }
            return thumbnail.asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, null, e);
        }
    }
}
