package intellij_plugin_test_project

// A factorial function
func factorial(n int) int {
	if n < 0 {
		panic("Input must be non-negative")
	}
	result := 1
	for i := 1; i <= n; i++ {
		result *= i
	}
	return result
}
