package pl.polskaamazonka.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.dto.PublicVideoProductDTO;
import pl.polskaamazonka.backend.service.PublicVideoPreviewService;
import pl.polskaamazonka.backend.service.VideoService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicVideoPreviewControllerTest {

    private VideoService videoService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        videoService = mock(VideoService.class);
        PublicVideoPreviewService previewService = new PublicVideoPreviewService(
                videoService,
                "https://amalinki.example"
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicVideoPreviewController(previewService))
                .build();
    }

    @Test
    void publicVideoReturnsDynamicMetadataWithoutProductData() throws Exception {
        PublicVideoDTO video = video("A110", "Film & okazje", "/uploads/preview.jpg");
        PublicVideoProductDTO product = new PublicVideoProductDTO();
        product.setName("TAJNA_NAZWA_PRODUKTU");
        product.setPromoCode("TAJNY_KOD");
        video.setProducts(List.of(product));
        when(videoService.getByPublicCodePublic("A110")).thenReturn(video);

        mockMvc.perform(get("/api/public/video-preview/A110"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("Film &amp; okazje | Polskie Amalinki")))
                .andExpect(content().string(containsString("https://amalinki.example/amafilmy/A110")))
                .andExpect(content().string(containsString("https://amalinki.example/uploads/preview.jpg")))
                .andExpect(content().string(not(containsString("TAJNA_NAZWA_PRODUKTU"))))
                .andExpect(content().string(not(containsString("TAJNY_KOD"))));
    }

    @Test
    void missingPreviewImageUsesGlobalFallback() throws Exception {
        when(videoService.getByPublicCodePublic("A111"))
                .thenReturn(video("A111", "Film", "  "));

        mockMvc.perform(get("/api/public/video-preview/A111"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "https://amalinki.example/assets/logo/polskie-amalinki-og.png"
                )));
    }

    @Test
    void titleIsEscapedBeforeItIsInsertedIntoHtml() throws Exception {
        when(videoService.getByPublicCodePublic("A112"))
                .thenReturn(video("A112", "<script>alert(\"x\")</script>", null));

        mockMvc.perform(get("/api/public/video-preview/A112"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString(
                        "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;"
                )));
    }

    @Test
    void missingVideoReturnsNotFound() throws Exception {
        when(videoService.getByPublicCodePublic("A404"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/public/video-preview/A404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonPublicVideoReturnsNotFound() throws Exception {
        when(videoService.getByPublicCodePublic("A403"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/public/video-preview/A403"))
                .andExpect(status().isNotFound());
    }

    private static PublicVideoDTO video(String publicCode, String title, String previewImageUrl) {
        PublicVideoDTO video = new PublicVideoDTO();
        video.setPublicCode(publicCode);
        video.setTitle(title);
        video.setPreviewImageUrl(previewImageUrl);
        return video;
    }
}
