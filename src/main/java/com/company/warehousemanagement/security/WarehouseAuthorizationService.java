package com.company.warehousemanagement.security;

import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.AccessManager;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import org.springframework.stereotype.Component;

@Component
public class WarehouseAuthorizationService {

    private final AccessManager accessManager;

    public WarehouseAuthorizationService(AccessManager accessManager) {
        this.accessManager = accessManager;
    }

    public boolean isAllowed(String permission) {
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(permission);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    public void require(String permission, String message) {
        if (!isAllowed(permission)) {
            throw new WarehouseBusinessException(message);
        }
    }
}
