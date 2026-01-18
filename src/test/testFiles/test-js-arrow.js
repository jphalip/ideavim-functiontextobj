// A factorial arrow function
const factorial = n => {
  if (!Number.isInteger(n)) {
    throw new TypeError("Input must be an integer");
  }
  if (n < 0) {
    throw new Error("Input must be non-negative");
  }
  let result = 1;
  for (let i = 1; i <= n; i++) {
    result *= i;
  }
  return result;
};