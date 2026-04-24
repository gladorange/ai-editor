package ai.editor.view;

import ai.editor.model.DocumentDownloadData;
import ai.editor.model.DocumentTemplateDetails;
import ai.editor.model.DocumentTemplateVariableItem;
import ai.editor.service.DocumentTemplateService;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
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
    private Long currentTemplateId;
    private VerticalLayout variablesList;

    public DocumentTemplateDetailView(DocumentTemplateService documentTemplateService) {
        this.documentTemplateService = documentTemplateService;
        addClassName("view-shell");
        setSizeFull();
        setHeight(null);
        addDetachListener(event -> getElement().executeJs(
                "window.destroyDocumentTemplateEditor?.(this);"
        ));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, Long templateId) {
        removeAll();
        currentTemplateId = templateId;

        if (templateId == null) {
            event.forwardTo(DocumentTemplateListView.class);
            return;
        }

        try {
            DocumentTemplateDetails details = documentTemplateService.getDetails(templateId);
            String htmlContent = details.htmlContent();
            if (htmlContent == null || htmlContent.isBlank()) {
                htmlContent = "<p>Документ не содержит текстового содержимого.</p>";
            }

            VerticalLayout documentColumn = new VerticalLayout(buildHeader(details), buildDocumentContent());
            documentColumn.addClassName("document-main-column");
            documentColumn.setPadding(false);
            documentColumn.setSpacing(true);

            HorizontalLayout contentLayout = new HorizontalLayout(
                    documentColumn,
                    buildVariablesSection(details.variables())
            );
            contentLayout.addClassName("document-detail-layout");
            contentLayout.setWidthFull();
            contentLayout.setSpacing(true);
            contentLayout.setAlignItems(Alignment.START);
            contentLayout.setFlexGrow(1, documentColumn);

            add(contentLayout);
            getElement().executeJs(
                    """
                    window.setTimeout(() => {
                        window.initDocumentTemplateEditor?.(this, '.element', $0);
                    }, 1000);
                    """,
                    htmlContent
            );
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

        Button saveButton = new Button("Сохранить", event ->
                getElement().executeJs("window.saveDocumentTemplateEditor?.(this);")
        );
        saveButton.addThemeName("primary");

        HorizontalLayout titleRow = new HorizontalLayout(new H2(details.name()), saveButton);
        titleRow.setWidthFull();
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setJustifyContentMode(JustifyContentMode.BETWEEN);

        header.add(titleRow);
        header.add(new Span("Дата загрузки: " + DATE_TIME_FORMATTER.format(details.createdAt())));
        header.add(createDownloadAnchor(details));
        header.setWidth(null);
        return header;
    }

    private VerticalLayout buildVariablesSection(java.util.List<DocumentTemplateVariableItem> variables) {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("template-variables-card");
        section.setPadding(true);
        section.setSpacing(false);
        section.setWidth(null);

        H2 title = new H2("Переменные в шаблоне");
        title.addClassName("template-variables-title");

        Span hint = new Span("Нажмите на переменную, чтобы изменить описание.");
        hint.addClassName("template-variables-hint");

        variablesList = new VerticalLayout();
        variablesList.addClassName("template-variables-list");
        variablesList.setPadding(false);
        variablesList.setSpacing(false);
        variablesList.setWidth(null);

        section.add(title, hint, variablesList);
        refreshVariablesSection(variables);
        return section;
    }

    private Div buildDocumentContent() {
        Div editorHost = new Div();
        editorHost.addClassNames("document-html", "element");

        Div content = new Div(editorHost);
        content.addClassName("document-content-card");
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

    private void refreshVariablesSection(java.util.List<DocumentTemplateVariableItem> variables) {
        if (variablesList == null) {
            return;
        }

        variablesList.removeAll();
        if (variables == null || variables.isEmpty()) {
            Span emptyState = new Span("Переменные в документе пока не найдены.");
            emptyState.addClassName("template-variables-empty");
            variablesList.add(emptyState);
            return;
        }

        for (DocumentTemplateVariableItem variable : variables) {
            variablesList.add(buildVariableItem(variable));
        }
    }

    private Button buildVariableItem(DocumentTemplateVariableItem variable) {
        Span name = new Span(variable.name());
        name.addClassName("template-variable-name");

        String descriptionText = variable.description() == null || variable.description().isBlank()
                ? "Описание не заполнено"
                : variable.description();
        Span description = new Span(descriptionText);
        description.addClassName("template-variable-description");

        VerticalLayout content = new VerticalLayout(name, description);
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("template-variable-item-content");

        Button button = new Button(content, event -> openVariableDialog(variable));
        button.addClassName("template-variable-item");
        button.setWidthFull();
        return button;
    }

    private void openVariableDialog(DocumentTemplateVariableItem variable) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Описание переменной");
        dialog.setWidth("420px");

        TextField nameField = new TextField("Переменная");
        nameField.setWidthFull();
        nameField.setReadOnly(true);
        nameField.setValue(variable.name());

        TextArea descriptionField = new TextArea("Описание");
        descriptionField.setWidthFull();
        descriptionField.setMinHeight("180px");
        descriptionField.setValue(variable.description() == null ? "" : variable.description());

        Button cancelButton = new Button("Отмена", event -> dialog.close());
        Button saveButton = new Button("Сохранить", event -> {
            if (currentTemplateId == null) {
                Notification.show("Не удалось определить документ");
                return;
            }

            try {
                documentTemplateService.updateVariableDescription(
                        currentTemplateId,
                        variable.id(),
                        descriptionField.getValue()
                );
                refreshVariablesSection(documentTemplateService.getVariables(currentTemplateId));
                Notification.show("Описание переменной сохранено");
                dialog.close();
            } catch (NoSuchElementException exception) {
                Notification.show("Переменная не найдена");
            }
        });
        saveButton.addThemeName("primary");

        dialog.add(new VerticalLayout(nameField, descriptionField));
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    @ClientCallable
    private void saveEditorContent(String htmlContent) {
        if (currentTemplateId == null) {
            Notification.show("Не удалось определить документ для сохранения");
            return;
        }

        try {
            documentTemplateService.updateHtmlContent(currentTemplateId, htmlContent);
            refreshVariablesSection(documentTemplateService.getVariables(currentTemplateId));
            Notification.show("Документ сохранен");
        } catch (NoSuchElementException exception) {
            Notification.show("Документ не найден");
        }
    }
}
