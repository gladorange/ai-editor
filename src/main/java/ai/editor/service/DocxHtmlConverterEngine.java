package ai.editor.service;

public interface DocxHtmlConverterEngine {

    String getType();

    String convertToHtml(byte[] content);
}
