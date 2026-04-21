package ai.editor.model;

import java.time.LocalDateTime;

public record DocumentTemplateDetails(
        Long id,
        String name,
        LocalDateTime createdAt,
        Long sourceFileId,
        String sourceFileName,
        String sourceContentType,
        String htmlContent
) {
}
