package com.parcelshare.parcel;

import com.parcelshare.common.ApiException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parcel policy: since the platform never inspects anything physically, it must refuse risky items up front.
 * Prohibited => rejected. Restricted => allowed but held for admin review. Allowed => goes live immediately.
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    public record Decision(Parcel.Status status, String flagReason) {
    }

    private final BlockedKeywordRepository keywords;

    @Transactional(readOnly = true)
    public Decision evaluate(ParcelCategory category, int weightGrams, long declaredValuePaise, String description) {
        if (!category.isActive()) {
            throw ApiException.bad("This category is not available");
        }
        if (category.getTier() == ParcelCategory.Tier.PROHIBITED) {
            throw ApiException.bad("\"" + category.getName() + "\" cannot be sent through ParcelShare");
        }
        if (weightGrams > category.getMaxGrams()) {
            throw ApiException.bad("Maximum weight for " + category.getName() + " is "
                    + (category.getMaxGrams() / 1000.0) + " kg");
        }
        if (declaredValuePaise > category.getMaxValuePaise()) {
            throw ApiException.bad("Maximum declared value for " + category.getName() + " is Rs "
                    + (category.getMaxValuePaise() / 100));
        }
        String lower = description.toLowerCase(Locale.ROOT);
        for (BlockedKeyword k : keywords.findAll()) {
            if (lower.contains(k.getWord().toLowerCase(Locale.ROOT))) {
                throw ApiException.bad("Your description mentions \"" + k.getWord()
                        + "\", which is not allowed. Please review the prohibited items list.");
            }
        }
        if (category.getTier() == ParcelCategory.Tier.RESTRICTED) {
            return new Decision(Parcel.Status.PENDING_REVIEW, "Restricted category: " + category.getName());
        }
        return new Decision(Parcel.Status.OPEN, null);
    }
}
