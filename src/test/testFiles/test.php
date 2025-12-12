<?php

// A factorial function
function factorial($n) {
    if (!is_int($n)) {
        throw new InvalidArgumentException("Input must be an integer");
    }
    if ($n < 0) {
        throw new InvalidArgumentException("Input must be non-negative");
    }
    $result = 1;
    for ($i = 1; $i <= $n; $i++) {
        $result *= $i;
    }
    return $result;
}