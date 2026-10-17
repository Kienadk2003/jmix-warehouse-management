package com.company.warehousemanagement.view.transactionitem;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.StandardDetailView.InitEntityEvent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "transaction-items/:id", layout = MainView.class)
@ViewController(id = "TransactionItem.detail")
@ViewDescriptor(path = "transaction-item-detail-view.xml")
@EditedEntityContainer("transactionItemDc")
public class TransactionItemDetailView extends StandardDetailView<TransactionItem> {

    @Subscribe
    public void onInitEntity(InitEntityEvent<TransactionItem> event) {
        event.getEntity().setQuantity(null);
    }
}