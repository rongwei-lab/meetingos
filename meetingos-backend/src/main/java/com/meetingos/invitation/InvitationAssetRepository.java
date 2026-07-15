package com.meetingos.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvitationAssetRepository extends JpaRepository<InvitationAsset, Long> {
    List<InvitationAsset> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);
    List<InvitationAsset> findByIdIn(Collection<Long> ids);
    Optional<InvitationAsset> findByIdAndMeetingId(Long id, Long meetingId);
}
