# A factorial function
sub factorial {
    my $n = shift;
    die "Input must be an integer"
        unless $n =~ /^-?\d+$/;
    die "Input must be non-negative"
        if $n < 0;
    my $result = 1;
    for my $i (1..$n) {
        $result *= $i;
    }
    return $result;
}