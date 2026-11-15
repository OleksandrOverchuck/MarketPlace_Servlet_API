package pl.jollycart.job;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.Part;

import pl.jollycart.db.Db;
import pl.jollycart.file.CvFileService;
import pl.jollycart.file.StoredFile;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.offer.OfferType;
import pl.jollycart.user.User;

/** Aplikacje na oferty pracy (odpowiednik JobApplicationService). */
public final class JobApplicationService {

    private final JobApplicationDao jobApplicationDao;
    private final OfferDao offerDao;
    private final CvFileService cvFileService;

    public JobApplicationService(JobApplicationDao jobApplicationDao, OfferDao offerDao,
                                 CvFileService cvFileService) {
        this.jobApplicationDao = jobApplicationDao;
        this.offerDao = offerDao;
        this.cvFileService = cvFileService;
    }

    public JobApplicationResponse createApplication(long offerId, User user, CreateJobApplicationRequest request) {
        return Db.inTransaction(() -> {
            Offer offer = findOffer(offerId);

            if (offer.type() != OfferType.JOB) {
                throw new IllegalArgumentException("Można aplikować tylko na oferty typu JOB");
            }

            if (offer.userId().equals(user.id())) {
                throw new IllegalArgumentException("Nie możesz aplikować na własną ofertę");
            }

            if (jobApplicationDao.existsByOfferAndUser(offerId, user.id())) {
                throw new IllegalArgumentException("Już aplikowałeś na tę ofertę");
            }

            LocalDateTime now = LocalDateTime.now();
            long id = jobApplicationDao.insert(offerId, user.id(), request.message(), now);

            return new JobApplicationResponse(
                    id, offer.id(), offer.title(), user.id(), user.nickname(),
                    JobApplicationStatus.PENDING, request.message(), now, now);
        });
    }

    public List<JobApplicationResponse> getMyApplications(User user) {
        return jobApplicationDao.findResponsesByUserId(user.id());
    }

    public List<JobApplicationResponse> getApplicationsForOffer(long offerId, User currentUser) {
        Offer offer = findOffer(offerId);

        if (!offer.userId().equals(currentUser.id())) {
            throw new IllegalArgumentException("Nie możesz przeglądać aplikacji do cudzej oferty");
        }

        return jobApplicationDao.findResponsesByOfferId(offerId);
    }

    /** CV dodaje sam kandydat, jednorazowo, w formacie PDF. */
    public CvFileResponse uploadCv(long applicationId, User user, Part file) {
        return Db.inTransaction(() -> {
            JobApplication application = jobApplicationDao.findById(applicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Aplikacja nie została znaleziona"));

            if (!application.userId().equals(user.id())) {
                throw new IllegalArgumentException("Nie możesz dodać CV do cudzej aplikacji");
            }

            if (application.cvFileId() != null) {
                throw new IllegalArgumentException("CV zostało już dodane do tej aplikacji");
            }

            StoredFile storedFile = cvFileService.storeCv(file);
            jobApplicationDao.attachCv(application.id(), storedFile.id());

            return CvFileResponse.from(storedFile);
        });
    }

    private Offer findOffer(long offerId) {
        return offerDao.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));
    }
}
