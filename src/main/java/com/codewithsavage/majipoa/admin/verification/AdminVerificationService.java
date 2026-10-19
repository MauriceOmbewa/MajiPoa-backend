package com.codewithsavage.majipoa.admin.verification;

import com.codewithsavage.majipoa.shared.model.VendorDocument;
import com.codewithsavage.majipoa.shared.model.VendorDocument.DocStatus;
import com.codewithsavage.majipoa.shared.model.VendorProfile;
import com.codewithsavage.majipoa.shared.model.VendorProfile.VerificationStatus;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import com.codewithsavage.majipoa.shared.repository.VendorDocumentRepository;
import com.codewithsavage.majipoa.shared.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Verification page: review queue, one vendor's documents, and the decisions.
 *
 * Notes to the vendor are saved on the vendor (verificationNote) and on documents (adminNote)
 * so they show on the vendor's dashboard. Sending a real SMS is left as a hook: see notifyVendor().
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVerificationService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private final VendorProfileRepository vendorRepo;
    private final VendorDocumentRepository docRepo;
    private final UserRepository userRepo;

    // ─── Queue ───────────────────────────────────────────────────────────────

    public AdminVerificationDtos.QueueResponse getQueue() {
        LocalDate today = LocalDate.now(ZONE);
        List<VendorProfile> vendors = vendorRepo.findAll();

        Map<Long, List<VendorDocument>> docsByVendor = docRepo.findAll().stream()
                .filter(d -> d.getVendor() != null)
                .collect(Collectors.groupingBy(d -> d.getVendor().getId()));

        // New applications, newest first
        List<AdminVerificationDtos.QueueItemDto> items = vendors.stream()
                .filter(v -> v.getVerificationStatus() == VerificationStatus.PENDING)
                .sorted(Comparator.comparing(VendorProfile::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(v -> new AdminVerificationDtos.QueueItemDto(
                        v.getId(), v.getBusinessName(), firstArea(v),
                        "APPLICATION", "New application", v.getCreatedAt()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Renewals: approved vendors that have a document expiring or expired
        vendors.stream()
                .filter(v -> v.getVerificationStatus() == VerificationStatus.APPROVED)
                .forEach(v -> docsByVendor.getOrDefault(v.getId(), List.of()).stream()
                        .filter(d -> {
                            String s = docState(d, today);
                            return s.equals("EXPIRING") || s.equals("EXPIRED");
                        })
                        .findFirst()
                        .ifPresent(d -> items.add(new AdminVerificationDtos.QueueItemDto(
                                v.getId(), v.getBusinessName(), firstArea(v),
                                "RENEWAL", d.getName() + " renewal", toInstant(d.getSubmittedOn())))));

        OptionalDouble avg = vendors.stream()
                .filter(v -> v.getReviewedAt() != null && v.getCreatedAt() != null)
                .mapToDouble(v -> Duration.between(v.getCreatedAt(), v.getReviewedAt()).toMinutes() / 60.0)
                .average();
        Double averageHours = avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null;

        return new AdminVerificationDtos.QueueResponse(items, items.size(), averageHours);
    }

    // ─── Detail ──────────────────────────────────────────────────────────────

    public AdminVerificationDtos.DetailDto getDetail(Long vendorId) {
        return toDetail(findVendor(vendorId));
    }

    // ─── Document decisions ──────────────────────────────────────────────────

    @Transactional
    public AdminVerificationDtos.DetailDto approveDocument(Long vendorId, Long docId, String adminSub) {
        VendorProfile vendor = findVendor(vendorId);
        VendorDocument doc = findDoc(vendorId, docId);

        String state = docState(doc, LocalDate.now(ZONE));
        if (state.equals("MISSING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document has not been submitted yet");
        }
        if (state.equals("EXPIRED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document has expired");
        }

        doc.setStatus(DocStatus.APPROVED);
        doc.setAdminNote(null);
        doc.setReviewedBy(adminLabel(adminSub));
        doc.setReviewedAt(Instant.now());
        docRepo.save(doc);
        return toDetail(vendor);
    }

    @Transactional
    public AdminVerificationDtos.DetailDto queryDocument(Long vendorId, Long docId, String note, String adminSub) {
        return leaveDocumentNote(vendorId, docId, requireNote(note), adminSub);
    }

    /** Ask the vendor to upload a missing document. The note is optional. */
    @Transactional
    public AdminVerificationDtos.DetailDto requestDocument(Long vendorId, Long docId, String note, String adminSub) {
        VendorDocument doc = findDoc(vendorId, docId);
        String text = (note == null || note.isBlank())
                ? "Please upload your " + doc.getName() + "."
                : note.trim();
        return leaveDocumentNote(vendorId, docId, text, adminSub);
    }

    private AdminVerificationDtos.DetailDto leaveDocumentNote(Long vendorId, Long docId, String note, String adminSub) {
        VendorProfile vendor = findVendor(vendorId);
        VendorDocument doc = findDoc(vendorId, docId);

        // A queried document needs a fresh look, so it can no longer count as approved
        if (doc.getStatus() == DocStatus.APPROVED) {
            doc.setStatus(DocStatus.NOT_SUBMITTED);
        }
        doc.setAdminNote(note);
        doc.setReviewedBy(adminLabel(adminSub));
        doc.setReviewedAt(Instant.now());
        docRepo.save(doc);
        return toDetail(vendor);
    }

    // ─── Vendor decisions ────────────────────────────────────────────────────

    @Transactional
    public AdminVerificationDtos.DetailDto approveVendor(Long vendorId, String note, String adminSub) {
        VendorProfile vendor = findVendor(vendorId);

        if (vendor.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vendor is already approved");
        }
        if (!allDocumentsOk(vendor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Every document must be approved and valid before the vendor can be approved");
        }

        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerificationNote(blankToNull(note));
        stampReview(vendor, adminSub, true);
        vendorRepo.save(vendor);
        notifyVendor(vendor, note);
        return toDetail(vendor);
    }

    @Transactional
    public AdminVerificationDtos.DetailDto requestChanges(Long vendorId, String note, String adminSub) {
        VendorProfile vendor = findVendor(vendorId);
        String text = requireNote(note);

        vendor.setVerificationStatus(VerificationStatus.CHANGES_REQUESTED);
        vendor.setVerificationNote(text);
        stampReview(vendor, adminSub, false);   // not a final decision, so reviewedAt stays empty
        vendorRepo.save(vendor);
        notifyVendor(vendor, text);
        return toDetail(vendor);
    }

    @Transactional
    public AdminVerificationDtos.DetailDto rejectVendor(Long vendorId, String note, String adminSub) {
        VendorProfile vendor = findVendor(vendorId);
        String text = requireNote(note);

        vendor.setVerificationStatus(VerificationStatus.REJECTED);
        vendor.setVerificationNote(text);
        stampReview(vendor, adminSub, true);
        vendorRepo.save(vendor);
        notifyVendor(vendor, text);
        return toDetail(vendor);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private AdminVerificationDtos.DetailDto toDetail(VendorProfile v) {
        LocalDate today = LocalDate.now(ZONE);
        List<VendorDocument> docs = docRepo.findByVendorId(v.getId());

        List<AdminVerificationDtos.DocumentDto> docDtos = docs.stream()
                .sorted(Comparator.comparing(VendorDocument::getId))
                .map(d -> toDocDto(d, today))
                .toList();

        String registration = docs.stream()
                .filter(d -> d.getName() != null && d.getName().toLowerCase().contains("registration"))
                .map(VendorDocument::getReference)
                .filter(r -> r != null && !r.isBlank())
                .findFirst().orElse(null);

        return new AdminVerificationDtos.DetailDto(
                v.getId(),
                v.getBusinessName(),
                v.getUser() != null ? v.getUser().getName() : null,
                v.getPhone(),
                v.getCreatedAt(),
                statusOf(v),
                v.getWaterSource(),
                v.getTreatment(),
                v.getProductsDeclared(),
                v.getAreas(),
                v.getSettlement(),
                registration,
                docDtos,
                v.getVerificationNote(),
                !docs.isEmpty() && allDocumentsOk(docs, today));
    }

    private AdminVerificationDtos.DocumentDto toDocDto(VendorDocument d, LocalDate today) {
        String state = docState(d, today);
        return new AdminVerificationDtos.DocumentDto(
                d.getId(), d.getName(), d.getReference(),
                d.getSubmittedOn(), d.getValidUntil(),
                state, checkLabel(state), d.getAdminNote());
    }

    private String statusOf(VendorProfile v) {
        return switch (v.getVerificationStatus()) {
            case PENDING -> "UNDER_REVIEW";
            case APPROVED -> "APPROVED";
            case SUSPENDED -> "SUSPENDED";
            case CHANGES_REQUESTED -> "CHANGES_REQUESTED";
            case REJECTED -> "REJECTED";
        };
    }

    /**
     * Document state shown on the page, worked out from the stored fields:
     *   MISSING            nothing submitted yet
     *   AWAITING_DECISION  submitted, waiting for an admin
     *   QUERIED            an admin asked the vendor a question
     *   APPROVED / EXPIRING / EXPIRED
     */
    private String docState(VendorDocument d, LocalDate today) {
        boolean expired = d.getValidUntil() != null && d.getValidUntil().isBefore(today);
        return switch (d.getStatus()) {
            case APPROVED -> expired ? "EXPIRED" : "APPROVED";
            case EXPIRING -> expired ? "EXPIRED" : "EXPIRING";
            case NOT_SUBMITTED -> {
                boolean submitted = d.getSubmittedOn() != null
                        || (d.getReference() != null && !d.getReference().isBlank());
                if (!submitted) yield "MISSING";
                yield (d.getAdminNote() != null && !d.getAdminNote().isBlank())
                        ? "QUERIED" : "AWAITING_DECISION";
            }
        };
    }

    private String checkLabel(String state) {
        return switch (state) {
            case "APPROVED" -> "Valid";
            case "EXPIRING" -> "Expiring soon";
            case "EXPIRED" -> "Expired";
            case "MISSING" -> "Missing";
            case "QUERIED" -> "Queried";
            default -> "Awaiting review";
        };
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean allDocumentsOk(VendorProfile v) {
        List<VendorDocument> docs = docRepo.findByVendorId(v.getId());
        return !docs.isEmpty() && allDocumentsOk(docs, LocalDate.now(ZONE));
    }

    /** Approved, or still valid but expiring soon, counts as OK. */
    private boolean allDocumentsOk(List<VendorDocument> docs, LocalDate today) {
        return docs.stream().allMatch(d -> {
            String s = docState(d, today);
            return s.equals("APPROVED") || s.equals("EXPIRING");
        });
    }

    private void stampReview(VendorProfile v, String adminSub, boolean finalDecision) {
        v.setReviewedBy(adminLabel(adminSub));
        if (finalDecision) v.setReviewedAt(Instant.now());
    }

    private VendorProfile findVendor(Long id) {
        return vendorRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    private VendorDocument findDoc(Long vendorId, Long docId) {
        VendorDocument d = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (d.getVendor() == null || !d.getVendor().getId().equals(vendorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found for this vendor");
        }
        return d;
    }

    private String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note to the vendor is required");
        }
        return note.trim();
    }

    private String adminLabel(String googleSub) {
        if (googleSub == null) return "admin";
        return userRepo.findByGoogleSub(googleSub)
                .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                .orElse(googleSub);
    }

    private String firstArea(VendorProfile v) {
        if (v.getAreas() == null || v.getAreas().isBlank()) return null;
        return v.getAreas().split(",")[0].trim();
    }

    private Instant toInstant(LocalDate d) {
        return d == null ? null : d.atStartOfDay(ZONE).toInstant();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * The note is already saved and shows on the vendor's dashboard.
     * TODO: plug in an SMS provider here (e.g. Africa's Talking) to also send it by SMS.
     */
    private void notifyVendor(VendorProfile vendor, String message) {
        if (message == null || message.isBlank()) return;
        log.info("Vendor note saved for vendor {} ({}): {}", vendor.getId(), vendor.getBusinessName(), message);
    }
}