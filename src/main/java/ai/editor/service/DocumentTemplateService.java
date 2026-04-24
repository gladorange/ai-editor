package ai.editor.service;

import ai.editor.entity.DocumentFile;
import ai.editor.entity.DocumentTemplate;
import ai.editor.entity.DocumentTemplateVariable;
import ai.editor.model.DocumentDownloadData;
import ai.editor.model.DocumentTemplateDetails;
import ai.editor.model.DocumentTemplateListItem;
import ai.editor.model.DocumentTemplateVariableItem;
import ai.editor.repository.DocumentFileRepository;
import ai.editor.repository.DocumentTemplateRepository;
import ai.editor.repository.DocumentTemplateVariableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentTemplateService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\{([a-zA-Zа-яА-Я0-9_]+)\\}\\}\\}");

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentTemplateVariableRepository documentTemplateVariableRepository;
    private final DocxConversionService docxConversionService;

    public DocumentTemplateService(
            DocumentTemplateRepository documentTemplateRepository,
            DocumentFileRepository documentFileRepository,
            DocumentTemplateVariableRepository documentTemplateVariableRepository,
            DocxConversionService docxConversionService
    ) {
        this.documentTemplateRepository = documentTemplateRepository;
        this.documentFileRepository = documentFileRepository;
        this.documentTemplateVariableRepository = documentTemplateVariableRepository;
        this.docxConversionService = docxConversionService;
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateListItem> findAll() {
        return documentTemplateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(template -> new DocumentTemplateListItem(
                        template.getId(),
                        template.getName(),
                        template.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentTemplateDetails getDetails(Long templateId) {
        DocumentTemplate template = documentTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NoSuchElementException("Document template not found: " + templateId));

        DocumentFile sourceFile = template.getSourceFile();
        return new DocumentTemplateDetails(
                template.getId(),
                template.getName(),
                template.getCreatedAt(),
                sourceFile.getId(),
                sourceFile.getFileName(),
                sourceFile.getContentType(),
                template.getHtmlContent(),
                getVariables(templateId)
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateVariableItem> getVariables(Long templateId) {
        return documentTemplateVariableRepository.findAllByDocumentTemplateIdOrderByNameAsc(templateId).stream()
                .map(this::toVariableItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownloadData getSourceFile(Long fileId) {
        DocumentFile documentFile = documentFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("Document file not found: " + fileId));

        return new DocumentDownloadData(
                documentFile.getFileName(),
                documentFile.getContentType(),
                documentFile.getContent()
        );
    }

    @Transactional
    public Long createTemplate(
            String documentName,
            String originalFileName,
            String contentType,
            byte[] fileContent
    ) {
        validateInput(documentName, originalFileName, fileContent);

        DocumentFile documentFile = new DocumentFile();
        documentFile.setFileName(originalFileName);
        documentFile.setContentType(resolveContentType(contentType));
        documentFile.setFileSize(fileContent.length);
        documentFile.setContent(fileContent);
        DocumentFile savedFile = documentFileRepository.save(documentFile);

        DocumentTemplate documentTemplate = new DocumentTemplate();
        documentTemplate.setName(documentName.trim());
        documentTemplate.setCreatedAt(LocalDateTime.now());
        documentTemplate.setHtmlContent(docxConversionService.convertToHtml(fileContent));
        documentTemplate.setSourceFile(savedFile);

        DocumentTemplate savedTemplate = documentTemplateRepository.save(documentTemplate);
        syncTemplateVariables(savedTemplate, savedTemplate.getHtmlContent());
        return savedTemplate.getId();
    }

    @Transactional
    public void updateHtmlContent(Long templateId, String htmlContent) {
        DocumentTemplate documentTemplate = documentTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NoSuchElementException("Document template not found: " + templateId));
        String normalizedHtmlContent = htmlContent == null ? "" : htmlContent;
        documentTemplate.setHtmlContent(normalizedHtmlContent);
        syncTemplateVariables(documentTemplate, normalizedHtmlContent);
    }

    @Transactional
    public void updateVariableDescription(Long templateId, Long variableId, String description) {
        DocumentTemplateVariable variable = documentTemplateVariableRepository
                .findByIdAndDocumentTemplateId(variableId, templateId)
                .orElseThrow(() -> new NoSuchElementException("Document template variable not found: " + variableId));
        variable.setDescription(description == null ? "" : description.trim());
    }

    private void validateInput(String documentName, String originalFileName, byte[] fileContent) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("Document name is required");
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("DOCX file name is required");
        }
        if (!originalFileName.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Only .docx files are supported");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("DOCX file content is empty");
        }
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DOCX_CONTENT_TYPE;
        }
        return contentType;
    }

    private void syncTemplateVariables(DocumentTemplate template, String htmlContent) {
        Set<String> detectedVariableNames = extractVariableNames(htmlContent);
        List<DocumentTemplateVariable> existingVariables =
                documentTemplateVariableRepository.findAllByDocumentTemplateIdOrderByNameAsc(template.getId());
        Map<String, DocumentTemplateVariable> existingVariablesByName = existingVariables.stream()
                .collect(Collectors.toMap(DocumentTemplateVariable::getName, Function.identity()));

        List<DocumentTemplateVariable> variablesToDelete = existingVariables.stream()
                .filter(variable -> !detectedVariableNames.contains(variable.getName()))
                .toList();
        if (!variablesToDelete.isEmpty()) {
            documentTemplateVariableRepository.deleteAll(variablesToDelete);
        }

        List<DocumentTemplateVariable> variablesToCreate = detectedVariableNames.stream()
                .filter(variableName -> !existingVariablesByName.containsKey(variableName))
                .map(variableName -> {
                    DocumentTemplateVariable variable = new DocumentTemplateVariable();
                    variable.setDocumentTemplate(template);
                    variable.setName(variableName);
                    variable.setDescription("");
                    return variable;
                })
                .toList();
        if (!variablesToCreate.isEmpty()) {
            documentTemplateVariableRepository.saveAll(variablesToCreate);
        }
    }

    private Set<String> extractVariableNames(String htmlContent) {
        Set<String> variableNames = new LinkedHashSet<>();
        if (htmlContent == null || htmlContent.isBlank()) {
            return variableNames;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(htmlContent);
        while (matcher.find()) {
            variableNames.add(matcher.group(1));
        }
        return variableNames;
    }

    private DocumentTemplateVariableItem toVariableItem(DocumentTemplateVariable variable) {
        return new DocumentTemplateVariableItem(
                variable.getId(),
                variable.getName(),
                variable.getDescription()
        );
    }
}
