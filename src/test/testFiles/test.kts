import a.c.b.Defg

// A factorial function
fun factorial(n: Int): Int {
    require(n >= 0) {
        "Input must be non-negative"
    }
    var result = 1
    for (i in 1..n) {
        result *= i
    }
    return result
}