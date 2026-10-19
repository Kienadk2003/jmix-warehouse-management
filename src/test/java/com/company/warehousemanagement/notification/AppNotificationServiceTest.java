package com.company.warehousemanagement.notification;

import com.company.warehousemanagement.entity.AppNotification;
import com.company.warehousemanagement.entity.NotificationType;
import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.service.AppNotificationService;
import com.company.warehousemanagement.service.AppNotificationService.NotificationPage;
import com.company.warehousemanagement.test_support.AuthenticatedAsAdmin;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the server-side isolation rule: a user can never read or modify the
 * notifications addressed to another user.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class AppNotificationServiceTest {

    @Autowired
    AppNotificationService service;
    @Autowired
    UnconstrainedDataManager dataManager;
    @Autowired
    SystemAuthenticator authenticator;
    @Autowired
    PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = createUser("notif-a-" + UUID.randomUUID());
        userB = createUser("notif-b-" + UUID.randomUUID());
        service.createForUsers(List.of(userA.getUsername()), NotificationType.TRANSACTION_SUBMITTED,
                "IMPORT", "NK-A-1", "tester", null, null, null);
        service.createForUsers(List.of(userA.getUsername(), userB.getUsername()),
                NotificationType.STOCKTAKE_APPROVED, "STOCKTAKE", "KK-1", "tester", null, null, null);
    }

    @AfterEach
    void tearDown() {
        for (User user : List.of(userA, userB)) {
            dataManager.load(AppNotification.class)
                    .query("select e from AppNotification e where e.recipientUsername = :u")
                    .parameter("u", user.getUsername())
                    .list()
                    .forEach(dataManager::remove);
            dataManager.remove(user);
        }
    }

    @Test
    void eachUserSeesOnlyOwnNotificationsAndCounts() {
        NotificationPage pageA = authenticator.withUser(userA.getUsername(), () -> service.listForCurrentUser(0, 20));
        NotificationPage pageB = authenticator.withUser(userB.getUsername(), () -> service.listForCurrentUser(0, 20));

        assertThat(pageA.items()).hasSize(2);
        assertThat(pageB.items()).hasSize(1);
        assertThat(authenticator.withUser(userA.getUsername(), service::countUnreadForCurrentUser)).isEqualTo(2);
        assertThat(authenticator.withUser(userB.getUsername(), service::countUnreadForCurrentUser)).isEqualTo(1);
    }

    @Test
    void userCannotMarkSomeoneElsesNotificationAsRead() {
        UUID aOnly = authenticator.withUser(userA.getUsername(), () -> service.listForCurrentUser(0, 20))
                .items().stream().filter(i -> "NK-A-1".equals(i.subjectNo())).findFirst().orElseThrow().id();

        boolean changedByB = authenticator.withUser(userB.getUsername(), () -> service.markRead(aOnly));

        assertThat(changedByB).isFalse();
        assertThat(authenticator.withUser(userA.getUsername(), service::countUnreadForCurrentUser)).isEqualTo(2);
    }

    @Test
    void markAllReadOnlyAffectsTheCurrentUser() {
        authenticator.withUser(userA.getUsername(), service::markAllReadForCurrentUser);

        assertThat(authenticator.withUser(userA.getUsername(), service::countUnreadForCurrentUser)).isZero();
        assertThat(authenticator.withUser(userB.getUsername(), service::countUnreadForCurrentUser)).isEqualTo(1);
    }

    private User createUser(String username) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("test-passwd"));
        return dataManager.save(user);
    }
}
