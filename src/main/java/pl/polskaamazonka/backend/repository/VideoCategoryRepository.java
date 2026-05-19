package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Videocategory;

public interface VideoCategoryRepository extends JpaRepository<Videocategory, Integer> {
    void deleteByVideo_Id(Long videoId);
}
