package com.parcelshare.geo;

public record CityDto(Long id, String name, String state, Long regionId, String regionName) {

    public static CityDto of(City c) {
        return new CityDto(c.getId(), c.getName(), c.getState(), c.getRegion().getId(), c.getRegion().getName());
    }
}
