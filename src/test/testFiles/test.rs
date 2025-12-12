// This is a Rust file

// A factorial function
fn factorial(n: i32) -> Result<i32, &'static str> {
    if n < 0 {
        return Err("Input must be non-negative");
    }
    let mut result = 1;
    for i in 1..=n {
        result *= i;
    }
    Ok(result)
}