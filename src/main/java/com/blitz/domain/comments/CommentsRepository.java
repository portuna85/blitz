package com.blitz.domain.comments;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentsRepository extends JpaRepository<Comments, Long> {

    Page<Comments> findByPostIdAndParentIdIsNull(Long postId, Pageable pageable);

    List<Comments> findByPostIdAndParentIdIn(Long postId, List<Long> parentIds, Sort sort);

    long countByPostIdAndDeletedFalse(Long postId);

    Optional<Comments> findByIdAndPostId(Long id, Long postId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Comments c where c.id = :id and c.postId = :postId")
    Optional<Comments> findByIdAndPostIdForUpdate(@Param("id") Long id, @Param("postId") Long postId);

    void deleteByPostId(Long postId);

    @Query("select c.id from Comments c where c.postId = :postId and c.parentId is null order by c.createdDate asc, c.id asc")
    List<Long> findTopLevelIdsOrdered(@Param("postId") Long postId);
}
