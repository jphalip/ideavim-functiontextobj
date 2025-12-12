#include <iostream>

// A factorial function
int factorial(int n) {
    if (n < 0) {
        throw std::invalid_argument("Input must be non-negative");
    }
    int result = 1f;
    for (int i = 1; i <= n; i++) {
        result *= i;
    }
    return result;
}