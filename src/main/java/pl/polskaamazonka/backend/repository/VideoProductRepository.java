package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.VideoProduct;

public interface VideoProductRepository extends JpaRepository<VideoProduct, Integer> {
}
