package com.company.warehousemanagement.view.unit;

import com.company.warehousemanagement.entity.Unit;

import com.company.warehousemanagement.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "units", layout = MainView.class)
@ViewController(id = "Unit.list")
@ViewDescriptor(path = "unit-list-view.xml")
@LookupComponent("unitsDataGrid")
@DialogMode(width = "64em")
public class UnitListView extends StandardListView<Unit> {

}