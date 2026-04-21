package ai.editor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocxConversionService {

    private final Map<String, DocxHtmlConverterEngine> enginesByType;
    private final String engineType;

    public DocxConversionService(
            List<DocxHtmlConverterEngine> engines,
            @Value("${app.docx.conversion.engine:docx4j}") String engineType
    ) {
        this.enginesByType = engines.stream()
                .collect(Collectors.toUnmodifiableMap(
                        engine -> normalizeType(engine.getType()),
                        Function.identity()
                ));
        this.engineType = normalizeType(engineType);
    }

    public String convertToHtml(byte[] content) {
        DocxHtmlConverterEngine engine = enginesByType.get(engineType);
        if (engine == null) {
            throw new IllegalStateException(
                    "Unsupported DOCX conversion engine: " + engineType
                            + ". Available engines: " + String.join(", ", enginesByType.keySet())
            );
        }

        return engine.convertToHtml(content);
    }

    private String normalizeType(String type) {
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
