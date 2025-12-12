# A factorial function
def factorial(n):
    if not isinstance(n, int):
        raise TypeError("Input must be an integer")
    if n < 0:
        raise ValueError("Input must be non-negative")
    result = 1
    for i in range(1, n + 1):
        result *= i
    return result