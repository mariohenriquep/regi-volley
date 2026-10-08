package com.regivolley.testsupport;

import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * {@code @Validated} on the class switches method validation to the AOP proxy, which raises
 * {@code jakarta.validation.ConstraintViolationException} (the other way a constraint on a parameter can fail).
 * Like {@link FailingTestController}, outside the application's component scan.
 */
@Controller
@Validated
@RequestMapping("/api/v1/test-validated")
@ResponseBody
public class ValidatedTestController {

    @GetMapping("/named/{name}")
    public String named(@PathVariable @Size(max = 3) String name) {
        return name;
    }
}
