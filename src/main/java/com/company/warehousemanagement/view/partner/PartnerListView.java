package com.company.warehousemanagement.view.partner;

import com.company.warehousemanagement.entity.Partner;

import com.company.warehousemanagement.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "partners", layout = MainView.class)
@ViewController(id = "Partner.list")
@ViewDescriptor(path = "partner-list-view.xml")
@LookupComponent("partnersDataGrid")
@DialogMode(width = "64em")
public class PartnerListView extends StandardListView<Partner> {

}