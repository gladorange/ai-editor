package ai.editor.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Шаблоны документов");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("margin", "0");

        RouterLink templatesLink = new RouterLink("Документы", DocumentTemplateListView.class);

        Nav navigation = new Nav(templatesLink);
        navigation.addClassName("main-nav");

        HorizontalLayout content = new HorizontalLayout(title, navigation);
        content.setWidthFull();
        content.setAlignItems(HorizontalLayout.Alignment.CENTER);
        content.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        content.addClassName("main-header");

        Header header = new Header(content);
        header.addClassName("main-header-shell");

        addToNavbar(header);
    }
}
