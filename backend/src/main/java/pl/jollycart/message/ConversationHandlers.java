package pl.jollycart.message;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/conversations (odpowiednik ConversationController). */
public final class ConversationHandlers {

    private final ConversationService conversationService;

    public ConversationHandlers(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void routes(Router router) {
        router.post("/api/conversations", this::create);
        router.get("/api/conversations", this::list);
        router.get("/api/conversations/{id}", this::getOne);
    }

    private void create(Ctx ctx) throws IOException {
        CreateConversationRequest request = ctx.body(CreateConversationRequest.class);
        ctx.json(201, conversationService.createConversation(
                ctx.currentUser(), request.otherUserId(), request.offerId()));
    }

    private void list(Ctx ctx) throws IOException {
        ctx.json(200, conversationService.getUserConversations(ctx.currentUser()));
    }

    private void getOne(Ctx ctx) throws IOException {
        ctx.json(200, conversationService.getConversation(ctx.pathLong("id"), ctx.currentUser()));
    }
}
