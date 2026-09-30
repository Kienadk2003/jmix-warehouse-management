package com.company.warehousemanagement.view.unit;

import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "units/:id", layout = MainView.class)
@ViewController(id = "Unit.detail")
@ViewDescriptor(path = "unit-detail-view.xml")
@EditedEntityContainer("unitDc")
public class UnitDetailView extends StandardDetailView<Unit> {
}