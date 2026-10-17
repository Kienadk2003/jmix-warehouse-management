package com.company.warehousemanagement.view.userproductcategory;

import com.company.warehousemanagement.entity.UserProductCategory;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "user-product-categories/:id", layout = MainView.class)
@ViewController(id = "UserProductCategory.detail")
@ViewDescriptor(path = "user-product-category-detail-view.xml")
@EditedEntityContainer("userProductCategoryDc")
public class UserProductCategoryDetailView extends StandardDetailView<UserProductCategory> {
}