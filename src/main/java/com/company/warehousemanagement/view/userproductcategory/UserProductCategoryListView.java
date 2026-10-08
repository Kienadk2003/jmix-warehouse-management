package com.company.warehousemanagement.view.userproductcategory;

import com.company.warehousemanagement.entity.UserProductCategory;

import com.company.warehousemanagement.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "user-product-categories", layout = MainView.class)
@ViewController(id = "UserProductCategory.list")
@ViewDescriptor(path = "user-product-category-list-view.xml")
@LookupComponent("userProductCategoriesDataGrid")
@DialogMode(width = "64em")
public class UserProductCategoryListView extends StandardListView<UserProductCategory> {

}