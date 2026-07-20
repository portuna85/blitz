package com.blitz.web;

import com.blitz.config.auth.LoginUser;
import com.blitz.config.auth.dto.SessionUser;
import com.blitz.service.CommentsService;
import com.blitz.service.PostsService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Controller
@Validated
public class IndexController {

    private final PostsService postsService;
    private final CommentsService commentsService;

    @GetMapping("/")
    public String index(Model model, @LoginUser SessionUser user,
                         @PageableDefault(size = 10) Pageable pageable) {
        model.addAttribute("posts", postsService.findAllDesc(pageable));
        if (user != null) {
            model.addAttribute("userName", user.name());
        }
        return "index";
    }

    @GetMapping("/posts/save")
    public String postsSave() {
        return "posts-save";
    }

    @GetMapping("/posts/{id}")
    public String postDetail(@PathVariable @Positive Long id, Model model, @LoginUser SessionUser user,
                              @RequestParam(defaultValue = "0") @PositiveOrZero int commentPage) {
        PostsService.PostView view = postsService.findDetail(id, user);
        model.addAttribute("post", view.post());
        model.addAttribute("isOwner", view.owner());

        model.addAttribute("commentPage", commentsService.findPage(id, commentPage, user));
        model.addAttribute("currentUserId", user == null ? null : user.userId());

        return "posts-detail";
    }

    @GetMapping("/posts/{id}/edit")
    public String postsUpdate(@PathVariable @Positive Long id, Model model, @LoginUser SessionUser user) {
        model.addAttribute("post", postsService.findOwnedById(id, user));

        return "posts-update";
    }

    @GetMapping("/posts/update/{id}")
    public String legacyPostRoute(@PathVariable @Positive Long id) {
        return "redirect:/posts/" + id;
    }
}
