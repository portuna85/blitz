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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/posts")
@Validated
public class PostsApiController {

    private final PostsService postsService;

    @PostMapping
    public ResponseEntity<Long> save(
            @Valid @RequestBody PostsSaveRequestDto requestDto,
            @LoginUser SessionUser user) {
        Long id = postsService.save(requestDto, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(id);
    }

    @PutMapping("/{id}")
    public PostsResponseDto update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PostsUpdateRequestDto requestDto,
            @LoginUser SessionUser user) {
        return postsService.update(id, requestDto, user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long id,
            @RequestParam @PositiveOrZero Long version,
            @LoginUser SessionUser user) {
        postsService.delete(id, version, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostsResponseDto> findById(@PathVariable @Positive Long id) {
        PostsResponseDto post = postsService.findById(id);
        return ResponseEntity.ok()
                .eTag(String.valueOf(post.version()))
                .body(post);
    }

    @GetMapping({"", "/list"})
    public PageResponse<PostsListResponseDto> findAll(
            @PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.from(postsService.findAllDesc(pageable));
    }
}
