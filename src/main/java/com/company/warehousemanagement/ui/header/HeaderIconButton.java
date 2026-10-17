package com.company.warehousemanagement.ui.header;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Square icon button with an unread badge. The badge is a sibling of the button (not
 * inside it) and ignores pointer events, so the whole 42px button stays clickable and the
 * click is handled by a normal Vaadin Button, never by the SVG.
 */
public class HeaderIconButton extends Div {

    private static final long MAX_BADGE = 99;

    private final Button button;
    private final Span badge = new Span();
    private String label = "";
    private long count;

    public HeaderIconButton(VaadinIcon icon, String extraClassName) {
        addClassNames("warehouse-header-icon", extraClassName);

        button = new Button(new Icon(icon));
        button.addClassName("warehouse-header-icon-button");
        button.getElement().setAttribute("theme", "tertiary");

        badge.addClassName("warehouse-header-badge");
        badge.getElement().setAttribute("aria-hidden", "true");
        badge.setVisible(false);

        add(button, badge);
    }

    public Button getButton() {
        return button;
    }

    /** Tooltip + accessible name; the unread count is appended for screen readers. */
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        button.setTooltipText(this.label);
        button.getElement().setAttribute("aria-label", this.label + (count > 0 ? " (" + count + ")" : ""));
    }

    /** Shows the number, "99+" above 99, and hides the badge for 0. */
    public void setCount(long count) {
        this.count = Math.max(0, count);
        if (this.count == 0) {
            badge.setVisible(false);
            badge.setText("");
        } else {
            badge.setText(this.count > MAX_BADGE ? MAX_BADGE + "+" : Long.toString(this.count));
            badge.setVisible(true);
        }
        button.getElement().setAttribute("aria-label", label + (this.count > 0 ? " (" + this.count + ")" : ""));
    }

    public long getCount() {
        return count;
    }
}
