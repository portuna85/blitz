package com.blitz.domain.posts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostsRepository extends JpaRepository<Posts, Long> {

    Page<PostsSummary> findAllProjectedBy(Pageable pageable);
}
