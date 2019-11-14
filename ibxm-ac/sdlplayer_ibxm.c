#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>

#include "SDL.h"
#include "SDL_main.h"

#include "ibxm.h"

#define SAMPLING_FREQ  48000  /* 48khz. */
#define NUM_CHANNELS   2      /* Stereo. */
#define BUFFER_SAMPLES 16320  /* 64k buffer. */
#define FALSE 0
#define TURE 1
static SDL_sem *semaphore;
static long samples_remaining;
struct replay *replay;

uint8_t loop = FALSE;

static void audio_callback( void *udata, Uint8 *stream, int len ) {
	long count;
	long sam = 0;
	size_t i;
	int idx, ampl, offset = 0;
	int mix_buf[16384 ];
	count = len / 2;
	printf("len:%i  samples_remaining:%lu count:%i\n", len,  samples_remaining, count);
	if( samples_remaining < count ) {
		/* Clear output.*/
		memset( stream, 0, len );
		count = samples_remaining;
	}
	if( count > 0 ) {
		/* Get audio from replay.*/
		for (i = 0; i < 10; i++) {
			sam = replay_get_audio(replay, mix_buf) * 2;
			for( idx = 0; idx < sam; idx++ ) {
				ampl = mix_buf[ idx ]>>1;
				if( ampl > 32767 ) {
					ampl = 32767;
				}
				if( ampl < -32768 ) {
					ampl = -32768;
				}
				stream[ offset++ ] = ampl & 0xFF;
				stream[ offset++ ] = ( ampl >> 8 ) & 0xFF;
			}
		}
		if(!loop) samples_remaining -= count;
	} else {
		/* Notify the main thread to stop playback.*/
		SDL_SemPost( semaphore );
	}
}

static void termination_handler( int signum ) {
	/* Notify the main thread to stop playback. */
	SDL_SemPost( semaphore );
	fprintf( stderr, "\nTerminated!\n" );
}

static long setup_sdl() {
	long result;
	SDL_AudioSpec audiospec;

	int tick_len = replay_calculate_tick_len(replay);

	/* Initialise SDL_AudioSpec Structure. */
	memset( &audiospec, 0, sizeof( SDL_AudioSpec ) );
	audiospec.freq = SAMPLING_FREQ;
	audiospec.format = AUDIO_S16SYS;
	audiospec.channels = NUM_CHANNELS;
	audiospec.samples = tick_len*20;
	audiospec.callback = audio_callback;
	audiospec.userdata = NULL;

	signal( SIGTERM, termination_handler );
	signal( SIGINT,  termination_handler );
	/* Initialise audio subsystem. */
	result = SDL_Init( SDL_INIT_AUDIO );
	if( result == 0 ) {
		/* Open the audio device. */
		result = SDL_OpenAudio( &audiospec, NULL );
		if( result == 0 ) {
			/* Begin playback. */
			SDL_PauseAudio( 0 );
			/* Wait for playback to finish. */
			semaphore = SDL_CreateSemaphore( 0 );
			printf("Running\n");
			replay_seek( replay, 0 );
			result = SDL_SemWait( semaphore );
			if( result != 0 ) {
				fprintf( stderr, "SDL_SemWait() failed.\n" );
			}
			printf("Stopping\n");
			/* Close audio device and shut down SDL. */
			SDL_CloseAudio();
			SDL_Quit();
		} else {
			fprintf( stderr, "Unable to open audio device: %s\n", SDL_GetError() );
		}
	} else {
		fprintf( stderr, "Unable to initialise SDL: %s\n", SDL_GetError() );
	}
	return result;
}

static long read_file( char *file_name, void *buffer ) {
	long file_length = -1, bytes_read;
	FILE *input_file = fopen( file_name, "rb" );
	if( input_file != NULL ) {
		if( fseek( input_file, 0L, SEEK_END ) == 0 ) {
			file_length = ftell( input_file );
			if( file_length >= 0 && buffer ) {
				if( fseek( input_file, 0L, SEEK_SET ) == 0 ) {
					bytes_read = fread( buffer, 1, file_length, input_file );
					if( bytes_read != file_length ) {
						file_length = -1;
					}
				} else {
					file_length = -1;
				}
			}
		}
		fclose( input_file );
	}
	if( file_length < 0 ) {
		fputs( strerror( errno ), stderr );
		fputs( "\n", stderr );
	}
	return file_length;
}

static int xm_to_play( struct module *module) {
	int duration, length = 0;
	replay = new_replay( module, SAMPLING_FREQ, 0 );
	if( replay ) {
		duration = replay_calculate_duration( replay );
		samples_remaining = duration * 2 + 44;
		printf("duration:%i\n",duration / 1000 / 60);
		/* Install signal handlers.*/

		setup_sdl();

		dispose_replay( replay );
	}
	return length;
}

int main( int argc, char **argv ) {
	int arg, result;
	int length;
	char *input;
	char message[ 64 ] = "";
	char *filename = argv[ 1 ];
	struct data data;
	struct module *module;

	for( arg = 1; arg < argc; arg++ ) {
		/* Parse arguments.*/
		if( strcmp( argv[ arg ], "-loop" ) == 0 ) {
			loop = TURE;
		} else {
			filename = argv[ arg ];
		}
	}

	result = EXIT_FAILURE;
	if( filename == NULL ) {
		fprintf( stderr, "Usage: %s [-reverb] filename\n", filename );
	} else {
		/* Read module file.*/
		length = read_file( filename, NULL );
		if( length > 0 ) {
			printf( "Module Data Length: %i bytes.\n", length );
			input = calloc( length, 1 );
			if( input != NULL ) {
				if( read_file( argv[ 1 ], input ) >= 0 ) {
					data.buffer = input;
					data.length = length;
					module = module_load( &data, message );
					if( module ) {
						/* Perform conversion. */
						xm_to_play(module);

						dispose_module( module );
					} else {
						fputs( message, stderr );
						fputs( "\n", stderr );
					}
				}
				printf("Stopping in main loop.\n");
				free( input );
			}
		}
	}
	return result;
}
