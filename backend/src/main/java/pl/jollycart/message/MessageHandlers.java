package pl.jollycart.message;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.Part;

import pl.jollycart.web.ContentDisposition;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.PayloadTooLargeException;
import pl.jollycart.web.Router;

/** Endpointy wiadomosci pod /api/conversations (odpowiednik MessageController). */
public final class MessageHandlers {

    private final MessageService messageService;

    public MessageHandlers(MessageService messageService) {
        this.messageService = messageService;
    }

    public void routes(Router router) {
        router.post("/api/conversations/{conversationId}/messages", this::createMessage);
        router.get("/api/conversations/{conversationId}/messages", this::getMessages);
        router.post("/api/conversations/{conversationId}/messages/attachments", this::createAttachmentMessage);
        router.get("/api/conversations/{conversationId}/messages/{messageId}/attachment",
                this::downloadAttachment);
    }

    private void createMessage(Ctx ctx) throws IOException {
        long conversationId = ctx.pathLong("conversationId");
        CreateMessageRequest request = ctx.body(CreateMessageRequest.class);
        ctx.json(201, messageService.createMessage(conversationId, ctx.currentUser(), request));
    }

    private void getMessages(Ctx ctx) throws IOException {
        ctx.json(200, messageService.getConversationMessages(
                ctx.pathLong("conversationId"), ctx.currentUser()));
    }

    /** Pole "file" (do 5 MB) i opcjonalne "content" jako podpis pod plikiem. */
    private void createAttachmentMessage(Ctx ctx) throws IOException {
        long conversationId = ctx.pathLong("conversationId");

        Part file;
        try {
            file = ctx.part("file");
        } catch (PayloadTooLargeException e) {
            throw new PayloadTooLargeException("Plik nie może być większy niż 5 MB");
        }

        String content = ctx.request().getParameter("content");

        ctx.json(201, messageService.createAttachmentMessage(
                conversationId, ctx.currentUser(), file, content));
    }

    /** PDF i obrazy pokazujemy w przegladarce, reszte pobieramy; ?download=true wymusza pobranie. */
    private void downloadAttachment(Ctx ctx) throws IOException {
        boolean download = Boolean.parseBoolean(ctx.query("download"));

        MessageService.AttachmentDownload attachment = messageService.getAttachment(
                ctx.pathLong("conversationId"), ctx.pathLong("messageId"), ctx.currentUser());

        String contentType = attachment.file().contentType();
        boolean previewable = "application/pdf".equals(contentType) || contentType.startsWith("image/");

        ctx.sendFile(attachment.path(), contentType, Map.of(
                "Content-Disposition", ContentDisposition.of(previewable && !download,
                        attachment.file().originalFileName()),
                "X-Content-Type-Options", "nosniff",
                "Cache-Control", "private, max-age=3600"
        ));
    }
}
