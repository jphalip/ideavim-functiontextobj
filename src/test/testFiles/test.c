#include <stdio.h>

// A factorial function
int factorial(int n) {
    if (n < 0) {
        fprintf(stderr, "Error: Input must be non-negative\n");
        return -1;
    }
    int result = 1;
    for (int i = 1; i <= n; i++) {
        result *= i;
    }
    return result;
}