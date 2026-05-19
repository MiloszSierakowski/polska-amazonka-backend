package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.Video;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findAllByOrderByCreatedAtDesc();

    @Query("SELECT vc.video FROM Videocategory vc WHERE vc.category.id = :categoryId ORDER BY vc.video.createdAt DESC")
    List<Video> findAllByCategoryId(@Param("categoryId") Long categoryId);
}
