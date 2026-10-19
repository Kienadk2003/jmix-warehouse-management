package com.company.warehousemanagement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import io.jmix.core.Messages;
import io.jmix.core.entity.EntityValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Presentation-only helper: renders "status" and "active" grid columns as coloured badges
 * (see .wm-badge in figma-v10.css). It never changes data, actions or business logic;
 * if anything goes wrong the grid simply keeps its default rendering.
 */
@org.springframework.stereotype.Component
public class GridBadgeDecorator implements VaadinServiceInitListener {

    private static final Logger log = LoggerFactory.getLogger(GridBadgeDecorator.class);
    private static final String DECORATED_KEY = "wm.badgesDecorated";
    private static final String[] BADGE_COLUMNS = {"status", "active"};

    private final Messages messages;

    public GridBadgeDecorator(Messages messages) {
        this.messages = messages;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addAfterNavigationListener(this::onNavigation));
    }

    private void onNavigation(AfterNavigationEvent event) {
        try {
            UI ui = UI.getCurrent();
            if (ui == null) {
                return;
            }
            ui.getInternals().getActiveRouterTargetsChain().forEach(target -> {
                if (target instanceof Component component) {
                    walk(component);
                }
            });
        } catch (RuntimeException e) {
            log.debug("Badge decoration skipped: {}", e.toString());
        }
    }

    private void walk(Component component) {
        if (component instanceof Grid<?> grid) {
            decorate(grid);
        }
        component.getChildren().forEach(this::walk);
    }

    private void decorate(Grid<?> grid) {
        if (ComponentUtil.getData(grid, DECORATED_KEY) != null) {
            return;
        }
        ComponentUtil.setData(grid, DECORATED_KEY, Boolean.TRUE);
        for (String key : BADGE_COLUMNS) {
            Grid.Column<?> column = grid.getColumnByKey(key);
            if (column == null) {
                continue;
            }
            Renderer<Object> renderer = new ComponentRenderer<Span, Object>(item -> badge(item, key));
            try {
                // Reflection keeps this helper independent of the exact Vaadin Column API.
                column.getClass().getMethod("setRenderer", Renderer.class).invoke(column, renderer);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.debug("Cannot apply badge renderer to column '{}': {}", key, e.toString());
            }
        }
    }

    private Span badge(Object item, String property) {
        Object value = null;
        try {
            value = EntityValues.getValue(item, property);
        } catch (RuntimeException ignored) {
            // fall through to empty badge
        }
        Span span = new Span();
        if (value == null) {
            return span;
        }
        String css;
        String text;
        if (value instanceof Boolean active) {
            boolean vi = isVietnamese();
            text = active ? (vi ? "Hoạt động" : "Active") : (vi ? "Ngừng" : "Inactive");
            css = active ? "wm-badge-ok" : "wm-badge-off";
        } else if (value instanceof Enum<?> enumValue) {
            text = messages.getMessage(enumValue);
            css = cssFor(enumValue.name());
        } else {
            text = String.valueOf(value);
            css = "wm-badge-info";
        }
        span.setText(text);
        span.addClassNames("wm-badge", css);
        return span;
    }

    private static String cssFor(String name) {
        return switch (name) {
            case "POSTED", "APPROVED", "CONFIRMED" -> "wm-badge-ok";
            case "DRAFT", "PENDING_APPROVAL", "COUNTING", "REVIEWING" -> "wm-badge-warn";
            case "REJECTED", "CANCELLED", "REVERSED" -> "wm-badge-bad";
            default -> "wm-badge-info";
        };
    }

    private static boolean isVietnamese() {
        UI ui = UI.getCurrent();
        Locale locale = ui != null ? ui.getLocale() : Locale.getDefault();
        return locale != null && "vi".equalsIgnoreCase(locale.getLanguage());
    }
}
