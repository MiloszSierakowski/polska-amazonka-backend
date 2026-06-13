package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Videocategory;

public interface VideoCategoryRepository extends JpaRepository<Videocategory, Long> {
    void deleteByVideo_Id(Long videoId);

    void deleteByCategory_Id(Long categoryId);

    long countByVideo_Id(Long videoId);
}
