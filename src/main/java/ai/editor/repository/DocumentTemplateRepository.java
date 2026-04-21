package ai.editor.repository;

import ai.editor.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

    List<DocumentTemplate> findAllByOrderByCreatedAtDesc();
}
