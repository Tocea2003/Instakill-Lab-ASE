package com.instakill.funnyfy.api.rest;

import com.instakill.funnyfy.application.FunnyfyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/funnyfy {"text": "..."}} -> {@code {"text": "<funnier version>"}}. Requires login. */
@RestController
@RequestMapping("/api/funnyfy")
public class FunnyfyRestController {

    private final FunnyfyService funnyfyService;

    public FunnyfyRestController(FunnyfyService funnyfyService) {
        this.funnyfyService = funnyfyService;
    }

    @PostMapping
    public FunnyfyResponse funnyfy(@Valid @RequestBody FunnyfyRequest request) {
        return new FunnyfyResponse(funnyfyService.makeFunnier(request.text()));
    }
}
