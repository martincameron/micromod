
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
	/* Get audio from replay. */
	count = len * OVERSAMPLE / 4;
	if( samples_remaining < count ) {
		memset( stream, 0, len );
		count = samples_remaining;
	}
	memset( mix_buffer, 0, count * NUM_CHANNELS * sizeof( short ) );
	micromod_get_audio( mix_buffer, count );
	downsample( mix_buffer, ( short * ) stream, count );
	reverb( ( short * ) stream, count / OVERSAMPLE );
	samples_remaining -= count;
	/* Notify the main thread if song has finished. */	
	if( samples_remaining <= 0 ) SDL_SemPost( semaphore );
}

static void load_module( char *file_name ) {
	FILE *file;
	void *module;
	long length, read, error;

	file = fopen( file_name, "rb" );
	if( file == NULL ) {
		fprintf( stderr, "Unable to open file.\n" );
		exit( EXIT_FAILURE );
	}

	module = malloc( 1084 );
	if( module == NULL ) {
		fprintf( stderr, "Unable to allocate memory for module header.\n");
		exit( EXIT_FAILURE );
	}
	read = fread( module, 1, 1084, file );
	if( read != 1084 ) {
		fprintf( stderr, "Unable to read module header.\n");
		exit( EXIT_FAILURE );
	}
	length = micromod_calculate_mod_file_len( module );
	if( length < 0 ) {
		fprintf( stderr, "Module file type not recognised.\n");
		exit( EXIT_FAILURE );
	}
	printf( "Module Data Length: %i bytes.\n", ( int ) length );
	free( module );

	module = malloc( length );
	if( module == NULL ) {
		fprintf( stderr, "Unable to allocate memory for module data.\n");
		exit( EXIT_FAILURE );
	}
	error = fseek( file, 0, SEEK_SET );
	if( error != 0 ) {
		fprintf( stderr, "Unable to seek to start of file.\n");
		exit( EXIT_FAILURE );
	}
	read = fread( module, 1, length, file );
	if( read != length ) {
		fprintf( stderr, "Module file is truncated. %i bytes missing.\n", ( int ) ( length - read ) );
	}
	error = fclose( file );
	if( error != 0 ) {
		fprintf( stderr, "Unable to close file.\n");
		exit( EXIT_FAILURE );
	}
	
	error = micromod_initialise( ( signed char * ) module, SAMPLING_FREQ * OVERSAMPLE );
	if( error != 0 ) {
		fprintf( stderr, "Unable to initialise replay.\n");
		exit( EXIT_FAILURE );
	}
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

int main( int argc, char **argv ) {
	char *filename;
	SDL_AudioSpec audiospec;

	if( argc == 2 ) {
		filename = argv[ 1 ];
	} else if( argc == 3 && strcmp( argv[ 1 ], "-reverb" ) == 0 ) {
		reverb_len = REVERB_BUF_LEN;
		filename = argv[ 2 ];
	} else {
		fprintf( stderr, "Usage: %s [-reverb] filename\n", argv[ 0 ] );
		return EXIT_FAILURE;
	}

	/* Read module. */
	load_module( filename );
	print_module_info();
	
	/* Calculate song length. */
	samples_remaining = micromod_calculate_song_duration();
	printf( "Song Duration: %i seconds.\n", ( int ) ( samples_remaining / ( SAMPLING_FREQ * OVERSAMPLE ) ) );
	fflush( NULL );

	/* Initialise SDL_AudioSpec Structure. */
	memset( &audiospec, 0, sizeof( SDL_AudioSpec ) );
	audiospec.freq = SAMPLING_FREQ;
	audiospec.format = AUDIO_S16SYS;
	audiospec.channels = NUM_CHANNELS;
	audiospec.samples = BUFFER_SAMPLES;
	audiospec.callback = audio_callback;
	audiospec.userdata = NULL;
	
	/* Open the audio device. */
	if( SDL_OpenAudio( &audiospec, NULL ) != 0 ) {
		fprintf( stderr, "Couldn't open audio device: %s\n", SDL_GetError() );
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

	/* Close audio device. */
	SDL_CloseAudio();

	return EXIT_SUCCESS;
}
