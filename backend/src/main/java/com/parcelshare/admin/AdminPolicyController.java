package com.parcelshare.admin;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AuditService;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.parcel.BlockedKeyword;
import com.parcelshare.parcel.BlockedKeywordRepository;
import com.parcelshare.parcel.ParcelCategory;
import com.parcelshare.parcel.ParcelCategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Parcel policy is data, not code: admins can tighten or relax it without a deployment. All changes are audited. */
@RestController
@RequestMapping("/admin/policy")
@RequiredArgsConstructor
public class AdminPolicyController {

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @NotNull ParcelCategory.Tier tier,
                                  @Min(0) @Max(30000) int maxGrams, @Min(0) long maxValuePaise, boolean active) {
    }

    public record CategoryView(Long id, String name, String tier, int maxGrams, long maxValuePaise, boolean active) {
        static CategoryView of(ParcelCategory c) {
            return new CategoryView(c.getId(), c.getName(), c.getTier().name(), c.getMaxGrams(), c.getMaxValuePaise(), c.isActive());
        }
    }

    public record KeywordRequest(@NotBlank @Size(max = 60) String word) {
    }

    public record KeywordView(Long id, String word) {
    }

    private final ParcelCategoryRepository categories;
    private final BlockedKeywordRepository keywords;
    private final AuditService audit;

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categories.findAll().stream()
                .sorted((a, b) -> a.getTier() != b.getTier() ? a.getTier().compareTo(b.getTier()) : a.getName().compareTo(b.getName()))
                .map(CategoryView::of).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CategoryView createCategory(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CategoryRequest req) {
        ParcelCategory c = new ParcelCategory();
        apply(c, req);
        c = categories.save(c);
        audit.log(me.id(), "CATEGORY_CREATED", "category", c.getId(), c.getName() + " " + c.getTier());
        return CategoryView.of(c);
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public CategoryView updateCategory(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody CategoryRequest req) {
        ParcelCategory c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
        String before = c.getName() + " " + c.getTier() + " " + c.getMaxGrams() + "g/" + c.getMaxValuePaise() + "p active=" + c.isActive();
        apply(c, req);
        audit.log(me.id(), "CATEGORY_UPDATED", "category", id, "before: " + before + " | after: " + c.getName() + " "
                + c.getTier() + " " + c.getMaxGrams() + "g/" + c.getMaxValuePaise() + "p active=" + c.isActive());
        return CategoryView.of(c);
    }

    private void apply(ParcelCategory c, CategoryRequest req) {
        c.setName(req.name().trim());
        c.setTier(req.tier());
        c.setMaxGrams(req.tier() == ParcelCategory.Tier.PROHIBITED ? 0 : req.maxGrams());
        c.setMaxValuePaise(req.tier() == ParcelCategory.Tier.PROHIBITED ? 0 : req.maxValuePaise());
        c.setActive(req.active());
    }

    @GetMapping("/keywords")
    @Transactional(readOnly = true)
    public List<KeywordView> listKeywords() {
        return keywords.findAll().stream().map(k -> new KeywordView(k.getId(), k.getWord()))
                .sorted((a, b) -> a.word().compareTo(b.word())).toList();
    }

    @PostMapping("/keywords")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public KeywordView addKeyword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody KeywordRequest req) {
        String word = req.word().trim().toLowerCase(Locale.ROOT);
        if (keywords.findAll().stream().anyMatch(k -> k.getWord().equalsIgnoreCase(word))) {
            throw ApiException.conflict("That keyword is already blocked");
        }
        BlockedKeyword k = keywords.save(new BlockedKeyword(word));
        audit.log(me.id(), "KEYWORD_ADDED", "keyword", k.getId(), word);
        return new KeywordView(k.getId(), k.getWord());
    }

    @DeleteMapping("/keywords/{id}")
    @Transactional
    public void removeKeyword(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        BlockedKeyword k = keywords.findById(id).orElseThrow(() -> ApiException.notFound("Keyword not found"));
        keywords.delete(k);
        audit.log(me.id(), "KEYWORD_REMOVED", "keyword", id, k.getWord());
    }
}
