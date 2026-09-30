package com.company.warehousemanagement.view.productcategory;

import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "product-categories/:id", layout = MainView.class)
@ViewController(id = "ProductCategory.detail")
@ViewDescriptor(path = "product-category-detail-view.xml")
@EditedEntityContainer("productCategoryDc")
public class ProductCategoryDetailView extends StandardDetailView<ProductCategory> {
}