package com.regivolley.api.application.usecase;

/**
 * A single application operation: takes one input, produces one output.
 * Each use case is a class with one reason to change (Single Responsibility),
 * and depends only on domain ports (Dependency Inversion).
 */
public interface UseCase<IN, OUT> {

    OUT execute(IN input);
}
