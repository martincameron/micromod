
CC=gcc
CFLAGS=-ansi -pedantic -Wall -g

all: xm2wav

clean:
	rm -f xm2wav

xm2wav: ibxm.c
	$(CC) $(CFLAGS) ibxm.c -o xm2wav
