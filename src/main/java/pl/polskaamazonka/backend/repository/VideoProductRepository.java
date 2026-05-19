package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.VideoProduct;

import java.util.List;

public interface VideoProductRepository extends JpaRepository<VideoProduct, Integer> {

    @Query("SELECT vp FROM VideoProduct vp JOIN FETCH vp.product p LEFT JOIN FETCH p.productLink WHERE vp.video.id = :videoId")
    List<VideoProduct> findByVideo_Id(@Param("videoId") Long videoId);

    long countByProduct_Id(Long productId);
}
