
CC=gcc
CFLAGS=-ansi -pedantic -Wall

all: micromod-sdl

clean:
	rm -f micromod-sdl micromod-w32

micromod-sdl: sdlplayer.c micromod.c micromod.h
	$(CC) $(CFLAGS) micromod.c sdlplayer.c -o micromod-sdl `sdl-config --cflags --libs`

micromod-w32: w32player.c micromod.c micromod.h
	$(CC) $(CFLAGS) micromod.c w32player.c -o micromod-w32 -lwinmm
