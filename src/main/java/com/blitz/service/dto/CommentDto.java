package com.blitz.service.dto;

import com.blitz.domain.Comment;
import com.blitz.domain.Posts;
import com.blitz.domain.User;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommentDto {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Request {
        private Long id;
        private String comment;
        private String createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        private String modifiedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        private User user;
        private Posts posts;

        public Comment toEntity() {
            Comment comments = Comment.builder()
                    .id(id)
                    .comment(comment)
                    .createdDate(createdDate)
                    .modifiedDate(modifiedDate)
                    .user(user)
                    .posts(posts)
                    .build();

            return comments;
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class Response {
        private Long id;
        private String comment;
        private String createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        private String modifiedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        private String nickname;
        private Long userId;
        private Long postsId;

        public Response(Comment comment) {
            this.id = comment.getId();
            this.comment = comment.getComment();
            this.createdDate = comment.getCreatedDate();
            this.modifiedDate = comment.getModifiedDate();
            this.nickname = comment.getUser().getNickname();
            this.userId = comment.getUser().getId();
            this.postsId = comment.getPosts().getId();
        }
    }
}
