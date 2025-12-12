// A factorial function
int factorial(int n) {
  if (n < 0) {
    throw ArgumentError('Input must be non-negative');
  }
  var result = 1;
  for (var i = 1; i <= n; i++) {
    result *= i;
  }
  return result;
}