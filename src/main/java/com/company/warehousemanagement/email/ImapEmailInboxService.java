package com.company.warehousemanagement.email;

import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SubjectTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * IMAP adapter. A short-lived connection is opened per request (connect, read, close):
 * simple, no connection state shared between users, and bounded by connect/read timeouts.
 *
 * <p>{@code mail.imap.peek=true} makes body fetches use BODY.PEEK so building a preview
 * never marks a message as read by accident.</p>
 */
@Service
public class ImapEmailInboxService implements EmailInboxService {

    private static final Logger log = LoggerFactory.getLogger(ImapEmailInboxService.class);

    private static final int MAX_PAGE_SIZE = 30;
    private static final int PREVIEW_LENGTH = 140;
    private static final int PREVIEW_SCAN_CHARS = 4_000;
    private static final int MAX_BODY_CHARS = 50_000;

    private final EmailInboxSettings settings;
    private final WarehouseAuthorizationService authorizationService;

    public ImapEmailInboxService(EmailInboxSettings settings,
                                 WarehouseAuthorizationService authorizationService) {
        this.settings = settings;
        this.authorizationService = authorizationService;
    }

    @FunctionalInterface
    private interface FolderCallback<T> {
        T apply(Folder folder) throws MessagingException, IOException;
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured();
    }

    @Override
    public boolean canAccess() {
        return authorizationService.isAllowed(WarehousePermissions.VIEW_EMAIL_INBOX);
    }

    @Override
    public int fetchUnreadCount() {
        return withFolder(false, Folder::getUnreadMessageCount);
    }

    @Override
    public EmailInboxPage list(int offset, int limit, String query) {
        requireAccess();
        int size = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int skip = Math.max(offset, 0);
        String term = query == null ? "" : query.trim();

        return withFolder(false, folder -> {
            Message[] candidates;
            int total;
            if (term.isEmpty()) {
                total = folder.getMessageCount();
                int end = total - skip;
                if (end < 1) {
                    return new EmailInboxPage(List.of(), false);
                }
                int start = Math.max(1, end - size + 1);
                candidates = folder.getMessages(start, end);
            } else {
                Message[] found = folder.search(new OrTerm(new FromStringTerm(term), new SubjectTerm(term)));
                total = found.length;
                int end = total - skip;
                if (end < 1) {
                    return new EmailInboxPage(List.of(), false);
                }
                int start = Math.max(0, end - size);
                candidates = java.util.Arrays.copyOfRange(found, start, end);
            }

            // One round trip for envelope + flags + structure of the whole page.
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            profile.add(FetchProfile.Item.CONTENT_INFO);
            profile.add(UIDFolder.FetchProfileItem.UID);
            folder.fetch(candidates, profile);

            List<EmailMessageSummary> items = new ArrayList<>(candidates.length);
            for (int i = candidates.length - 1; i >= 0; i--) {
                items.add(toSummary(folder, candidates[i]));
            }
            return new EmailInboxPage(items, skip + size < total);
        });
    }

    @Override
    public EmailMessageDetail open(String uid, boolean markAsRead) {
        requireAccess();
        return withFolder(markAsRead, folder -> {
            Message message = findByUid(folder, uid);
            boolean wasRead = message.isSet(Flags.Flag.SEEN);
            if (markAsRead && !wasRead) {
                message.setFlag(Flags.Flag.SEEN, true);
            }
            List<String> attachments = new ArrayList<>();
            collectAttachmentNames(message, attachments);
            String body = extractText(message, MAX_BODY_CHARS);
            return new EmailMessageDetail(
                    uid,
                    senderOf(message),
                    subjectOf(message),
                    body == null ? "" : body,
                    receivedAt(message),
                    wasRead || markAsRead,
                    attachments);
        });
    }

    @Override
    public void setRead(String uid, boolean read) {
        requireAccess();
        withFolder(true, folder -> {
            findByUid(folder, uid).setFlag(Flags.Flag.SEEN, read);
            return Boolean.TRUE;
        });
    }

    // ---------------------------------------------------------------------------------
    // Connection handling
    // ---------------------------------------------------------------------------------

    private void requireAccess() {
        if (!canAccess()) {
            throw new EmailInboxException(EmailInboxException.Reason.ACCESS_DENIED);
        }
    }

    private <T> T withFolder(boolean writable, FolderCallback<T> callback) {
        if (!settings.isConfigured()) {
            throw new EmailInboxException(EmailInboxException.Reason.NOT_CONFIGURED);
        }
        Store store = null;
        Folder folder = null;
        try {
            store = connect();
            folder = store.getFolder(settings.folder());
            folder.open(writable ? Folder.READ_WRITE : Folder.READ_ONLY);
            return callback.apply(folder);
        } catch (AuthenticationFailedException exception) {
            log.warn("IMAP login was rejected for the configured mailbox");
            throw new EmailInboxException(EmailInboxException.Reason.AUTHENTICATION, exception);
        } catch (MessagingException exception) {
            log.warn("IMAP request failed: {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
            throw new EmailInboxException(EmailInboxException.Reason.CONNECTION, exception);
        } catch (IOException exception) {
            log.warn("IMAP content could not be read: {}", exception.getMessage());
            throw new EmailInboxException(EmailInboxException.Reason.OTHER, exception);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private Store connect() throws MessagingException {
        boolean ssl = settings.ssl();
        String protocol = ssl ? "imaps" : "imap";
        String prefix = "mail." + protocol + ".";

        Properties properties = new Properties();
        properties.put(prefix + "connectiontimeout", String.valueOf(settings.connectionTimeoutMillis()));
        properties.put(prefix + "timeout", String.valueOf(settings.readTimeoutMillis()));
        properties.put(prefix + "writetimeout", String.valueOf(settings.readTimeoutMillis()));
        properties.put(prefix + "peek", "true");
        if (ssl) {
            properties.put(prefix + "ssl.checkserveridentity", "true");
        } else if (settings.startTls()) {
            properties.put(prefix + "starttls.enable", "true");
            properties.put(prefix + "starttls.required", "true");
        }

        Session session = Session.getInstance(properties);
        Store store = session.getStore(protocol);
        store.connect(settings.host(), settings.port(), settings.username(), settings.password());
        return store;
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException ignored) {
                // best effort
            }
        }
    }

    private void closeQuietly(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (MessagingException ignored) {
                // best effort
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------------

    private Message findByUid(Folder folder, String uid) throws MessagingException {
        long parsed;
        try {
            parsed = Long.parseLong(uid);
        } catch (NumberFormatException exception) {
            throw new EmailInboxException(EmailInboxException.Reason.NOT_FOUND);
        }
        Message message = ((UIDFolder) folder).getMessageByUID(parsed);
        if (message == null) {
            throw new EmailInboxException(EmailInboxException.Reason.NOT_FOUND);
        }
        return message;
    }

    private EmailMessageSummary toSummary(Folder folder, Message message) throws MessagingException {
        String uid = String.valueOf(((UIDFolder) folder).getUID(message));
        String preview = "";
        boolean attachment = false;
        try {
            attachment = hasAttachment(message);
            String text = extractText(message, PREVIEW_SCAN_CHARS);
            preview = shorten(text);
        } catch (MessagingException | IOException | RuntimeException exception) {
            // A message with a broken body must not break the whole list.
            log.debug("Could not build preview for message {}", uid, exception);
        }
        return new EmailMessageSummary(
                uid,
                senderOf(message),
                subjectOf(message),
                preview,
                receivedAt(message),
                message.isSet(Flags.Flag.SEEN),
                attachment);
    }

    private String senderOf(Message message) {
        try {
            jakarta.mail.Address[] from = message.getFrom();
            if (from == null || from.length == 0) {
                return "";
            }
            if (from[0] instanceof InternetAddress address) {
                String personal = address.getPersonal();
                return personal != null && !personal.isBlank() ? personal.trim() : address.getAddress();
            }
            return from[0].toString();
        } catch (MessagingException exception) {
            return "";
        }
    }

    private String subjectOf(Message message) {
        try {
            String subject = message.getSubject();
            return subject == null ? "" : subject.trim();
        } catch (MessagingException exception) {
            return "";
        }
    }

    private Instant receivedAt(Message message) {
        try {
            Date date = message.getReceivedDate();
            if (date == null) {
                date = message.getSentDate();
            }
            return date == null ? null : date.toInstant();
        } catch (MessagingException exception) {
            return null;
        }
    }

    private boolean hasAttachment(Part part) throws MessagingException, IOException {
        if (!part.isMimeType("multipart/*")) {
            return false;
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart body = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(body.getDisposition())
                    || (body.getFileName() != null && !body.getFileName().isBlank())
                    || hasAttachment(body)) {
                return true;
            }
        }
        return false;
    }

    private void collectAttachmentNames(Part part, List<String> names) throws MessagingException, IOException {
        if (!part.isMimeType("multipart/*")) {
            return;
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart body = multipart.getBodyPart(i);
            String fileName = body.getFileName();
            if (fileName != null && !fileName.isBlank()) {
                try {
                    names.add(MimeUtility.decodeText(fileName));
                } catch (java.io.UnsupportedEncodingException exception) {
                    names.add(fileName);
                }
            }
            collectAttachmentNames(body, names);
        }
    }

    /**
     * Returns readable text for the message: text/plain when present, otherwise the
     * HTML converted to plain text. Content is only ever displayed as text.
     */
    private String extractText(Part part, int maxChars) throws MessagingException, IOException {
        String text = firstText(part, "text/plain");
        if (text == null) {
            String html = firstText(part, "text/html");
            text = html == null ? null : htmlToText(html);
        }
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private String firstText(Part part, String mimeType) throws MessagingException, IOException {
        if (part.isMimeType(mimeType) && !Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            Object content = part.getContent();
            return content instanceof String string ? string : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = firstText(multipart.getBodyPart(i), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static String htmlToText(String html) {
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }

    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH).trim() + "…";
    }
}
