package com.parcelshare.identity;

import com.parcelshare.geo.City;
import com.parcelshare.geo.CityRepository;
import com.parcelshare.trip.Trip;
import com.parcelshare.trip.TripRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optional demo accounts and trips so the app is explorable straight after startup.
 * Disabled unless parcelshare.seed-demo=true (set SEED_DEMO=true). NEVER enable in production:
 * the accounts use a known password.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "parcelshare.seed-demo", havingValue = "true")
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);
    static final String DEMO_PASSWORD = "demo12345";

    private final UserRepository users;
    private final CityRepository cities;
    private final TripRepository trips;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase("rahul@demo.local")) {
            return;
        }
        AppUser rahul = user("rahul@demo.local", "Rahul Verma", "9810000001", true);
        AppUser priya = user("priya@demo.local", "Priya Singh", "9810000002", true);
        user("ananya@demo.local", "Ananya Gupta", "9810000003", false);

        rahul.setRatingSum(98);
        rahul.setRatingCount(20);
        rahul.setCompletedDeliveries(19);
        priya.setRatingSum(47);
        priya.setRatingCount(10);
        priya.setCompletedDeliveries(9);
        users.save(rahul);
        users.save(priya);

        trip(rahul, "Delhi", "Lucknow", 30, 5000, 10000, Trip.Mode.BUS);
        trip(priya, "Noida", "Lucknow", 32, 3000, 8000, Trip.Mode.TRAIN);
        trip(rahul, "Delhi", "Jaipur", 70, 4000, 9000, Trip.Mode.CAR);
        log.info("Demo data created. Log in with rahul@demo.local / priya@demo.local (travellers) or ananya@demo.local (sender), password '{}'.",
                DEMO_PASSWORD);
    }

    private AppUser user(String email, String name, String phone, boolean traveller) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(name);
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setEmailVerified(true);
        if (traveller) {
            u.setTravellerVerification(AppUser.Verification.APPROVED);
        }
        return users.save(u);
    }

    private void trip(AppUser traveller, String from, String to, int hoursAhead, int capacityGrams, int pricePerKg, Trip.Mode mode) {
        Trip t = new Trip();
        t.setTraveller(traveller);
        t.setOrigin(city(from));
        t.setDestination(city(to));
        t.setDepartsAt(Instant.now().plus(hoursAhead, ChronoUnit.HOURS));
        t.setTravelMode(mode);
        t.setCapacityGrams(capacityGrams);
        t.setPricePerKgPaise(pricePerKg);
        trips.save(t);
    }

    private City city(String name) {
        return cities.findAll().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }
}
