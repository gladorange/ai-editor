package ai.editor.service;

import ai.editor.model.DocumentTemplateDetails;
import ai.editor.repository.DocumentFileRepository;
import ai.editor.repository.DocumentTemplateRepository;
import ai.editor.repository.DocumentTemplateVariableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.docx.conversion.engine=docx4j"
})
class DocumentTemplateServiceTest {

    @Autowired
    private DocumentTemplateService documentTemplateService;

    @Autowired
    private DocumentTemplateRepository documentTemplateRepository;

    @Autowired
    private DocumentFileRepository documentFileRepository;

    @Autowired
    private DocumentTemplateVariableRepository documentTemplateVariableRepository;

    @Test
    void createTemplateStoresHtmlAndOriginalFile() throws IOException {
        byte[] docx = createMinimalDocx("Привет из тестового документа");

        Long templateId = documentTemplateService.createTemplate(
                "Тестовый шаблон",
                "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTemplateDetails details = documentTemplateService.getDetails(templateId);

        assertNotNull(templateId);
        assertEquals(1, documentTemplateRepository.count());
        assertEquals(1, documentFileRepository.count());
        assertEquals("Тестовый шаблон", details.name());
        assertEquals("sample.docx", details.sourceFileName());
        assertTrue(details.htmlContent().contains("Привет из тестового документа"));
    }

    @Test
    void createTemplateExtractsVariablesFromDocument() throws IOException {
        byte[] docx = createMinimalDocx("{{{CLIENT_NAME}}} и {{{ГОРОД_1}}}");

        Long templateId = documentTemplateService.createTemplate(
                "Переменные",
                "variables.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTemplateDetails details = documentTemplateService.getDetails(templateId);

        assertEquals(2, documentTemplateVariableRepository.count());
        assertIterableEquals(
                java.util.List.of("CLIENT_NAME", "ГОРОД_1"),
                details.variables().stream().map(variable -> variable.name()).toList()
        );
    }

    @Test
    void updateHtmlContentSynchronizesVariablesAndKeepsDescriptions() throws IOException {
        byte[] docx = createMinimalDocx("{{{A}}} {{{B}}}");

        Long templateId = documentTemplateService.createTemplate(
                "Синхронизация",
                "sync.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTemplateDetails beforeUpdate = documentTemplateService.getDetails(templateId);
        Long variableAId = beforeUpdate.variables().stream()
                .filter(variable -> "A".equals(variable.name()))
                .findFirst()
                .orElseThrow()
                .id();

        documentTemplateService.updateVariableDescription(templateId, variableAId, "Описание A");
        documentTemplateService.updateHtmlContent(templateId, "<p>{{{A}}} {{{C}}}</p>");

        DocumentTemplateDetails afterUpdate = documentTemplateService.getDetails(templateId);

        assertEquals(2, documentTemplateVariableRepository.count());
        assertIterableEquals(
                java.util.List.of("A", "C"),
                afterUpdate.variables().stream().map(variable -> variable.name()).toList()
        );
        assertEquals(
                "Описание A",
                afterUpdate.variables().stream()
                        .filter(variable -> "A".equals(variable.name()))
                        .findFirst()
                        .orElseThrow()
                        .description()
        );
        assertEquals(
                "",
                afterUpdate.variables().stream()
                        .filter(variable -> "C".equals(variable.name()))
                        .findFirst()
                        .orElseThrow()
                        .description()
        );
    }

    private byte[] createMinimalDocx(String text) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml"
                        ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);

            writeEntry(zipOutputStream, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                        Target="word/document.xml"/>
                    </Relationships>
                    """);

            writeEntry(zipOutputStream, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p>
                          <w:r>
                            <w:t>%s</w:t>
                          </w:r>
                        </w:p>
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """.formatted(escapeXml(text)));

            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
