package dev.simplecore.searchable.test.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "test_json_field_entity")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestJsonFieldEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "description_i18n", columnDefinition = "TEXT")
    @Convert(converter = MapToJsonConverter.class)
    @Builder.Default
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Column(name = "metadata", columnDefinition = "TEXT")
    @Convert(converter = MapToJsonConverter.class)
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}