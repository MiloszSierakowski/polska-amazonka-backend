package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "product_link_id", nullable = false)
    private Link productLink;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VideoProduct> videoProducts;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductTag> tags = new ArrayList<>();

    public void replaceTags(List<String> values) {
        Map<String, ProductTag> existingTagsByValue = new LinkedHashMap<>();
        for (ProductTag tag : tags) {
            existingTagsByValue.put(tag.getValue().toLowerCase(Locale.ROOT), tag);
        }

        List<ProductTag> replacement = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            ProductTag tag = existingTagsByValue.remove(value.toLowerCase(Locale.ROOT));
            if (tag == null) {
                tag = new ProductTag();
            }
            tag.setProduct(this);
            tag.setValue(value);
            tag.setDisplayOrder(index);
            replacement.add(tag);
        }

        tags.clear();
        tags.addAll(replacement);
    }
}
