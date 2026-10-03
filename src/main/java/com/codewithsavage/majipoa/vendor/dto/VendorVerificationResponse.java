package com.codewithsavage.majipoa.vendor.dto;

import com.codewithsavage.majipoa.shared.model.VendorDocument.DocStatus;
import com.codewithsavage.majipoa.shared.model.VendorProfile.SubscriptionPlan;
import com.codewithsavage.majipoa.shared.model.VendorProfile.VerificationStatus;

import java.time.LocalDate;
import java.util.List;

public record VendorVerificationResponse(
        String             businessName,
        String             waterSource,
        String             treatment,
        String             phValue,
        String             tdsValue,
        String             about,
        VerificationStatus verificationStatus,
        SubscriptionPlan   plan,
        List<VendorDocumentDto> documents
) {
    public record VendorDocumentDto(
            Long      id,
            String    name,
            String    note,
            String    reference,
            LocalDate submittedOn,
            LocalDate validUntil,
            DocStatus status
    ) {}
}
