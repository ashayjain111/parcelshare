package com.parcelshare.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByVerifyToken(String token);

    Optional<AppUser> findByResetTokenHash(String hash);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdentityRefAndIdNot(String identityRef, Long id);

    List<AppUser> findByTravellerVerification(AppUser.Verification v);
}
