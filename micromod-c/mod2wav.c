
#include "errno.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"

#include "micromod.h"

static long filt_l, filt_r;

static long read_file( char *file_name, void *buffer, int limit ) {
	long file_length = -1, bytes_read;
	FILE *input_file = fopen( file_name, "rb" );
	if( input_file != NULL ) {
		if( fseek( input_file, 0L, SEEK_END ) == 0 ) {
			file_length = ftell( input_file );
			if( file_length >= 0 && buffer ) {
				if( fseek( input_file, 0L, SEEK_SET ) == 0 ) {
					if( limit > 0 && file_length > limit ) {
						file_length = limit;
					}
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

static signed char* load_module( char *file_name ) {
	int length;
	signed char header[ 1084 ], *module = NULL;
	if( read_file( file_name, header, 1084 ) == 1084 ) {
		length = micromod_calculate_mod_file_len( header );
		if( length > 0 ) {
			printf( "Module Data Length: %d bytes.\n", length );
			module = calloc( length, sizeof( signed char ) );
			if( module ) {
				if( read_file( file_name, module, length ) < length ) {
					free( module );
					module = NULL;
				}
			}
		} else {
			fputs( "Unrecognized module type.\n", stderr );
		}
	}
	return module;
}

static int set_pattern( signed char *module_data, int pattern ) {
	int idx, pat, max = 0;
	for( idx = 0; idx < 128; idx++ ) {
		pat = module_data[ 952 + idx ] & 0x7F;
		if( pat > max ) {
			max = pat;
		}
	}
	if( pattern < 0 || pattern > max ) {
		pattern = 0;
	}
	module_data[ 952 ] = pattern;
	module_data[ 950 ] = 1;
	return pattern;
}

static int key_to_freq( int key, int c2rate ) {
	int freq = 0;
	short key_to_period[] = { 1814, /*
		 C-0   C#0   D-0   D#0   E-0   F-0   F#0   G-0   G#0   A-1  A#1  B-1 */
		1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
		 856,  808,  762,  720,  678,  640,  604,  570,  538,  508, 480, 453,
		 428,  404,  381,  360,  339,  320,  302,  285,  269,  254, 240, 226,
		 214,  202,  190,  180,  170,  160,  151,  143,  135,  127, 120, 113,
		 107,  101,   95,   90,   85,   80,   75,   71,   67,   63,  60,  56,
		  53,   50,   47,   45,   42,   40,   37,   35,   33,   31,  30,  28,
		  26
	};
	if( key > 0 && key < 73 ) {
		freq = c2rate * 428 / key_to_period[ key ];
	}
	return freq;
}

static int str_to_freq( char *str, int c2rate ) {
	int key, freq = 0;
	short str_to_key[] = { -2, 0, 1, 3, 5, 6, 8 };
	if( str[ 0 ] >= 'A' && str[ 0 ] <= 'G' && strlen( str ) == 3 ) {
		key = str_to_key[ str[ 0 ] - 'A' ];
		if( str[ 1 ] == '#' ) {
			key++;
		}
		if( "A-A#B-C-C#D-D#E-F-F#G-G#"[ key * 2 + 5 ] == str[ 1 ] ) {
			if( str[ 2 ] >= '0' && str[ 2 ] <= '9' ) {
				key += ( str[ 2 ] - '0' ) * 12;
				freq = key_to_freq( key, c2rate );
			}
		}
	} else {
		freq = atoi( str );
	}
	return freq;
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
		input = load_module( argv[ 1 ] );
		if( input ) {
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
			free( input );
		}
	}
	return result;
}
