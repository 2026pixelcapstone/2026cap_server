package com.expansion.server.domain.gallery.repository;

import com.expansion.server.domain.gallery.entity.GalleryComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryCommentRepository extends JpaRepository<GalleryComment, Long> {

    // 최상위 댓글만 (대댓글 제외)
    Page<GalleryComment> findByPost_PostIdAndParentIsNull(Long postId, Pageable pageable);

    // 특정 댓글의 대댓글
    Page<GalleryComment> findByParent_CommentId(Long parentId, Pageable pageable);

    // 게시글 삭제 시 연결된 댓글 전체 삭제
    void deleteByPost_PostId(Long postId);
}
