package ai.editor.view;

import ai.editor.model.DocumentDownloadData;
import ai.editor.model.DocumentTemplateDetails;
import ai.editor.service.DocumentTemplateService;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

@Route(value = "documents", layout = MainLayout.class)
@PageTitle("Карточка документа")
public class DocumentTemplateDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DocumentTemplateService documentTemplateService;

    public DocumentTemplateDetailView(DocumentTemplateService documentTemplateService) {
        this.documentTemplateService = documentTemplateService;
        addClassName("view-shell");
        setSizeFull();
        setHeight(null);
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, Long templateId) {
        removeAll();

        if (templateId == null) {
            event.forwardTo(DocumentTemplateListView.class);
            return;
        }

        try {
            DocumentTemplateDetails details = documentTemplateService.getDetails(templateId);
            add(buildHeader(details), buildDocumentContent(details));
        } catch (NoSuchElementException exception) {
            Notification.show("Документ не найден");
            event.forwardTo(DocumentTemplateListView.class);
        }
    }

    private VerticalLayout buildHeader(DocumentTemplateDetails details) {
        VerticalLayout header = new VerticalLayout();
        header.addClassName("document-header-card");
        header.setPadding(true);
        header.setSpacing(false);

        header.add(new H2(details.name()));
        header.add(new Span("Дата загрузки: " + DATE_TIME_FORMATTER.format(details.createdAt())));
        header.add(createDownloadAnchor(details));
        return header;
    }

    private Html buildDocumentContent(DocumentTemplateDetails details) {
        String html = details.htmlContent();
        if (html == null || html.isBlank()) {
            html = "<p>Документ не содержит текстового содержимого.</p>";
        }

        Html content = new Html("<div class='document-html'>" + html + "</div>");
        content.getElement().getClassList().add("document-content-card");
        return content;
    }

    private Anchor createDownloadAnchor(DocumentTemplateDetails details) {
        DocumentDownloadData downloadData = documentTemplateService.getSourceFile(details.sourceFileId());
        StreamResource resource = new StreamResource(
                downloadData.fileName(),
                () -> new ByteArrayInputStream(downloadData.content())
        );
        resource.setContentType(downloadData.contentType());

        Anchor anchor = new Anchor(resource, "Скачать исходный DOCX");
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }
}
