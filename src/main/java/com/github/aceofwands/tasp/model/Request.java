package com.github.aceofwands.tasp.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class Request {
    private @NotEmpty String message;
    private boolean allowWrite;
}
