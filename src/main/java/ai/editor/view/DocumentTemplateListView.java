package ai.editor.view;

import ai.editor.model.DocumentTemplateListItem;
import ai.editor.service.DocumentTemplateService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Шаблоны документов")
public class DocumentTemplateListView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DocumentTemplateService documentTemplateService;
    private final Grid<DocumentTemplateListItem> grid = new Grid<>(DocumentTemplateListItem.class, false);

    public DocumentTemplateListView(DocumentTemplateService documentTemplateService) {
        this.documentTemplateService = documentTemplateService;

        addClassName("view-shell");
        setSizeFull();
        configureGrid();

        Button uploadButton = new Button("Загрузить новый", event -> openUploadDialog());

        HorizontalLayout header = new HorizontalLayout(new H2("Список шаблонов"), uploadButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(header, grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.addColumn(DocumentTemplateListItem::id)
                .setHeader("ID")
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(DocumentTemplateListItem::name)
                .setHeader("Название")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(item -> DATE_TIME_FORMATTER.format(item.createdAt()))
                .setHeader("Дата загрузки")
                .setAutoWidth(true);
        grid.setSizeFull();
        grid.addClassName("document-grid");
        grid.addItemClickListener(event ->
                getUI().ifPresent(ui -> ui.navigate("documents/" + event.getItem().id()))
        );
    }

    private void refreshGrid() {
        grid.setItems(documentTemplateService.findAll());
    }

    private void openUploadDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Загрузка DOCX шаблона");
        dialog.setWidth("520px");

        TextField nameField = new TextField("Название документа");
        nameField.setWidthFull();

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".docx");
        upload.setMaxFiles(1);
        upload.setUploadButton(new Button("Выбрать DOCX"));
        upload.setDropLabel(new Span("Перетащите DOCX сюда или выберите файл"));

        AtomicReference<UploadedDocx> uploadedDocx = new AtomicReference<>();
        upload.addSucceededListener(event -> {
            try {
                uploadedDocx.set(new UploadedDocx(
                        event.getFileName(),
                        event.getMIMEType(),
                        buffer.getInputStream().readAllBytes()
                ));
                nameField.setValue(stripDocxExtension(event.getFileName()));
            } catch (IOException exception) {
                uploadedDocx.set(null);
                Notification.show("Не удалось прочитать загруженный файл");
            }
        });

        upload.addFileRejectedListener(event -> Notification.show(event.getErrorMessage()));

        Button cancelButton = new Button("Отмена", event -> dialog.close());
        Button saveButton = new Button("Сохранить", event -> {
            UploadedDocx file = uploadedDocx.get();
            if (file == null) {
                Notification.show("Сначала загрузите DOCX файл");
                return;
            }

            try {
                documentTemplateService.createTemplate(
                        nameField.getValue(),
                        file.fileName(),
                        file.contentType(),
                        file.content()
                );
                refreshGrid();
                dialog.close();
            } catch (IllegalArgumentException exception) {
                Notification.show(exception.getMessage());
            }
        });
        saveButton.addThemeName("primary");

        dialog.add(new VerticalLayout(nameField, upload));
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private record UploadedDocx(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }

    private String stripDocxExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        if (fileName.toLowerCase().endsWith(".docx")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }
}
