package ai.editor.service;

import org.docx4j.Docx4J;
import org.docx4j.convert.out.HTMLSettings;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class Docx4jDocxHtmlConverterEngine implements DocxHtmlConverterEngine {

    @Override
    public String getType() {
        return "docx4j";
    }

    @Override
    public String convertToHtml(byte[] content) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            WordprocessingMLPackage wordprocessingMLPackage = WordprocessingMLPackage.load(inputStream);
            HTMLSettings htmlSettings = Docx4J.createHTMLSettings();
            htmlSettings.setWmlPackage(wordprocessingMLPackage);

            Docx4J.toHTML(htmlSettings, outputStream, Docx4J.FLAG_EXPORT_PREFER_XSL);

            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException | Docx4JException exception) {
            throw new IllegalStateException("Failed to convert DOCX to HTML with docx4j", exception);
        }
    }
}
