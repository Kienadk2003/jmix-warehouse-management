package com.company.warehousemanagement.view.userwarehouse;

import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.entity.UserProductCategory;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route(value = "user-warehouses/:id", layout = MainView.class)
@ViewController(id = "UserWarehouse.detail")
@ViewDescriptor(path = "user-warehouse-detail-view.xml")
@EditedEntityContainer("userWarehouseDc")
public class UserWarehouseDetailView extends StandardDetailView<UserWarehouse> {

    @ViewComponent
    private DataContext dataContext;

    @ViewComponent
    private EntityComboBox<ProductCategory> productCategoryField;

    @Autowired
    private DataManager dataManager;


    /*
     * Khi mở màn hình Edit/Create
     */
    @Subscribe
    public void onReady(ReadyEvent event) {

        UserWarehouse userWarehouse = getEditedEntity();

        if (userWarehouse == null) {
            productCategoryField.setVisible(false);
            productCategoryField.setRequired(false);
            return;
        }

        User user = userWarehouse.getUser();

        updateProductCategoryVisibility(user);

        /*
         * Nếu chưa chọn User thì chưa cần load Category
         */
        if (user == null) {
            return;
        }

        /*
         * Manager không có Product Category
         */
        if (isManager(user)) {
            userWarehouse.setProductCategory(null);
            return;
        }

        /*
         * Staff:
         * lấy Product Category đã được phân công
         */
        List<UserProductCategory> assignments =
                dataManager.load(UserProductCategory.class)
                        .query("""
                                select e
                                from UserProductCategory e
                                where e.user = :user
                                order by e.id
                                """)
                        .parameter("user", user)
                        .fetchPlan("_base")
                        .list();

        if (!assignments.isEmpty()) {

            ProductCategory productCategory =
                    assignments.get(0).getProductCategory();

            userWarehouse.setProductCategory(productCategory);
        }
    }


    /*
     * Khi người dùng chọn User trên form
     */
    @Subscribe("userField")
    public void onUserFieldValueChange(
            AbstractField.ComponentValueChangeEvent<
                    EntityComboBox<User>,
                    User> event) {

        User user = event.getValue();

        updateProductCategoryVisibility(user);

        /*
         * Nếu chọn Manager thì xóa Category
         */
        if (user == null || isManager(user)) {
            getEditedEntity().setProductCategory(null);
        }
    }


    /*
     * Kiểm tra User có phải Warehouse Manager không
     */
    private boolean isManager(User user) {

        if (user == null ||
                user.getUsername() == null) {
            return false;
        }

        List<RoleAssignmentEntity> assignments =
                dataManager.load(RoleAssignmentEntity.class)
                        .query("""
                                select e
                                from sec_RoleAssignmentEntity e
                                where e.username = :username
                                  and e.roleCode = :roleCode
                                """)
                        .parameter("username", user.getUsername())
                        .parameter("roleCode", "warehouse-manager")
                        .list();

        return !assignments.isEmpty();
    }


    /*
     * Hiện / ẩn Product Category
     */
    private void updateProductCategoryVisibility(User user) {

        boolean manager = isManager(user);

        boolean staff = user != null && !manager;

        productCategoryField.setVisible(staff);
        productCategoryField.setRequired(staff);
    }


    /*
     * Khi Save
     */
    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {

        UserWarehouse userWarehouse = getEditedEntity();

        if (userWarehouse == null ||
                userWarehouse.getUser() == null ||
                userWarehouse.getWarehouse() == null) {
            return;
        }

        User user = userWarehouse.getUser();

        /*
         * Manager:
         * không có Product Category
         */
        if (isManager(user)) {

            userWarehouse.setProductCategory(null);

            return;
        }

        /*
         * Staff nhưng chưa chọn Category
         */
        if (userWarehouse.getProductCategory() == null) {
            return;
        }

        /*
         * Tìm Category hiện tại của Staff
         */
        List<UserProductCategory> assignments =
                dataManager.load(UserProductCategory.class)
                        .query("""
                                select e
                                from UserProductCategory e
                                where e.user = :user
                                order by e.id
                                """)
                        .parameter("user", user)
                        .fetchPlan("_base")
                        .list();


        /*
         * CREATE
         */
        if (assignments.isEmpty()) {

            UserProductCategory assignment =
                    dataContext.create(UserProductCategory.class);

            assignment.setUser(user);

            assignment.setProductCategory(
                    userWarehouse.getProductCategory()
            );

        }

        /*
         * EDIT
         */
        else {

            UserProductCategory assignment =
                    dataContext.merge(assignments.get(0));

            assignment.setProductCategory(
                    userWarehouse.getProductCategory()
            );
        }
    }
}