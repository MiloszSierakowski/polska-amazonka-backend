package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "video")
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiktok_url", length = 500)
    private String tiktokUrl;

    @Column(name = "local_mp4_url", length = 500)
    private String localMp4Url;

    @Column(name = "preview_image_url", length = 500)
    private String previewImageUrl;

    @Column(name = "title")
    private String title;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VideoProduct> videoProducts;

}
