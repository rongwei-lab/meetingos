package com.meetingos.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    @Query("""
            select u from AppUser u
            where (:keyword is null or lower(u.username) like lower(concat('%', :keyword, '%')) or lower(u.displayName) like lower(concat('%', :keyword, '%')))
              and (:status is null or u.status = :status)
            """)
    Page<AppUser> search(@Param("keyword") String keyword,
                         @Param("status") UserStatus status,
                         Pageable pageable);
}
