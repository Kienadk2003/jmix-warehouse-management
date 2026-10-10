package com.company.warehousemanagement.view.main;

import com.company.warehousemanagement.entity.User;
import com.google.common.base.Strings;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.View.InitEvent;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;

@Route("")
@ViewController(id = "MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    @Autowired
    private Messages messages;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private CurrentUserSubstitution currentUserSubstitution;
    @Autowired
    private DataManager dataManager;

    @ViewComponent
    private Span dashboardInventoryCount;
    @ViewComponent
    private Span dashboardPendingCount;
    @ViewComponent
    private Span dashboardImportCount;
    @ViewComponent
    private Span dashboardExportCount;
    @ViewComponent
    private Span dashboardTransferCount;
    @ViewComponent
    private Span dashboardAdjustmentCount;
    @ViewComponent
    private Span dashboardStocktakeCount;
    @ViewComponent
    private Span dashboardProductCount;
    @ViewComponent
    private Span dashboardWarehouseCount;

    @ViewComponent
    private Button refreshDashboardButton;
    @ViewComponent
    private Button openImportButton;
    @ViewComponent
    private Button openExportButton;
    @ViewComponent
    private Button openTransferButton;
    @ViewComponent
    private Button openStocktakeButton;
    @ViewComponent
    private Button openAdjustmentButton;
    @ViewComponent
    private Button openInventoryButton;
    @ViewComponent
    private Button openStockCardButton;
    @ViewComponent
    private Button openReportButton;

    @Subscribe
    public void onInit(InitEvent event) {
        refreshDashboardCounts();
        refreshDashboardButton.addClickListener(click -> refreshDashboardCounts());
        openImportButton.addClickListener(click -> navigateTo("import-receipts"));
        openExportButton.addClickListener(click -> navigateTo("export-issues"));
        openTransferButton.addClickListener(click -> navigateTo("transfers"));
        openStocktakeButton.addClickListener(click -> navigateTo("stocktakes"));
        openAdjustmentButton.addClickListener(click -> navigateTo("adjustments"));
        openInventoryButton.addClickListener(click -> navigateTo("inventory"));
        openStockCardButton.addClickListener(click -> navigateTo("stock-card"));
        openReportButton.addClickListener(click -> navigateTo("inventory-report"));
    }

    private void refreshDashboardCounts() {
        setCount(dashboardInventoryCount,
                "select count(e) from Inventory e where e.quantity > 0");
        setCombinedCount(dashboardPendingCount,
                "select count(e) from WarehouseTransaction e where e.status = 'PENDING_APPROVAL'",
                "select count(e) from Stocktake e where e.status = 'PENDING_APPROVAL'");
        setCount(dashboardImportCount,
                "select count(e) from WarehouseTransaction e where e.type = 'IMPORT'");
        setCount(dashboardExportCount,
                "select count(e) from WarehouseTransaction e where e.type = 'EXPORT'");
        setCount(dashboardTransferCount,
                "select count(e) from WarehouseTransaction e where e.type = 'TRANSFER'");
        setCount(dashboardAdjustmentCount,
                "select count(e) from WarehouseTransaction e where e.type = 'ADJUSTMENT'");
        setCount(dashboardStocktakeCount,
                "select count(e) from Stocktake e");
        setCount(dashboardProductCount,
                "select count(e) from Product e");
        setCount(dashboardWarehouseCount,
                "select count(e) from Warehouse e");
    }

    private void setCount(Span target, String query) {
        try {
            Long result = dataManager.loadValue(query, Long.class).one();
            target.setText(Long.toString(result == null ? 0L : result));
        } catch (RuntimeException exception) {
            // Keep the dashboard usable even if a role is not allowed to count a particular entity.
            log.debug("Unable to load dashboard metric using query: {}", query, exception);
            target.setText("—");
        }
    }

    private void setCombinedCount(Span target, String... queries) {
        long total = 0;
        for (String query : queries) {
            try {
                Long result = dataManager.loadValue(query, Long.class).one();
                total += result == null ? 0L : result;
            } catch (RuntimeException exception) {
                // Avoid showing a misleading partial value if one of the counts cannot be loaded.
                log.debug("Unable to load combined dashboard metric using query: {}", query, exception);
                target.setText("—");
                return;
            }
        }
        target.setText(Long.toString(total));
    }

    private void navigateTo(String route) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate(route);
        }
    }

    @Install(to = "userMenu", subject = "buttonRenderer")
    private Component userMenuButtonRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        String userName = generateUserName(user);

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-button-content");

        Avatar avatar = createAvatar(userName);

        Span name = uiComponents.create(Span.class);
        name.setText(userName);
        name.setClassName("user-menu-text");

        content.add(avatar, name);

        if (isSubstituted(user)) {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(messages.getMessage("userMenu.substituted"));
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    @Install(to = "userMenu", subject = "headerRenderer")
    private Component userMenuHeaderRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-header-content");

        String name = generateUserName(user);

        Avatar avatar = createAvatar(name);
        avatar.addThemeVariants(AvatarVariant.LARGE);

        Span text = uiComponents.create(Span.class);
        text.setText(name);
        text.setClassName("user-menu-text");

        content.add(avatar, text);

        if (name.equals(user.getUsername())) {
            text.addClassName("user-menu-text-subtext");
        } else {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(user.getUsername());
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    private Avatar createAvatar(String fullName) {
        Avatar avatar = uiComponents.create(Avatar.class);
        avatar.setName(fullName);
        avatar.getElement().setAttribute("tabindex", "-1");
        avatar.setClassName("user-menu-avatar");

        return avatar;
    }

    private String generateUserName(User user) {
        String userName = String.format("%s %s",
                        Strings.nullToEmpty(user.getFirstName()),
                        Strings.nullToEmpty(user.getLastName()))
                .trim();

        return userName.isEmpty() ? user.getUsername() : userName;
    }

    private boolean isSubstituted(User user) {
        UserDetails authenticatedUser = currentUserSubstitution.getAuthenticatedUser();
        return user != null && !authenticatedUser.getUsername().equals(user.getUsername());
    }
}
