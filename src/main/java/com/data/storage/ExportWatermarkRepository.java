package com.data.storage;

import com.data.shared.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportWatermarkRepository extends JpaRepository<ExportWatermarkEntity, DataSource> {
}
