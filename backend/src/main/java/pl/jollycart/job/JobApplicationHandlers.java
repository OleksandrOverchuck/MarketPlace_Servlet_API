package pl.jollycart.job;

import java.io.IOException;

import jakarta.servlet.http.Part;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/job-applications (odpowiednik JobApplicationController). */
public final class JobApplicationHandlers {

    private final JobApplicationService jobApplicationService;

    public JobApplicationHandlers(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    public void routes(Router router) {
        router.post("/api/job-applications/offers/{offerId}", this::create);
        router.get("/api/job-applications/me", this::getMine);
        router.get("/api/job-applications/offers/{offerId}", this::getForOffer);
        router.post("/api/job-applications/{applicationId}/cv", this::uploadCv);
    }

    private void create(Ctx ctx) throws IOException {
        long offerId = ctx.pathLong("offerId");
        CreateJobApplicationRequest request = ctx.body(CreateJobApplicationRequest.class);
        ctx.json(201, jobApplicationService.createApplication(offerId, ctx.currentUser(), request));
    }

    private void getMine(Ctx ctx) throws IOException {
        ctx.json(200, jobApplicationService.getMyApplications(ctx.currentUser()));
    }

    private void getForOffer(Ctx ctx) throws IOException {
        ctx.json(200, jobApplicationService.getApplicationsForOffer(
                ctx.pathLong("offerId"), ctx.currentUser()));
    }

    private void uploadCv(Ctx ctx) throws IOException {
        long applicationId = ctx.pathLong("applicationId");
        Part file = ctx.part("file");
        ctx.json(200, jobApplicationService.uploadCv(applicationId, ctx.currentUser(), file));
    }
}
