// A factorial arrow function
const factorial = (n: number): number => {
    if (!Number.isInteger(n)) {
        throw new TypeError("Input must be an integer");
    }
    if (n < 0) {
        throw new Error("Input must be non-negative");
    }
    let result: number = 1;
    for (let i: number = 1; i <= n; i++) {
        result *= i;
    }
    return result;
};