package com.expansion.server.domain.editor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "frames")
@Getter
@NoArgsConstructor
public class Frame {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "frame_id")
    private Long frameId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "frame_order", nullable = false)
    private int frameOrder;

    @Column(name = "duration", nullable = false)
    private int duration;

    @Builder
    public Frame(Project project, int frameOrder, int duration){
        this.project = project;
        this.frameOrder = frameOrder;
        this.duration = duration;
    }
}
