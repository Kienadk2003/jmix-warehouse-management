package com.company.warehousemanagement.view.export;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.ExportIssueService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.JmixTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.flowui.view.Subscribe;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "export-issues/:id", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.exportDetail")
@ViewDescriptor(path = "export-detail-view.xml")
@EditedEntityContainer("warehouseTransactionDc")
public class ExportDetailView extends StandardDetailView<WarehouseTransaction> {

    @Autowired
    private ExportIssueService exportIssueService;

    @ViewComponent
    private Span statusLabel;

    @ViewComponent
    private JmixTextField documentNoField;

    @ViewComponent
    private JmixButton addItemButton;

    @ViewComponent
    private JmixButton editItemButton;

    @ViewComponent
    private JmixButton removeItemButton;

    @Subscribe
    public void onInitEntity(final InitEntityEvent<WarehouseTransaction> event) {
        exportIssueService.prepareNewDraft(event.getEntity());
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null) {
            return;
        }

        updateStatusLabel(transaction);
        boolean editable = transaction.getStatus() == WarehouseTransactionStatus.DRAFT;
        setReadOnly(!editable);
        documentNoField.setReadOnly(true);
        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        try {
            exportIssueService.validateDraft(getEditedEntity());
        } catch (WarehouseBusinessException e) {
            event.getErrors().add(e.getMessage());
        }
    }

    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        updateStatusLabel(getEditedEntity());
    }

    private void updateStatusLabel(WarehouseTransaction transaction) {
        WarehouseTransactionStatus status = transaction.getStatus();
        statusLabel.setText(status != null ? status.getId() : "-");
    }
}
