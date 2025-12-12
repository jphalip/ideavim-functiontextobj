import a.b.c.Defg;

// A factorial function
public static int factorial(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("Input must be non-negative");
    }
    int result = 1;
    for (int i = 1; i <= n; i++) {
        result *= i;
    }
    return result;
}