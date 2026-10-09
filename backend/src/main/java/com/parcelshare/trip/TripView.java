package com.parcelshare.trip;

import com.parcelshare.geo.CityDto;
import com.parcelshare.identity.UserSummary;
import java.time.Instant;

public record TripView(Long id, UserSummary traveller, CityDto origin, CityDto destination, Instant departsAt,
                       String travelMode, int capacityGrams, int reservedGrams, int pricePerKgPaise, String status) {

    public static TripView of(Trip t) {
        return new TripView(t.getId(), UserSummary.of(t.getTraveller()), CityDto.of(t.getOrigin()),
                CityDto.of(t.getDestination()), t.getDepartsAt(), t.getTravelMode().name(), t.getCapacityGrams(),
                t.getReservedGrams(), t.getPricePerKgPaise(), t.getStatus().name());
    }
}
