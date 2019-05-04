#ifndef __MICROMOD_H
#define __MICROMOD_H

#ifndef MICROMOD_MAX_CHANNELS
#define MICROMOD_MAX_CHANNELS 16
#endif

/*
	Structures for internal usage
*/

struct micromod_note {
	unsigned short key;
	unsigned char instrument, effect, param;
};

struct micromod_instrument {
	unsigned char volume, fine_tune;
	unsigned long loop_start, loop_length;
	signed char *sample_data;
};

/*
	Main object-like structure
*/

struct micromod_channel {
	struct micromod_note note;
	unsigned short period, porta_period;
	unsigned long sample_offset, sample_idx, step;
	unsigned char volume, panning, fine_tune, ampl, mute;
	unsigned char id, instrument, assigned, porta_speed, pl_row, fx_count;
	unsigned char vibrato_type, vibrato_phase, vibrato_speed, vibrato_depth;
	unsigned char tremolo_type, tremolo_phase, tremolo_speed, tremolo_depth;
	signed char tremolo_add, vibrato_add, arpeggio_add;
};

struct micromod_obj {
	signed char *module_data;
	unsigned char *pattern_data, *sequence;
	long song_length, restart, num_patterns, num_channels;
	struct micromod_instrument instruments[ 32 ];

	long sample_rate, gain, c2_rate, tick_len, tick_offset;
	long pattern, break_pattern, row, next_row, tick;
	long speed, pl_count, pl_channel, random_seed;

	struct micromod_channel channels[ MICROMOD_MAX_CHANNELS ];
};

/*
	Common functions
*/

/*
	Returns a string containing version information.
*/
const char *micromod_get_version( void );

/*
	Calculate the length in bytes of a module file given the 1084-byte header.
	Returns -1 if the data is not recognised as a module.
*/
long micromod_calculate_mod_file_len( signed char *module_header );

/*
	Instance-specific functions
*/

/*
	Set the player to play the specified module data.
	Returns -1 if the data is not recognised as a module.
	Returns -2 if the sampling rate is less than 8000hz.
*/
long micromod_initialise_obj( struct micromod_obj* obj, signed char *data, long sampling_rate );

/*
	Obtains song and instrument names from the module.
	The song name is returned as instrument 0.
	The name is copied into the location pointed to by string,
	and is at most 23 characters long, including the trailing null.
*/
void micromod_get_string_obj( struct micromod_obj* obj, long instrument, char *string );

/*
	Returns the total song duration in samples at the current sampling rate.
*/
long micromod_calculate_song_duration_obj( struct micromod_obj* obj );

/*
	Jump directly to a specific pattern in the sequence.
*/
void micromod_set_position_obj( struct micromod_obj* obj, long pos );

/*
	Mute the specified channel.
	If channel is negative, un-mute all channels.
	Returns the number of channels.
*/
long micromod_mute_channel_obj( struct micromod_obj* obj, long channel );

/*
	Set the playback gain.
	For 4-channel modules, a value of 64 can be used without distortion.
	For 8-channel modules, a value of 32 or less is recommended.
*/
void micromod_set_gain_obj( struct micromod_obj* obj, long value );

/*
	Calculate the specified number of stereo samples of audio.
	Output buffer must be zeroed.
*/
void micromod_get_audio_obj( struct micromod_obj* obj, short *output_buffer, long count );

/*
	Global instance versions (provided for compatiblity, shouldn't be used in new programs)
*/

__attribute__ ((deprecated)) long micromod_initialise( signed char *data, long sampling_rate );
__attribute__ ((deprecated)) void micromod_get_string( long instrument, char *string );
__attribute__ ((deprecated)) long micromod_calculate_song_duration( void );
__attribute__ ((deprecated)) void micromod_set_position( long pos );
__attribute__ ((deprecated)) long micromod_mute_channel( long channel );
__attribute__ ((deprecated)) void micromod_set_gain( long value );
__attribute__ ((deprecated)) void micromod_get_audio( short *output_buffer, long count );
#endif
