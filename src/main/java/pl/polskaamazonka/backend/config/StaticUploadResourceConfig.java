package pl.polskaamazonka.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class StaticUploadResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadsRoot = UploadPaths.resolveUploadsRoot();
        String uploadsLocation = toResourceLocation(uploadsRoot);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsLocation);

        Path categoriesDirectory = UploadPaths.resolveCategoriesDirectory();
        String categoriesLocation = toResourceLocation(categoriesDirectory);
        registry.addResourceHandler("/categories/**")
                .addResourceLocations(categoriesLocation);
    }

    private String toResourceLocation(Path path) {
        String location = path.toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        return location;
    }
}
