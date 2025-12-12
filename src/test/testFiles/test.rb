# A factorial function
def factorial(n)
  raise TypeError, "Input must be an integer" unless n.is_a? Integer
  raise ArgumentError, "Input must be non-negative" if n < 0
  result = 1
  (1..n).each do |i|
    result *= i
  end
  return result
end