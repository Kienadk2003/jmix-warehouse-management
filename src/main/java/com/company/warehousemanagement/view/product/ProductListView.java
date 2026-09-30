package com.company.warehousemanagement.view.product;

import com.company.warehousemanagement.entity.Product;

import com.company.warehousemanagement.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "products", layout = MainView.class)
@ViewController(id = "Product.list")
@ViewDescriptor(path = "product-list-view.xml")
@LookupComponent("productsDataGrid")
@DialogMode(width = "64em")
public class ProductListView extends StandardListView<Product> {

}