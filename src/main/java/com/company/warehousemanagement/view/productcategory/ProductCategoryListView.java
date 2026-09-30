package com.company.warehousemanagement.view.productcategory;

import com.company.warehousemanagement.entity.ProductCategory;

import com.company.warehousemanagement.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "product-categories", layout = MainView.class)
@ViewController(id = "ProductCategory.list")
@ViewDescriptor(path = "product-category-list-view.xml")
@LookupComponent("productCategoriesDataGrid")
@DialogMode(width = "64em")
public class ProductCategoryListView extends StandardListView<ProductCategory> {

}