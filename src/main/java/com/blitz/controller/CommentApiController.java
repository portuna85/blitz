package com.blitz.controller;

import com.blitz.service.CommentService;
import com.blitz.service.dto.CommentDto;
import com.blitz.service.dto.UserDto;
import com.blitz.service.security.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class CommentApiController {

    private final CommentService commentService;

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity save(@PathVariable Long id, @RequestBody CommentDto.Request dto,
                               @LoginUser UserDto.Response userSessionDto) {
        return ResponseEntity.ok(commentService.save(id, userSessionDto.getNickname(), dto));
    }

    @GetMapping("/posts/{id}/comments")
    public List<CommentDto.Response> read(@PathVariable Long id) {
        return commentService.findAll(id);
    }

    @PutMapping({"/posts/{id}/comments/{id}"})
    public ResponseEntity update(@PathVariable Long id, @RequestBody CommentDto.Request dto) {
        commentService.update(id, dto);
        return ResponseEntity.ok(id);
    }

    @DeleteMapping("/posts/{id}/comments/{id}")
    public ResponseEntity delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.ok(id);
    }
}
