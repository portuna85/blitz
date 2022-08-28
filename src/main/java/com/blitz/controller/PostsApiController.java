package com.blitz.controller;

import com.blitz.service.PostsService;
import com.blitz.service.dto.PostsDto;
import com.blitz.service.dto.UserDto;
import com.blitz.service.security.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class PostsApiController {

    private final PostsService postsService;

    @PostMapping("/posts")
    public ResponseEntity save(@RequestBody PostsDto.Request dto, @LoginUser UserDto.Response user) {
        return ResponseEntity.ok(postsService.save(dto, user.getNickname()));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity read(@PathVariable Long id) {
        return ResponseEntity.ok(postsService.findById(id));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity update(@PathVariable Long id, @RequestBody PostsDto.Request dto) {
        postsService.update(id, dto);
        return ResponseEntity.ok(id);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity delete(@PathVariable Long id) {
        postsService.delete(id);
        return ResponseEntity.ok(id);
    }
}
