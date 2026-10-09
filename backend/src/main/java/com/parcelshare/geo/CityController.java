package com.parcelshare.geo;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CityController {

    private final CityRepository cities;

    @GetMapping("/cities")
    @Transactional(readOnly = true)
    public List<CityDto> list() {
        return cities.findAllByOrderByNameAsc().stream().map(CityDto::of).toList();
    }
}
