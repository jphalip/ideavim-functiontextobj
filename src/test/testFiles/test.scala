// This is a Scala file

// A factorial function
def factorial(n: Int): Int = {
  require(n >= 0, "Input must be non-negative")
  var result = 1
  for (i <- 1 to n) {
    result *= i
  }
  result
}