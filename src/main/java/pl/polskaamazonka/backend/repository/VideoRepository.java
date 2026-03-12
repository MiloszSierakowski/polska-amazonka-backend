package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Video;

public interface VideoRepository extends JpaRepository<Video, Integer> {
}
