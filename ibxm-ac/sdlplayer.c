
#include "errno.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"
#include "signal.h"

#include "SDL.h"
#include "SDL_main.h"

#include "ibxm.h"

/*
	Simple command-line test player for ibxm-ac using SDL.
*/

#define SAMPLING_FREQ 48000 /* 48khz. */
#define REVERB_BUF_LEN 4800 /* 50ms. */
#define BUFFER_SAMPLES 2048 /* 8k buffer. */

/* Display dimensions. */
static const int WIDTH = ( 8 * 11 + 4 ) * 8, HEIGHT = 17 * 16;

static const int PAL_ENTRIES[] = { /*
	Blue      Green     Cyan      Red       Magenta   Yellow    White     Lime */
	0x0000C0, 0x008000, 0x008080, 0x800000, 0x800080, 0x806600, 0x808080, 0x668000,
	0x0066FF, 0x00FF00, 0x00FFFF, 0xFF0000, 0xFF00FF, 0xFFCC00, 0xFFFFFF, 0xCCFF00
};

static const char *KEY_TO_STR = "A-A#B-C-C#D-D#E-F-F#G-G#";
static const char *B36_TO_STR = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

/* Colours to use for effect commands (MOD/XM upper case, S3M lower case). */
static const char FX_CLR[] = {
	/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? */
	   1,1,1,1,1,7,7,5,5,4,0,0,0,0,0,0,
	/* @ A B C D E F G H I J K L M N O */
	   0,5,6,5,6,0,6,5,5,0,0,0,4,0,0,0,
	/* P Q R S T U V W X Y Z [ \ ] ^ _ */
	   5,0,4,0,5,0,0,0,1,0,0,0,0,0,0,0,
	/* ` a b c d e f g h i j k l m n o */
	   0,6,6,6,5,1,1,1,1,5,1,7,7,0,0,4,
	/* p q r s t u v w x y z { | } ~ */
	   0,4,5,0,6,1,5,0,0,0,0,0,0,0,0
};

/* Colours to use for Exy effects. */
static const char EX_CLR[] = {
	/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
	   0,1,1,1,1,1,6,5,0,4,0,0,0,0,0,0,0,5,5,4,4,4,4
};

/* Colours to use for Sxy effects.*/
static const char SX_CLR[] = {
	/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
	   5,4,1,1,5,0,0,0,5,0,0,0,0,0,0,0,0,5,4,4,4,4,4
};

/* Colours to use for volume-column effects. */
static const char VC_CLR[] = {
	/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
	   5,5,5,5,5,0,5,5,5,5,0,0,0,0,0,0,0,1,1,5,5,5,1
};

static SDL_Window *window;
static SDL_Renderer *renderer;
static SDL_Texture *target, *charset;

static int song_end_event, display_event;
static int samples_remaining;
static short reverb_buffer[ REVERB_BUF_LEN ];
static int mix_buffer[ SAMPLING_FREQ / 3 ];
static int mix_len, mix_idx, reverb_len, reverb_idx, mute, loop;

/* Simple stereo cross delay with feedback. */
static void reverb( short *buffer, int count ) {
	int buffer_idx, buffer_end;
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

static void clip_audio( int *input, short *output, int count ) {
	int idx, end, sam;
	for( idx = 0, end = count << 1; idx < end; idx++ ) {
		sam = input[ idx ];
		if( sam < -32768 ) {
			sam = -32768;
		}
		if( sam > 32767 ) {
			sam = 32767;
		}
		output[ idx ] = sam;
	}
}

static void get_audio( struct replay *replay, short *buffer, int count ) {
	int buf_idx = 0, remain;
	while( buf_idx < count ) {
		if( mix_idx >= mix_len ) {
			mix_len = replay_get_audio( replay, mix_buffer, mute );
			mix_idx = 0;
		}
		remain = mix_len - mix_idx;
		if( ( buf_idx + remain ) > count ) {
			remain = count - buf_idx;
		}
		clip_audio( &mix_buffer[ mix_idx << 1 ], &buffer[ buf_idx << 1 ], remain );
		mix_idx += remain;
		buf_idx += remain;
	}
}

static void audio_callback( void *udata, Uint8 *stream, int len ) {
	struct replay *replay = ( struct replay * ) udata;
	SDL_Event event = { 0 };
	int count = len / 4;
	if( window ) {
		/* Update display. */
		event.type = display_event;
		event.user.code = ( replay_get_sequence_pos( replay ) << 8 ) | replay_get_row( replay );
		SDL_PushEvent( &event );
	}
	if( samples_remaining < count ) {
		/* Clear output.*/
		memset( stream, 0, len );
		count = samples_remaining;
	}
	if( count > 0 ) {
		/* Get audio from replay.*/
		get_audio( ( struct replay * ) udata, ( short * ) stream, count );
		reverb( ( short * ) stream, count );
		if( !loop ) {
			samples_remaining -= count;
		}
	} else {
		/* Notify the main thread to stop playback.*/
		event.type = song_end_event;
		SDL_PushEvent( &event );
	}
}

static void termination_handler( int signum ) {
	SDL_Event event = { 0 };
	/* Notify the main thread to stop playback. */
	event.type = song_end_event;
	SDL_PushEvent( &event );
	fprintf( stderr, "\nTerminated!\n" );
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
		fprintf( stderr, "Unable to read '%s'. %s\n", file_name, strerror( errno ) );
	}
	return file_length;
}

static void print_module_info( struct module *module ) {
	int idx = 0, end = 0;
	for( idx = 0, end = ( module->num_instruments + 1 ) / 2; idx < end; idx++ ) {
		printf( "%03i - %-32s ", idx, idx ? module->instruments[ idx ].name : module->name );
		printf( "%03i - %-32s\n", idx + end, module->instruments[ idx + end ].name );
	}
	if( idx + end <= module->num_instruments ) {
		printf( "%39s%03i - %-32s\n", "", idx + end, module->instruments[ idx + end ].name );
	}
}

static SDL_Texture* create_texture( int width, int height, Uint32 *pixels ) {
	struct SDL_Texture *texture = SDL_CreateTexture( renderer,
		SDL_PIXELFORMAT_RGBA8888, SDL_TEXTUREACCESS_STATIC, width, height );
	if( texture ) {
		SDL_SetTextureBlendMode( texture, SDL_BLENDMODE_BLEND );
		if( SDL_UpdateTexture( texture, NULL, pixels, width * sizeof( Uint32 ) ) ) {
			fprintf( stderr, "Unable to update texture: %s\n", SDL_GetError() );
		}
	} else {
		fprintf( stderr, "Unable to create texture: %s\n", SDL_GetError() );
	}
	return texture;
}

static SDL_Texture* create_charset( char *source, const int *pal_entries, int pal_size ) {
	int w, h, clr, chr, y, x;
	int stride, srcoff, pixoff;
	Uint32 *pixels;
	SDL_Texture *texture = NULL;
	stride = source[ 9 ] < 32 ? 10 : 9;
	w = 8 * 96;
	h = 16 * pal_size;
	pixels = calloc( w * h, sizeof( Uint32 ) );
	if( pixels ) {
		for( clr = 0; clr < pal_size; clr++ ) {
			srcoff = 0;
			for( chr = 1; chr < 95; chr++ ) {
				pixoff = clr * w * 16 + chr * 8;
				for( y = 0; y < 16; y++ ) {
					for( x = 0; x < 8; x++ ) {
						if( source[ srcoff + x ] > 32 ) {
							pixels[ pixoff + x ] = ( pal_entries[ clr ] << 8 ) | 0xFF;
						}
					}
					srcoff += stride;
					pixoff += w;
				}
			}
		}
		texture = create_texture( w, h, pixels );
		free( pixels );
	} else {
		fputs( "Out of memory.", stderr );
	}
	return texture;
}

static void fill_rect( int x, int y, int w, int h, int c ) {
	struct SDL_Rect rect;
	rect.x = x;
	rect.y = y;
	rect.w = w;
	rect.h = h;
	SDL_RenderSetClipRect( renderer, NULL );
	SDL_SetRenderDrawColor( renderer, ( c >> 16 ) & 0xFF, ( c >> 8 ) & 0xFF, c & 0xFF, 0xFF );
	SDL_RenderFillRect( renderer, &rect );
}

static int blit_texture( SDL_Texture *texture, int sx, int sy, int sw, int sh, int dx, int dy ) {
	struct SDL_Rect clip, dest;
	clip.x = dx;
	clip.y = dy;
	clip.w = sw;
	clip.h = sh;
	dest.x = dx - sx;
	dest.y = dy - sy;
	SDL_QueryTexture( texture, NULL, NULL, &dest.w, &dest.h );
	SDL_RenderSetClipRect( renderer, &clip );
	return SDL_RenderCopy( renderer, texture, NULL, &dest );
}

static void draw_char( char chr, int row, int col, int clr ) {
	blit_texture( charset, ( chr - 32 ) * 8, clr * 16, 8, 16, col * 8, row * 16 );
}

static void draw_text( char *text, int len, int row, int col, int clr ) {
	int idx = 0, chr = text[ 0 ];
	while( chr ) {
		draw_char( chr, row, col + idx, clr );
		chr = text[ ++idx ];
		if( len && idx >= len ) {
			chr = 0;
		}
	}
}

static void draw_int( int val, int len, int row, int col, int clr ) {
	while( len > 0 ) {
		len = len - 1;
		draw_char( 48 + val % 10, row, col + len, clr );
		val = val / 10;
	}
}

static void get_note( struct pattern *pat, int row, int chn, char *note ) {
	int offset = ( pat->num_channels * row + chn ) * 5;
	int key = pat->data[ offset ] & 0xFF;
	int instrument = pat->data[ offset + 1 ] & 0xFF;
	int volume = pat->data[ offset + 2 ] & 0xFF;
	int effect = pat->data[ offset + 3 ] & 0xFF;
	int param = pat->data[ offset + 4 ] & 0xFF;
	note[ 0 ] = ( key > 0 && key < 118 ) ? KEY_TO_STR[ ( ( key + 2 ) % 12 ) * 2 ] : 45;
	note[ 1 ] = ( key > 0 && key < 118 ) ? KEY_TO_STR[ ( ( key + 2 ) % 12 ) * 2 + 1 ] : 45;
	note[ 2 ] = ( key > 0 && key < 118 ) ? 48 + ( key + 2 ) / 12 : 45;
	note[ 3 ] = ( instrument > 0xF && instrument < 0xFF ) ? B36_TO_STR[ ( instrument >> 4 ) & 0xF ] : 45;
	note[ 4 ] = ( instrument > 0x0 && instrument < 0xFF ) ? B36_TO_STR[ instrument & 0xF ] : 45;
	note[ 5 ] = ( volume > 0xF && volume < 0xFF ) ? B36_TO_STR[ ( volume >> 4 ) & 0xF ] : 45;
	note[ 6 ] = ( volume > 0x0 && volume < 0xFF ) ? B36_TO_STR[ volume & 0xF ] : 45;
	if( ( effect > 0 || param > 0 ) && effect < 36 ) {
		note[ 7 ] = B36_TO_STR[ effect ];
	} else if( effect > 0x80 && effect < 0x9F ) {
		note[ 7 ] = 96 + ( effect & 0x1F );
	} else {
		note[ 7 ] = 45;
	}
	note[ 8 ] = ( effect > 0 || param > 0 ) ? B36_TO_STR[ ( param >> 4 ) & 0xF ] : 45;
	note[ 9 ] = ( effect > 0 || param > 0 ) ? B36_TO_STR[ param & 0xF ] : 45;
}

static void draw_pattern( struct module *mod, int pat, int row, int channel, int mute ) {
	int c, c1, y, r, x, clr, bclr, scroll_x, scroll_w;
	char note[ 10 ];
	int num_chan = mod->patterns[ pat ].num_channels;
	int num_rows = mod->patterns[ pat ].num_rows;
	int num_cols = ( WIDTH - 4 * 8 ) / ( 11 * 8 );
	if( num_cols > num_chan - channel ) {
		num_cols = num_chan - channel;
	}
	fill_rect( 0, 0, WIDTH, HEIGHT, 0 );
	draw_int( pat, 3, 0, 0, 3 );
	for( c = 0; c < num_cols; c++ ) {
		if( mute & ( 1 << ( c + channel ) ) ) {
			draw_text( " Muted  ", 8, 0, c * 11 + 4, 3 );
			draw_int( c + channel, 2, 0, c * 11 + 12, 3 );
		} else {
			draw_text( "Channel ", 8, 0, c * 11 + 4, 0 );
			draw_int( c + channel, 2, 0, c * 11 + 12, 0 );
		}
	}
	for( y = 1; y < 16; y++ ) {
		r = row - 8 + y;
		if( r >= 0 && r < num_rows ) {
			bclr = ( y == 8 ) ? 8 : 0;
			draw_int( r, 3, y, 0, bclr );
			for( c = 0; c < num_cols; c++ ) {
				x = 4 + c * 11;
				get_note( &mod->patterns[ pat ], r, c + channel, note );
				if( mute & ( 1 << ( c + channel ) ) ) {
					draw_text( note, 10, y, x, bclr );
				} else {
					draw_text( note, 3, y, x, note[ 0 ] == 45 ? bclr : bclr + 2 );
					draw_char( note[ 3 ], y, x + 3, note[ 3 ] == 45 ? bclr : bclr + 3 );
					draw_char( note[ 4 ], y, x + 4, note[ 4 ] == 45 ? bclr : bclr + 3 );
					clr = bclr;
					if( note[ 5 ] >= 48 && note[ 5 ] <= 70 ) {
						clr = bclr + VC_CLR[ note[ 5 ] - 48 ];
					}
					draw_char( note[ 5 ], y, x + 5, clr );
					draw_char( note[ 6 ], y, x + 6, clr );
					clr = bclr;
					if( note[ 7 ] == 69 && note[ 8 ] >= 48 && note[ 8 ] <= 70 ) {
						clr = clr + EX_CLR[ note[ 8 ] - 48 ];
					} else if( note[ 7 ] == 115 && note[ 8 ] >= 48 && note[ 8 ] <= 70 ) {
						clr = clr + SX_CLR[ note[ 8 ] - 48 ];
					} else if( note[ 7 ] >= 48 && note[ 7 ] <= 126 ) {
						clr = clr + FX_CLR[ note[ 7 ] - 48 ];
					}
					draw_text( &note[ 7 ], 3, y, x + 7, clr );
				}
			}
		}
	}
	scroll_x = 4 + num_cols * 11 * channel / num_chan;
	scroll_w = num_cols * 11 * num_cols / num_chan;
	draw_char( 91, 16, scroll_x - 1, 0 );
	for( c = scroll_x, c1 = scroll_x + scroll_w - 1; c < c1; c++ ) {
		draw_char( 61, 16, c, 0 );
	}
	draw_char( 93, 16, scroll_x + scroll_w - 1, 0 );
}

static int scroll_click( struct module *mod, int x, int channel ) {
	int num_chan = mod->num_channels;
	int num_cols = ( WIDTH - 4 * 8 ) / ( 11 * 8 );
	if( num_cols > num_chan - channel ) {
		num_cols = num_chan - channel;
	}
	if( x > ( 4 + num_cols * 11 * channel / num_chan ) * 8 ) {
		channel += 4;
		if( channel > mod->num_channels - num_cols ) {
			channel = mod->num_channels - num_cols;
		}
	} else {
		channel -= 4;
		if( channel < 0 ) {
			channel = 0;
		}
	}
	return channel;
}

static void close_display() {
	if( charset ) {
		SDL_DestroyTexture( charset );
		charset = NULL;
	}
	if( target ) {
		SDL_DestroyTexture( target );
		target = NULL;
	}
	if( renderer ) {
		SDL_DestroyRenderer( renderer );
		renderer = NULL;
	}
	if( window ) {
		SDL_DestroyWindow( window );
		window = NULL;
	}
}

static int open_display() {
	int result = 0, length;
	char * buffer;
	window = SDL_CreateWindow( IBXM_VERSION, SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, WIDTH, HEIGHT, 0 );
	if( window ) {
		renderer = SDL_CreateRenderer( window, -1, SDL_RENDERER_TARGETTEXTURE );
		if( renderer ) {
			target = SDL_CreateTexture( renderer, SDL_PIXELFORMAT_RGBA8888, SDL_TEXTUREACCESS_TARGET, WIDTH, HEIGHT );
			if( target ) { 
				SDL_SetRenderTarget( renderer, target );
				length = read_file( "topaz8.txt", NULL );
				if( length > 0 ) {
					buffer = calloc( length + 1, sizeof( char ) );
					if( buffer ) {
						read_file( "topaz8.txt", buffer );
						charset = create_charset( buffer, PAL_ENTRIES, 16 );
						if( charset ) {
							result = 1;
						}
						free( buffer );
					}
				}
			}
		}
	}
	if( !result ) {
		close_display();
	}
	return result;
}

static void redraw_display() {
	if( renderer && target ) {
		SDL_SetRenderTarget( renderer, NULL );
		SDL_RenderCopy( renderer, target, NULL, NULL );
		SDL_RenderPresent( renderer );
		SDL_SetRenderTarget( renderer, target );
	}
}

static int play_module( struct module *module, int interpolation, int display ) {
	int result, chan, scroll = 0;
	SDL_AudioSpec audiospec;
	SDL_Event event = { 0 };
	/* Initialise replay.*/
	struct replay *replay = new_replay( module, SAMPLING_FREQ, interpolation );
	if( replay ) {
		/* Calculate song length. */
		samples_remaining = replay_calculate_duration( replay );
		printf( "Song Duration: %d seconds.\n", samples_remaining / SAMPLING_FREQ );
		fflush( NULL );
		/* Initialise SDL_AudioSpec Structure. */
		memset( &audiospec, 0, sizeof( SDL_AudioSpec ) );
		audiospec.freq = SAMPLING_FREQ;
		audiospec.format = AUDIO_S16SYS;
		audiospec.channels = 2;
		audiospec.samples = BUFFER_SAMPLES;
		audiospec.callback = audio_callback;
		audiospec.userdata = replay;
		/* Initialise audio subsystem. */
		result = SDL_Init( SDL_INIT_AUDIO | ( display ? SDL_INIT_VIDEO : 0 ) );
		if( result == 0 ) {
			if( display ) {
				display = open_display();
			}
			/* Open the audio device. */
			result = SDL_OpenAudio( &audiospec, NULL );
			if( result == 0 ) {
				/* Register events. */
				song_end_event = SDL_RegisterEvents( 1 );
				display_event = SDL_RegisterEvents( 1 );
				/* Begin playback. */
				SDL_PauseAudio( 0 );
				/* Wait for playback to finish. */
				while( event.type != SDL_QUIT ) {
					SDL_WaitEvent( &event );
					if( event.type == display_event ) {
						/*printf( "%03d %03d\n", ( int ) event.user.code >> 8, ( int ) event.user.code & 0xFF );*/
						draw_pattern( module, module->sequence[ event.user.code >> 8 ], event.user.code & 0xFF, scroll, mute );
						redraw_display();
					} else if( event.type == SDL_MOUSEBUTTONDOWN ) {
						if( event.button.y > 256 ) {
							scroll = scroll_click( module, event.button.x, scroll );
						} else {
							chan = ( event.button.x - 4 * 8 ) / ( 11 * 8 );
							if( event.button.x < 4 * 8 || chan >= module->num_channels ) {
								mute = 0;
							} else {
								chan = 1 << ( scroll + chan );
								if( mute == ~chan ) {
									/* Solo channel, unmute all. */
									mute = 0;
								} else if( mute & chan ) {
									/* Muted channel, unmute. */
									mute ^= chan;
								} else {
									/* Unmuted channel, set as solo. */
									mute = -1 ^ chan;
								}
							}
						}
					} else if( event.type == song_end_event ){
						break;
					}
				}
				/* Shut down. */
				SDL_CloseAudio();
				close_display();
				SDL_Quit();
			} else {
				fprintf( stderr, "Unable to open audio device: %s\n", SDL_GetError() );
			}
		} else {
			fprintf( stderr, "Unable to initialise SDL: %s\n", SDL_GetError() );
		}
		dispose_replay( replay );
	} else {
		fprintf( stderr, "Unable to initialise replay.\n" );
	}
	return result;
}

int main( int argc, char **argv ) {
	int arg, length, result = EXIT_FAILURE;
	int interpolation = 0, display = 0;
	char *filename = NULL, *input, message[ 64 ] = "";
	struct data data;
	struct module *module;
	for( arg = 1; arg < argc; arg++ ) {
		/* Parse arguments.*/
		if( strcmp( argv[ arg ], "-loop" ) == 0 ) {
			loop = 1;
		} else if( strcmp( argv[ arg ], "-reverb" ) == 0 ) {
			reverb_len = REVERB_BUF_LEN;
		} else if( strcmp( argv[ arg ], "-interp" ) == 0 ) {
			interpolation = 1;
		} else if( strcmp( argv[ arg ], "-display" ) == 0 ) {
			display = 1;
		} else {
			filename = argv[ arg ];
		}
	}
	if( filename == NULL ) {
		fprintf( stderr, "%s\nUsage: %s [-display] [-loop] [-reverb] [-interp] module.xm\n", IBXM_VERSION, argv[ 0 ] );
	} else {
		/* Read module file.*/
		length = read_file( filename, NULL );
		if( length >= 0 ) {
			input = calloc( length, 1 );
			if( input != NULL ) {
				if( read_file( filename, input ) >= 0 ) {
					data.buffer = input;
					data.length = length;
					module = module_load( &data, message );
					if( module ) {
						print_module_info( module );
						/* Install signal handlers.*/
						signal( SIGTERM, termination_handler );
						signal( SIGINT,  termination_handler );
						/* Play.*/
						if( play_module( module, interpolation, display ) == 0 ) {
							result = EXIT_SUCCESS;
						}
						dispose_module( module );
					} else {
						fputs( message, stderr );
						fputs( "\n", stderr );
					}
				}
				free( input );
			}
		}
	}
	return result;
}
