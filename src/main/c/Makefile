pair_reduction.so: pair_reduction.c
	gcc -O3 -fPIC -std=c17 -Wall -Wextra -march=native -fopenmp -shared -lm -o pair_reduction.so pair_reduction.c

clean:
	rm -f pair_reduction.so
