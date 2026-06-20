package com.humix.api.domain.humming.repository;

import com.humix.api.domain.humming.entity.Humming;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HummingRepository extends JpaRepository<Humming, Long> {
}
