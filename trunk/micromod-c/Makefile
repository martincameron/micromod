
CC=gcc
CFLAGS=-ansi -pedantic -Wall

all: micromod-sdl

clean:
	rm -f micromod micromod.exe stdout.txt stderr.txt *~

micromod-sdl:
	$(CC) $(CFLAGS) micromod.c sdlplayer.c -o micromod `sdl-config --cflags --libs`

micromod-w32:
	$(CC) $(CFLAGS) micromod.c w32player.c -o micromod -lwinmm

