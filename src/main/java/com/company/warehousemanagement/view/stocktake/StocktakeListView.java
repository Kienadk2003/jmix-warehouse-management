package com.company.warehousemanagement.view.stocktake;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "stocktakes", layout = MainView.class)
@ViewController(id = "Stocktake.list")
@ViewDescriptor(path = "stocktake-list-view.xml")
@LookupComponent("stocktakesDataGrid")
public class StocktakeListView extends StandardListView<Stocktake> {
}
