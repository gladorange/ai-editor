package ai.editor.model;

public record DocumentDownloadData(
        String fileName,
        String contentType,
        byte[] content
) {
}
