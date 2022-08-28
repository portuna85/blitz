package com.blitz.repository.persistence;

import com.blitz.domain.Comment;
import com.blitz.domain.Posts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> getCommentByPostsOrderById(Posts posts);
}
