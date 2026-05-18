package com.sheltersim.model;

import java.util.List;

public record SolverResult(
    List<Configuration> configs,
    long evaluated,
    long valid
) {}
