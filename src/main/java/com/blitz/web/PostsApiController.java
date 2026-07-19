package com.blitz.web;

import com.blitz.config.auth.LoginUser;
import com.blitz.config.auth.dto.SessionUser;
import com.blitz.service.PostsService;
import com.blitz.web.dto.PageResponse;
import com.blitz.web.dto.PostsListResponseDto;
import com.blitz.web.dto.PostsResponseDto;
import com.blitz.web.dto.PostsSaveRequestDto;
import com.blitz.web.dto.PostsUpdateRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class PostsApiController {

    private final PostsService postsService;

    @PostMapping("/api/v1/posts")
    public Long save(@Valid @RequestBody PostsSaveRequestDto requestDto, @LoginUser SessionUser user) {
        return postsService.save(requestDto, user);
    }

    @PutMapping("/api/v1/posts/{id}")
    public Long update(@PathVariable Long id, @Valid @RequestBody PostsUpdateRequestDto requestDto, @LoginUser SessionUser user) {
        return postsService.update(id, requestDto, user);
    }

    @DeleteMapping("/api/v1/posts/{id}")
    public Long delete(@PathVariable Long id, @LoginUser SessionUser user) {
        postsService.delete(id, user);
        return id;
    }

    @GetMapping("/api/v1/posts/{id}")
    public PostsResponseDto findById(@PathVariable Long id) {
        return postsService.findById(id);
    }

    @GetMapping("/api/v1/posts/list")
    public PageResponse<PostsListResponseDto> findAll(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(postsService.findAllDesc(pageable));
    }
}
