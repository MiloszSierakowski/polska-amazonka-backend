package pl.polskaamazonka.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.service.VideoService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicVideoControllerByCodeTest {

    @Test
    void getByPublicCodeDelegatesToService() {
        VideoService videoService = mock(VideoService.class);
        PublicVideoController controller = new PublicVideoController(videoService);
        PublicVideoDTO dto = new PublicVideoDTO();
        dto.setPublicCode("A110");
        when(videoService.getByPublicCodePublic("A110")).thenReturn(dto);

        PublicVideoDTO result = controller.getByPublicCode("A110");

        assertEquals(dto, result);
        verify(videoService).getByPublicCodePublic("A110");
    }

    @Test
    void byCodeEndpointUsesDedicatedPathSegment() throws Exception {
        Method method = PublicVideoController.class.getDeclaredMethod("getByPublicCode", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertNotNull(mapping);
        assertEquals("/by-code/{publicCode}", mapping.value()[0]);
    }
}
