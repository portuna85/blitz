package com.blitz.controller;

import com.blitz.domain.Posts;
import com.blitz.service.PostsService;
import com.blitz.service.dto.CommentDto;
import com.blitz.service.dto.PostsDto;
import com.blitz.service.dto.UserDto;
import com.blitz.service.security.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
public class PostsIndexController {

    private final PostsService postsService;

    @GetMapping("/")                 /* default page = 0, size = 10  */
    public String index(Model model, @PageableDefault(sort = "id", direction = Sort.Direction.DESC)
    Pageable pageable, @LoginUser UserDto.Response user) {
        Page<Posts> list = postsService.pageList(pageable);

        if (user != null) {
            model.addAttribute("user", user);
        }

        model.addAttribute("posts", list);
        model.addAttribute("previous", pageable.previousOrFirst().getPageNumber());
        model.addAttribute("next", pageable.next().getPageNumber());
        model.addAttribute("hasNext", list.hasNext());
        model.addAttribute("hasPrev", list.hasPrevious());

        return "index";
    }

    @GetMapping("/posts/write")
    public String write(@LoginUser UserDto.Response user, Model model) {
        if (user != null) {
            model.addAttribute("user", user);
        }
        return "posts/posts-write";
    }

    @GetMapping("/posts/read/{id}")
    public String read(@PathVariable Long id, @LoginUser UserDto.Response user, Model model) {
        PostsDto.Response dto = postsService.findById(id);
        List<CommentDto.Response> comments = dto.getComments();

        if (comments != null && !comments.isEmpty()) {
            model.addAttribute("comments", comments);
        }

        if (user != null) {
            model.addAttribute("user", user);

            if (dto.getUserId().equals(user.getId())) {
                model.addAttribute("writer", true);
            }

            if (comments.stream().anyMatch(s -> s.getUserId().equals(user.getId()))) {
                model.addAttribute("isWriter", true);
            }
        }

        postsService.updateView(id); // views ++
        model.addAttribute("posts", dto);
        return "posts/posts-read";
    }

    @GetMapping("/posts/update/{id}")
    public String update(@PathVariable Long id, @LoginUser UserDto.Response user, Model model) {
        PostsDto.Response dto = postsService.findById(id);
        if (user != null) {
            model.addAttribute("user", user);
        }
        model.addAttribute("posts", dto);

        return "posts/posts-update";
    }

    @GetMapping("/posts/search")
    public String search(String keyword, Model model, @PageableDefault(sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable, @LoginUser UserDto.Response user) {
        Page<Posts> searchList = postsService.search(keyword, pageable);

        if (user != null) {
            model.addAttribute("user", user);
        }
        model.addAttribute("searchList", searchList);
        model.addAttribute("keyword", keyword);
        model.addAttribute("previous", pageable.previousOrFirst().getPageNumber());
        model.addAttribute("next", pageable.next().getPageNumber());
        model.addAttribute("hasNext", searchList.hasNext());
        model.addAttribute("hasPrev", searchList.hasPrevious());

        return "posts/posts-search";
    }
}

