# A factorial function
factorial <- function(n) {
    if (!is.numeric(n) || n %% 1 != 0) {
        stop("Input must be an integer")
    }
    if (n < 0) {
        stop("Input must be non-negative")
    }
    result <- 1
    for (i in 1:n) {
        result <- result * i
    }
    return(result)
}