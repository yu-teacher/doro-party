package com.doro.party.domain.user.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IamSyncStateRepository extends JpaRepository<IamSyncState, String> {
}
