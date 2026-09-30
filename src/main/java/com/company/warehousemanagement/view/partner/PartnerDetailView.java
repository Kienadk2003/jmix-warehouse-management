package com.company.warehousemanagement.view.partner;

import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "partners/:id", layout = MainView.class)
@ViewController(id = "Partner.detail")
@ViewDescriptor(path = "partner-detail-view.xml")
@EditedEntityContainer("partnerDc")
public class PartnerDetailView extends StandardDetailView<Partner> {
}