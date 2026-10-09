package com.parcelshare.geo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByNameAsc();
}
