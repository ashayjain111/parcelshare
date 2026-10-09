package com.parcelshare.parcel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelCategoryRepository extends JpaRepository<ParcelCategory, Long> {

    List<ParcelCategory> findByActiveTrueOrderByTierAscNameAsc();
}
