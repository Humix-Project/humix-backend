package com.humix.api.domain.humming.entity;

import com.humix.api.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "humming")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Humming {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "humming_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uuid", nullable = false)
    private Member member;

    @Column(name = "s3_file_url", length = 512, nullable = false)
    private String s3FileUrl;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Humming(Member member, String s3FileUrl, Integer durationSeconds) {
        this.member = member;
        this.s3FileUrl = s3FileUrl;
        this.durationSeconds = durationSeconds;
    }
}