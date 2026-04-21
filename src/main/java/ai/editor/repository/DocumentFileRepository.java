package ai.editor.repository;

import ai.editor.entity.DocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {
}
