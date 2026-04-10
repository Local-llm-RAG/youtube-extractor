package com.data.storage;

import com.data.oai.pipeline.DataSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "export_watermark")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportWatermarkEntity {

    @Id
    @Column(name = "data_source", length = 128)
    @Enumerated(EnumType.STRING)
    private DataSource dataSource;

    @Column(name = "exported_at", nullable = false)
    private OffsetDateTime exportedAt;
}
