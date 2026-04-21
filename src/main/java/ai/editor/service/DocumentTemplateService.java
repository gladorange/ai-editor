package ai.editor.service;

import ai.editor.entity.DocumentFile;
import ai.editor.entity.DocumentTemplate;
import ai.editor.model.DocumentDownloadData;
import ai.editor.model.DocumentTemplateDetails;
import ai.editor.model.DocumentTemplateListItem;
import ai.editor.repository.DocumentFileRepository;
import ai.editor.repository.DocumentTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DocumentTemplateService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocxConversionService docxConversionService;

    public DocumentTemplateService(
            DocumentTemplateRepository documentTemplateRepository,
            DocumentFileRepository documentFileRepository,
            DocxConversionService docxConversionService
    ) {
        this.documentTemplateRepository = documentTemplateRepository;
        this.documentFileRepository = documentFileRepository;
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
                template.getHtmlContent()
        );
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

        return documentTemplateRepository.save(documentTemplate).getId();
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
}
