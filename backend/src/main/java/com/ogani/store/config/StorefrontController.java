package com.ogani.store.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StorefrontController {
    @GetMapping({"/shop","/shop/"})
    public String entry() { return "redirect:/shop/index.html"; }
}
