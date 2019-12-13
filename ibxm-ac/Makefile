
CC=gcc
CFLAGS=-ansi -pedantic -Wall -g

all: xm2wav

clean:
	rm -f xm2wav

xm2wav: xm2wav.c ibxm.c ibxm.h
	$(CC) $(CFLAGS) xm2wav.c ibxm.c -o xm2wav

ibxm-sdl: sdlplayer.c ibxm.c ibxm.h
	$(CC) $(CFLAGS) ibxm.c sdlplayer.c -o ibxm-sdl `sdl2-config --cflags --libs`
