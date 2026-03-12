package pl.polskaamazonka.backend.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoDTO {
    private Integer id;
    private String tiktokUrl;
    private String localMp4Url;
    private String previewImageUrl;
    private String title;
    private Boolean isActive;
}
