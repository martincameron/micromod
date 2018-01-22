
#include "errno.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"

#include "micromod.h"

static long filt_l, filt_r;

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

static long write_file( char *filename, char *buffer, int length ) {
	long count = -1;
	FILE *file = fopen( filename, "wb" );
	if( file != NULL ) {
		count = fwrite( buffer, 1, length, file );
		fclose( file );
	}
	if( count < length ) {
		fputs( strerror( errno ), stderr );
		fputs( "\n", stderr );
		count = -1;
	}
	return count;
}

static void write_int32le( int value, char *dest ) {
	dest[ 0 ] = value & 0xFF;
	dest[ 1 ] = ( value >> 8 ) & 0xFF;
	dest[ 2 ] = ( value >> 16 ) & 0xFF;
	dest[ 3 ] = ( value >> 24 ) & 0xFF;
}

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

static long mod_to_wav( signed char *module_data, char *wav, long sample_rate ) {
	short mix_buf[ 8192 ];
	long idx, duration, count, ampl, offset, length = 0;
	if( micromod_initialise( module_data, sample_rate * 2 ) == 0 ) {
		duration = micromod_calculate_song_duration() >> 1;
		length = duration * 4 + 40;
		if( wav ) {
			printf( "Wave file length: %ld bytes.\n", length );
			strcpy( wav, "RIFF" );
			write_int32le( duration * 4 + 36, &wav[ 4 ] );
			strcpy( &wav[ 8 ], "WAVEfmt " );
			write_int32le( 16, &wav[ 16 ] );
			write_int32le( 0x00020001, &wav[ 20 ] );
			write_int32le( sample_rate, &wav[ 24 ] );
			write_int32le( sample_rate * 4, &wav[ 28 ] );
			write_int32le( 0x00100004, &wav[ 32 ] );
			strcpy( &wav[ 36 ], "data" );
			write_int32le( duration, &wav[ 40 ] );
			offset = 40;
			while( offset < length ) {
				count = 4096;
				if( count * 2 > length - offset ) {
					count = ( length - offset ) >> 1;
				}
				memset( mix_buf, 0, count * 2 * sizeof( short ) );
				micromod_get_audio( mix_buf, count );
				downsample( mix_buf, mix_buf, count );
				for( idx = 0; idx < count; idx++ ) {
					ampl = mix_buf[ idx ];
					wav[ offset++ ] = ampl & 0xFF;
					wav[ offset++ ] = ( ampl >> 8 ) & 0xFF;
				}
			}
		}
	} else {
		fputs( "Unsupported module or sampling rate.\n", stderr );
	}
	return length;
}

int main( int argc, char **argv ) {
	int result, length;
	signed char *input;
	char *output, *ext;
	result = EXIT_FAILURE;
	if( argc != 3 ) {
		fprintf( stderr, "%s\nUsage: %s input.mod output.wav\n", MICROMOD_VERSION, argv[ 0 ] );
	} else {
		/* Get output file extension. */
		ext = argv[ 2 ];
		length = strlen( argv[ 2 ] );
		if( length > 3 ) {
			ext = &ext[ length - 3 ];
		}
		/* Read module file.*/
		length = read_file( argv[ 1 ], NULL );
		if( length >= 0 ) {
			printf( "Module Data Length: %d bytes.\n", length );
			input = calloc( length, 1 );
			if( input != NULL ) {
				if( read_file( argv[ 1 ], input ) >= 0 ) {
					/* Perform conversion. */
					length = mod_to_wav( input, NULL, 48000 );
					if( length > 0 ) {
						output = calloc( length, 1 );
						if( output != NULL ) {
							mod_to_wav( input, output, 48000 );
							if( write_file( argv[ 2 ], output, length ) > 0 ) {
								result = EXIT_SUCCESS;
							}
							free( output );
						}
					}
				}
				free( input );
			}
		}
	}
	return result;
}
