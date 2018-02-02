
#include "errno.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"
#include "time.h"

#include "micromod.h"

/*
	Protracker MOD to WAV/IFF-8SVX converter. (c)2018 mumart@gmail.com
*/

enum {
	RAW, WAV, IFF
};

static const short str_to_key[] = { -2, 0, 1, 3, 5, 6, 8 };
static const short key_to_period[] = { 1814, /*
	 C-0   C#0   D-0   D#0   E-0   F-0   F#0   G-0   G#0   A-1  A#1  B-1 */
	1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
	 856,  808,  762,  720,  678,  640,  604,  570,  538,  508, 480, 453,
	 428,  404,  381,  360,  339,  320,  302,  285,  269,  254, 240, 226,
	 214,  202,  190,  180,  170,  160,  151,  143,  135,  127, 120, 113,
	 107,  101,   95,   90,   85,   80,   75,   71,   67,   63,  60,  56,
	  53,   50,   47,   45,   42,   40,   37,   35,   33,   31,  30,  28,
	  26
};

static short mix_buf[ 8192 ];
static int filt_l, filt_r, qerror;

static int read_file( char *file_name, void *buffer, int limit ) {
	int file_length = -1, bytes_read;
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

static int write_file( char *filename, char *buffer, int length ) {
	int count = -1;
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

static void write_shortbe( int value, char *dest ) {
	dest[ 0 ] = ( value >> 8 ) & 0xFF;
	dest[ 1 ] = value & 0xFF;
}

static void write_int32be( int value, char *dest ) {
	dest[ 0 ] = ( value >> 24 ) & 0xFF;
	dest[ 1 ] = ( value >> 16 ) & 0xFF;
	dest[ 2 ] = ( value >> 8 ) & 0xFF;
	dest[ 3 ] = value & 0xFF;
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
static void downsample( short *input, short *output, int count ) {
	short in_idx = 0, out_idx = 0, out_l, out_r;
	while( out_idx < count ) {
		out_l = filt_l + ( input[ in_idx++ ] >> 1 );
		out_r = filt_r + ( input[ in_idx++ ] >> 1 );
		filt_l = input[ in_idx++ ] >> 2;
		filt_r = input[ in_idx++ ] >> 2;
		output[ out_idx++ ] = out_l + filt_l;
		output[ out_idx++ ] = out_r + filt_r;
	}
}

/* Convert count stereo samples in input to 8-bit mono. */
static void quantize( short *input, char *output, int gain, int count ) {
	int in_idx = 0, out_idx = 0, ampl;
	while( out_idx < count ) {
		/* Convert stereo to mono and apply gain. */
		ampl = input[ in_idx++ ];
		ampl = ( ampl + input[ in_idx++ ] ) * gain >> 7;
		/* Dithering. */
		ampl -= qerror;
		qerror = ampl;
		/* Rounding. */
		ampl += ampl & 0x80;
		ampl = ampl / 256;
		qerror = ( ampl << 8 ) - qerror;
		/* Clipping. */
		if( ampl < -128 ) ampl = -128;
		if( ampl > 127 ) ampl = 127;
		output[ out_idx++ ] = ampl;
	}
}

static int mod_to_wav( signed char *module_data, char *wav, int sample_rate ) {
	int idx, duration, count, ampl, offset, end, length = 0;
	if( micromod_initialise( module_data, sample_rate * 2 ) == 0 ) {
		duration = micromod_calculate_song_duration() >> 1;
		length = duration * 4 + 44;
		if( wav ) {
			strcpy( wav, "RIFF" );
			write_int32le( duration * 4 + 36, &wav[ 4 ] );
			strcpy( &wav[ 8 ], "WAVEfmt " );
			write_int32le( 16, &wav[ 16 ] );
			write_int32le( 0x00020001, &wav[ 20 ] );
			write_int32le( sample_rate, &wav[ 24 ] );
			write_int32le( sample_rate * 4, &wav[ 28 ] );
			write_int32le( 0x00100004, &wav[ 32 ] );
			strcpy( &wav[ 36 ], "data" );
			write_int32le( duration * 4, &wav[ 40 ] );
			offset = 44;
			while( offset < length ) {
				count = 8192;
				if( count > length - offset ) {
					count = length - offset;
				}
				memset( mix_buf, 0, count * sizeof( short ) );
				micromod_get_audio( mix_buf, count >> 1 );
				downsample( mix_buf, mix_buf, count >> 1 );
				idx = 0;
				end = offset + count;
				while( offset < end ) {
					ampl = mix_buf[ idx++ ];
					wav[ offset++ ] = ampl & 0xFF;
					wav[ offset++ ] = ( ampl >> 8 ) & 0xFF;
				}
				printf( "\rProgress: %d%%", offset * 100 / length );
				fflush( stdout );
			}
		}
	} else {
		fputs( "Unsupported module or invalid sampling rate.\n", stderr );
	}
	return length;
}

static long mod_to_sam( signed char *module_data, char *sam, int gain, int sample_rate, int iff ) {
	int duration, count, offset = 0, length = 0;
	if( micromod_initialise( module_data, sample_rate * 2 ) == 0 ) {
		length = duration = micromod_calculate_song_duration() >> 1;
		if( iff ) {
			length = duration + 48;
		}
		if( sam ) {
			if( iff ) {
				strcpy( sam, "FORM" );
				write_int32be( duration + 40, &sam[ 4 ] );
				strcpy( &sam[ 8 ], "8SVXVHDR" );
				write_int32be( 20, &sam[ 16 ] );
				write_int32be( duration, &sam[ 20 ] );
				write_int32be( 0, &sam[ 24 ] );
				write_int32be( 32, &sam[ 28 ] );
				write_shortbe( sample_rate, &sam[ 32 ] );
				sam[ 34 ] = 1;
				sam[ 35 ] = 0;
				write_int32be( 65536, &sam[ 36 ] );
				strcpy( &sam[ 40 ], "BODY" );
				write_int32be( duration, &sam[ 44 ] );
				offset = 48;
			}
			while( offset < length ) {
				count = 2048;
				if( count > length - offset ) {
					count = length - offset;
				}
				memset( mix_buf, 0, count * 4 * sizeof( short ) );
				micromod_get_audio( mix_buf, count << 1 );
				downsample( mix_buf, mix_buf, count << 1 );
				quantize( mix_buf, &sam[ offset ], gain, count );
				offset += count;
				printf( "\rProgress: %d%%", offset * 100 / length );
				fflush( stdout );
			}
		}
	} else {
		fputs( "Unsupported module or invalid sampling rate.\n", stderr );
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
			} else {
				fputs( "Not enough memory to load module.\n", stderr );
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
	if( key > 0 && key < 73 ) {
		freq = c2rate * 428 / key_to_period[ key ];
	}
	return freq;
}

static int str_to_freq( char *str, int c2rate ) {
	int key, freq = 0;
	int chr = str[ 0 ] >= 'a' ? str[ 0 ] - 32 : str[ 0 ];
	if( chr >= 'A' && chr <= 'G' && strlen( str ) == 3 ) {
		key = str_to_key[ chr - 'A' ];
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

void uppercase( char *str ) {
	int chr = str[ 0 ], idx = 0;
	while( chr ) {
		if( chr >= 'a' && chr <= 'z' ) {
			str[ idx ] = chr - 32;
		}
		chr = str[ ++idx ];
	}
}

int filetype( char *name ) {
	char ext[ 5 ];
	int type = WAV, len = strlen( name );
	if( len > 3 ) {
		strcpy( ext, &name[ len - 4 ] );
		uppercase( ext );
		if( strcmp( ext, ".IFF" ) == 0 ) type = IFF;
		if( strcmp( ext, ".RAW" ) == 0 ) type = RAW;
	}
	return type;
}

int main( int argc, char **argv ) {
	int result = EXIT_FAILURE, idx = 1;
	int length, type, patt = -1, rate = -1, gain = 64;
	char *arg, *in_file = NULL, *out_file = NULL, *output;
	signed char *module;
	time_t seconds = time( NULL );
	puts( micromod_get_version() );
	while( idx < argc ) {
		arg = argv[ idx++ ];
		if( idx < argc && strcmp( "-pat", arg ) == 0 ) {
			patt = atoi( argv[ idx++ ] );
		} else if( idx < argc && strcmp( "-rate", arg ) == 0 ) {
			rate = str_to_freq( argv[ idx++ ], 8287 );
		} else if( idx < argc && strcmp( "-gain", arg ) == 0 ) {
			gain = atoi( argv[ idx++ ] );
		} else if( !in_file ) {
			in_file = arg;
		} else if( !out_file ) {
			out_file = arg;
		}
	}
	if( out_file ) {
		printf( "Converting \"%s\" to \"%s\".\n", in_file, out_file );
		/* Get output file type. */
		type = filetype( out_file );
		/* Read module file.*/
		module = load_module( in_file );
		if( module ) {
			/* Configure parameters.*/
			if( rate < 0 ) {
				rate = ( type == WAV ) ? 48000 : str_to_freq( "A-4", 8287 );
			}
			if( gain < 1 ) {
				gain = 128;
			}
			if( patt >= 0 ) {
				printf( "Converting pattern %d, sample rate %d, gain %d.\n", patt, rate, gain );
				set_pattern( module, patt );
			} else {
				printf( "Converting whole song, sample rate %d, gain %d.\n", rate, gain );
			}
			/* Calculate length. */
			if( type == WAV ) {
				puts( "Generating 16-bit stereo RIFF-WAV file." );
				length = mod_to_wav( module, NULL, rate );
			} else {
				printf( "Generating 8-bit mono %s sample.\n", type == IFF ? "IFF-8SVX" : "RAW" );
				length = mod_to_sam( module, NULL, gain, rate, type == IFF );
			}
			if( length > 0 ) {
				printf( "Output file length: %d bytes.\n", length );
				/* Perform conversion. */
				output = calloc( length, 1 );
				if( output ) {
					if( type == WAV ) {
						mod_to_wav( module, output, rate );
					} else {
						mod_to_sam( module, output, gain, rate, type == IFF );
					}
					printf( "\rCompleted in %d seconds!\n", ( int ) ( time( NULL ) - seconds ) );
					if( write_file( out_file, output, length ) > 0 ) {
						result = EXIT_SUCCESS;
					}
					free( output );
				} else {
					fputs( "Not enough memory for output file.\n", stderr );
				}
			}
			free( module );
		}
	} else {
		fprintf( stderr, "Usage: %s input.mod output [-pat p] [-rate r] [-gain g]\n\n", argv[ 0 ] );
		fprintf( stderr, "   If output ends with \".wav\", generate 16-bit stereo RIFF-WAV file.\n" );
		fprintf( stderr, "   If output ends with \".iff\", generate 8-bit mono IFF-8SVX file.\n" );
		fprintf( stderr, "   If output ends with \".raw\", generate 8-bit mono signed raw samples.\n" );
		fprintf( stderr, "   If pattern is unspecified, convert the whole song.\n" );
		fprintf( stderr, "   Rate can be specified in HZ or as a key such as \"C-2\".\n" );
		fprintf( stderr, "   Gain works only for IFF/RAW output and defaults to 128.\n\n" );
		fprintf( stderr, "Whole song to wav: %s input.mod output.wav -rate 48000\n", argv[ 0 ] );
		fprintf( stderr, "Pattern to sample: %s input.mod output.iff -pat 0 -rate A-4 -gain 80\n", argv[ 0 ] );
	}
	return result;
}
