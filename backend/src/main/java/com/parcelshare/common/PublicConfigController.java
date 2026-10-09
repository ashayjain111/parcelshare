package com.parcelshare.common;

import com.parcelshare.parcel.ParcelCategoryRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-sensitive information the frontend needs before login. */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicConfigController {

    public record PublicCategory(String name, String tier, int maxGrams, long maxValuePaise) {
    }

    private final AppProps props;
    private final ParcelCategoryRepository categories;

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("demoMode", props.demoMode());
    }

    /** The live allowed / restricted / prohibited lists, so the public policy page can never drift from the rules enforced. */
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<PublicCategory> categories() {
        return categories.findByActiveTrueOrderByTierAscNameAsc().stream()
                .map(c -> new PublicCategory(c.getName(), c.getTier().name(), c.getMaxGrams(), c.getMaxValuePaise())).toList();
    }
}
