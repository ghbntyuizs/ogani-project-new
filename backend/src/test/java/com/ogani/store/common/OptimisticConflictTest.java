package com.ogani.store.common;

import com.ogani.store.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


class OptimisticConflictTest {
    @RestController
    static class ConflictingController {
        @GetMapping("/conflict")
        void update() { throw new ObjectOptimisticLockingFailureException("Product", 1L); }
    }
    @Test void concurrentUpdateReturnsConflict() throws Exception {
        MockMvcBuilders.standaloneSetup(new ConflictingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(MockMvcRequestBuilders.get("/conflict"))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }
}
