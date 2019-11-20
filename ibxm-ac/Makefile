
CC=gcc
CFLAGS=-ansi -pedantic -Wall -g

all: xm2wav sdlplayer

clean:
	rm -f xm2wav

xm2wav: xm2wav.c ibxm.c ibxm.h
	$(CC) $(CFLAGS) xm2wav.c ibxm.c -o xm2wav

sdlplayer: sdlplayer.c ibxm.c ibxm.h
	$(CC) $(CFLAGS) sdlplayer.c ibxm.c -o sdlplayer `sdl-config --cflags --libs`
