package ai.editor.model;

import java.time.LocalDateTime;

public record DocumentTemplateListItem(
        Long id,
        String name,
        LocalDateTime createdAt
) {
}
