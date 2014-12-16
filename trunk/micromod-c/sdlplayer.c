
#include <stdio.h>
#include <stdlib.h>

#include "SDL/SDL.h"
#include "SDL/SDL_main.h"

#include "micromod.h"

/*
	Simple command line test player for micromod using SDL.
*/

#define SAMPLING_FREQ  48000  /* 48khz. */
#define REVERB_BUF_LEN 4800   /* 50ms. */
#define OVERSAMPLE     2      /* 2x oversampling. */
#define NUM_CHANNELS   2      /* Stereo. */
#define BUFFER_SAMPLES 16384  /* 64k buffer. */

static SDL_sem *semaphore;
static signed char *module;
static long samples_remaining;
static short reverb_buffer[ REVERB_BUF_LEN ];
static short mix_buffer[ BUFFER_SAMPLES * NUM_CHANNELS * OVERSAMPLE ];
static long reverb_len, reverb_idx, filt_l, filt_r;

/*
	2:1 downsampling with simple but effective anti-aliasing.
	Count is the number of stereo samples to process, and must be even.
	input may point to the same buffer as output.
*/
static void downsample( short *input, short *output, long count ) {
	long in_idx, out_idx, out_l, out_r;
	in_idx = out_idx = 0;
	while( out_idx < count ) {	
		out_l = filt_l + ( input[ in_idx++ ] >> 1 );
		out_r = filt_r + ( input[ in_idx++ ] >> 1 );
		filt_l = input[ in_idx++ ] >> 2;
		filt_r = input[ in_idx++ ] >> 2;
		output[ out_idx++ ] = out_l + filt_l;
		output[ out_idx++ ] = out_r + filt_r;
	}
}

/* Simple stereo cross delay with feedback. */
static void reverb( short *buffer, long count ) {
	long buffer_idx, buffer_end;
	if( reverb_len > 2 ) {
		buffer_idx = 0;
		buffer_end = buffer_idx + ( count << 1 );
		while( buffer_idx < buffer_end ) {
			buffer[ buffer_idx ] = ( buffer[ buffer_idx ] * 3 + reverb_buffer[ reverb_idx + 1 ] ) >> 2;
			buffer[ buffer_idx + 1 ] = ( buffer[ buffer_idx + 1 ] * 3 + reverb_buffer[ reverb_idx ] ) >> 2;
			reverb_buffer[ reverb_idx ] = buffer[ buffer_idx ];
			reverb_buffer[ reverb_idx + 1 ] = buffer[ buffer_idx + 1 ];
			reverb_idx += 2;
			if( reverb_idx >= reverb_len ) {
				reverb_idx = 0;
			}
			buffer_idx += 2;
		}
	}
}

static void audio_callback( void *udata, Uint8 *stream, int len ) {
	long count;
	count = len * OVERSAMPLE / 4;
	if( samples_remaining < count ) {
		/* Clear output. */
		memset( stream, 0, len );
		count = samples_remaining;
	}
	if( count > 0 ) {
		/* Get audio from replay. */
		memset( mix_buffer, 0, count * NUM_CHANNELS * sizeof( short ) );
		micromod_get_audio( mix_buffer, count );
		downsample( mix_buffer, ( short * ) stream, count );
		reverb( ( short * ) stream, count / OVERSAMPLE );
		samples_remaining -= count;
	} else {
		/* Notify the main thread if song has finished. */	
		SDL_SemPost( semaphore );
	}
}

static long read_file( char *file_name, void *buffer, long length ) {
	FILE *file;
	long error, count;
	file = fopen( file_name, "rb" );
	if( file == NULL ) {
		return -1;
	}
	count = fread( buffer, 1, length, file );
	if( count < length && !feof( file ) ) {
		return -1;
	}
	error = fclose( file );
	if( error != 0 ) {
		return -1;
	}
	return count;
}

static void print_module_info() {
	int inst;
	char string[ 23 ];
	for( inst = 0; inst < 16; inst++ ) {
		micromod_get_string( inst, string );
		printf( "%02i - %-22s ", inst, string );
		micromod_get_string( inst + 16, string );
		printf( "%02i - %-22s\n", inst + 16, string );
	}
}

static void free_module() {
	free( module );
}

int main( int argc, char **argv ) {
	long error, count, length;
	char *filename;
	signed char header[ 1084 ];
	SDL_AudioSpec audiospec;
	/* Parse arguments.*/
	if( argc == 2 ) {
		filename = argv[ 1 ];
	} else if( argc == 3 && strcmp( argv[ 1 ], "-reverb" ) == 0 ) {
		reverb_len = REVERB_BUF_LEN;
		filename = argv[ 2 ];
	} else {
		fprintf( stderr, "Usage: %s [-reverb] filename\n", argv[ 0 ] );
		return EXIT_FAILURE;
	}
	/* Calculate module file length.*/
	count = read_file( filename, header, 1084 );
	if( count != 1084 ) {
		fprintf( stderr, "Unable to read module header.\n");
		exit( EXIT_FAILURE );
	}
	length = micromod_calculate_mod_file_len( header );
	if( length < 0 ) {
		fprintf( stderr, "Module file type not recognised.\n");
		exit( EXIT_FAILURE );
	}
	printf( "Module Data Length: %li bytes.\n", length );
	/* Allocate memory for module.*/
	module = calloc( length, 1 );
	if( module == NULL ) {
		fprintf( stderr, "Unable to allocate memory for module data.\n");
		exit( EXIT_FAILURE );
	}
	atexit( free_module );
	/* Read module data.*/
	count = read_file( filename, module, length );
	if( count < 0 ) {
		fprintf( stderr, "Unable to read module file.\n");
		exit( EXIT_FAILURE );
	} else if( count < length ) {
		fprintf( stderr, "Module file is truncated. %li bytes missing.\n", length - count );
	}
	/* Initialise replay.*/
	error = micromod_initialise( module, SAMPLING_FREQ * OVERSAMPLE );
	if( error != 0 ) {
		fprintf( stderr, "Unable to initialise replay.\n");
		exit( EXIT_FAILURE );
	}
	print_module_info();
	/* Calculate song length. */
	samples_remaining = micromod_calculate_song_duration();
	printf( "Song Duration: %li seconds.\n", samples_remaining / ( SAMPLING_FREQ * OVERSAMPLE ) );
	fflush( NULL );
	/* Initialise SDL_AudioSpec Structure. */
	memset( &audiospec, 0, sizeof( SDL_AudioSpec ) );
	audiospec.freq = SAMPLING_FREQ;
	audiospec.format = AUDIO_S16SYS;
	audiospec.channels = NUM_CHANNELS;
	audiospec.samples = BUFFER_SAMPLES;
	audiospec.callback = audio_callback;
	audiospec.userdata = NULL;
	/* Initialise audio subsystem. */
	if( SDL_Init( SDL_INIT_AUDIO ) != 0 ) {
		fprintf( stderr, "Unable to initialise SDL: %s\n", SDL_GetError() );
		return EXIT_FAILURE;
	}
	atexit( SDL_Quit );
	/* Open the audio device. */
	if( SDL_OpenAudio( &audiospec, NULL ) != 0 ) {
		fprintf( stderr, "Unable to open audio device: %s\n", SDL_GetError() );
		return EXIT_FAILURE;
	}
	/* Begin playback. */
	SDL_PauseAudio( 0 );
	/* Wait for playback to finish. */
	semaphore = SDL_CreateSemaphore( 0 );
	if( SDL_SemWait( semaphore ) != 0 ) {
		fprintf( stderr, "SDL_SemWait() failed.\n" );
		return EXIT_FAILURE;
	}
	/* Close audio device and exit. */
	SDL_CloseAudio();
	return EXIT_SUCCESS;
}
