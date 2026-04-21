package ai.editor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class MammothDocxHtmlConverterEngine implements DocxHtmlConverterEngine {

    private static final Logger log = LoggerFactory.getLogger(MammothDocxHtmlConverterEngine.class);

    @Override
    public String getType() {
        return "mammoth";
    }

    @Override
    public String convertToHtml(byte[] content) {
        Result<String> result;
        try {
            result = new DocumentConverter()
                    .convertToHtml(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to convert DOCX to HTML with Mammoth", exception);
        }

        result.getWarnings().forEach(warning ->
                log.warn("Mammoth DOCX conversion warning: {}", warning)
        );

        return result.getValue();
    }
}
