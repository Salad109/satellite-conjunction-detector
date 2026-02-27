package io.salad109.conjunctiondetector.ui.internal;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
class UiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public String handleNotFound(ResponseStatusException ex, Model model) {
        model.addAttribute("status", ex.getStatusCode().value());
        model.addAttribute("reason", ex.getReason());
        return "error";
    }
}
