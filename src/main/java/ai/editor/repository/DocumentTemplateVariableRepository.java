package ai.editor.repository;

import ai.editor.entity.DocumentTemplateVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTemplateVariableRepository extends JpaRepository<DocumentTemplateVariable, Long> {

    List<DocumentTemplateVariable> findAllByDocumentTemplateIdOrderByNameAsc(Long templateId);

    Optional<DocumentTemplateVariable> findByIdAndDocumentTemplateId(Long variableId, Long templateId);
}
