package com.humix.api.domain.melodyScore.entity;

import com.humix.api.domain.humming.entity.Humming;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "melody_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MelodyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "melody_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humming_id", nullable = false)
    private Humming humming;

    @Lob // 데이터베이스의 LONGTEXT로 매핑됩니다.
    @Column(name = "notes_data", columnDefinition = "LONGTEXT", nullable = false)
    private String notesData;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public MelodyScore(Humming humming, String notesData) {
        this.humming = humming;
        this.notesData = notesData;
    }

    // 악보 수정 시 사용할 편의 메서드
    public void updateNotesData(String notesData) {
        this.notesData = notesData;
    }
}