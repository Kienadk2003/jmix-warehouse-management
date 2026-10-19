package com.company.warehousemanagement.email;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: no mail server and no Spring context are needed. */
class EmailInboxNotConfiguredTest {

    @Test
    void emptyEnvironmentMeansNotConfigured() {
        EmailInboxSettings settings = new EmailInboxSettings(new MockEnvironment());

        assertThat(settings.isConfigured()).isFalse();
        assertThat(settings.folder()).isEqualTo("INBOX");
        assertThat(settings.port()).isEqualTo(993);
    }

    @Test
    void hostWithoutCredentialsIsStillNotConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("khoflow.inbox.imap.host", "imap.example.com");

        assertThat(new EmailInboxSettings(environment).isConfigured()).isFalse();
    }

    @Test
    void completeSettingsAreConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("khoflow.inbox.imap.host", "imap.example.com")
                .withProperty("khoflow.inbox.imap.username", "inbox@example.com")
                .withProperty("khoflow.inbox.imap.password", "secret");

        assertThat(new EmailInboxSettings(environment).isConfigured()).isTrue();
    }

    @Test
    void readingAnUnconfiguredMailboxFailsWithNotConfiguredAndNeverReturnsMail() {
        ImapEmailInboxService service =
                new ImapEmailInboxService(new EmailInboxSettings(new MockEnvironment()), null);

        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(service::fetchUnreadCount)
                .isInstanceOfSatisfying(EmailInboxException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EmailInboxException.Reason.NOT_CONFIGURED));
    }

    @Test
    void htmlIsConvertedToPlainText() {
        String text = ImapEmailInboxService.htmlToText(
                "<html><style>p{color:red}</style><body><p>Hello&nbsp;<b>team</b></p><script>alert(1)</script></body></html>");

        assertThat(text).isEqualTo("Hello team");
    }
}
