#include "micromod.h"

#define FP_SHIFT 14
#define FP_ONE   16384
#define FP_MASK  16383

static const char *MICROMOD_VERSION = "Micromod Protracker replay 20180625 (c)mumart@gmail.com";

static const unsigned short fine_tuning[] = {
	4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126,
	4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894
};

static const unsigned short arp_tuning[] = {
	4096, 3866, 3649, 3444, 3251, 3069, 2896, 2734,
	2580, 2435, 2299, 2170, 2048, 1933, 1825, 1722
};

static const unsigned char sine_table[] = {
	  0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
	255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
};

static long calculate_num_patterns( signed char *module_header ) {
	long num_patterns, order_entry, pattern;
	num_patterns = 0;
	for( pattern = 0; pattern < 128; pattern++ ) {
		order_entry = module_header[ 952 + pattern ] & 0x7F;
		if( order_entry >= num_patterns ) num_patterns = order_entry + 1;
	}
	return num_patterns;
}

static long calculate_num_channels( signed char *module_header ) {
	long numchan;
	switch( ( module_header[ 1082 ] << 8 ) | module_header[ 1083 ] ) {
		case 0x4b2e: /* M.K. */
		case 0x4b21: /* M!K! */
		case 0x542e: /* N.T. */
		case 0x5434: /* FLT4 */
			numchan = 4;
			break;
		case 0x484e: /* xCHN */
			numchan = module_header[ 1080 ] - 48;
			break;
		case 0x4348: /* xxCH */
			numchan = ( ( module_header[ 1080 ] - 48 ) * 10 ) + ( module_header[ 1081 ] - 48 );
			break;
		default: /* Not recognised. */
			numchan = 0;
			break;
	}
	if( numchan > MICROMOD_MAX_CHANNELS ) numchan = 0;
	return numchan;
}

static long unsigned_short_big_endian( signed char *buf, long offset ) {
	return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
}

static void set_tempo( struct micromod_obj* obj, long tempo ) {
	obj->tick_len = ( ( obj->sample_rate << 1 ) + ( obj->sample_rate >> 1 ) ) / tempo;
}

static void update_frequency( struct micromod_obj* obj, struct micromod_channel *chan ) {
	long period, volume;
	unsigned long freq;
	period = chan->period + chan->vibrato_add;
	period = period * arp_tuning[ chan->arpeggio_add ] >> 11;
	period = ( period >> 1 ) + ( period & 1 );
	if( period < 14 ) period = 6848;
	freq = obj->c2_rate * 428 / period;
	chan->step = ( freq << FP_SHIFT ) / obj->sample_rate;
	volume = chan->volume + chan->tremolo_add;
	if( volume > 64 ) volume = 64;
	if( volume < 0 ) volume = 0;
	chan->ampl = ( volume * obj->gain ) >> 5;
}

static void tone_portamento( struct micromod_channel *chan ) {
	long source, dest;
	source = chan->period;
	dest = chan->porta_period;
	if( source < dest ) {
		source += chan->porta_speed;
		if( source > dest ) source = dest;
	} else if( source > dest ) {
		source -= chan->porta_speed;
		if( source < dest ) source = dest;
	}
	chan->period = source;
}

static void volume_slide( struct micromod_channel *chan, long param ) {
	long volume;
	volume = chan->volume + ( param >> 4 ) - ( param & 0xF );
	if( volume < 0 ) volume = 0;
	if( volume > 64 ) volume = 64;
	chan->volume = volume;
}

static long waveform( struct micromod_obj* obj, long phase, long type ) {
	long amplitude = 0;
	switch( type & 0x3 ) {
		case 0: /* Sine. */
			amplitude = sine_table[ phase & 0x1F ];
			if( ( phase & 0x20 ) > 0 ) amplitude = -amplitude;
			break;
		case 1: /* Saw Down. */
			amplitude = 255 - ( ( ( phase + 0x20 ) & 0x3F ) << 3 );
			break;
		case 2: /* Square. */
			amplitude = 255 - ( ( phase & 0x20 ) << 4 );
			break;
		case 3: /* Random. */
			amplitude = ( obj->random_seed >> 20 ) - 255;
			obj->random_seed = ( obj->random_seed * 65 + 17 ) & 0x1FFFFFFF;
			break;
	}
	return amplitude;
}

static void vibrato( struct micromod_obj* obj, struct micromod_channel *chan ) {
	chan->vibrato_add = waveform( obj, chan->vibrato_phase, chan->vibrato_type ) * chan->vibrato_depth >> 7;
}

static void tremolo( struct micromod_obj* obj, struct micromod_channel *chan ) {
	chan->tremolo_add = waveform( obj, chan->tremolo_phase, chan->tremolo_type ) * chan->tremolo_depth >> 6;
}

static void trigger( struct micromod_obj* obj, struct micromod_channel *channel ) {
	long period, ins;
	ins = channel->note.instrument;
	if( ins > 0 && ins < 32 ) {
		channel->assigned = ins;
		channel->sample_offset = 0;
		channel->fine_tune = obj->instruments[ ins ].fine_tune;
		channel->volume = obj->instruments[ ins ].volume;
		if( obj->instruments[ ins ].loop_length > 0 && channel->instrument > 0 )
			channel->instrument = ins;
	}
	if( channel->note.effect == 0x09 ) {
		channel->sample_offset = ( channel->note.param & 0xFF ) << 8;
	} else if( channel->note.effect == 0x15 ) {
		channel->fine_tune = channel->note.param;
	}
	if( channel->note.key > 0 ) {
		period = ( channel->note.key * fine_tuning[ channel->fine_tune & 0xF ] ) >> 11;
		channel->porta_period = ( period >> 1 ) + ( period & 1 );
		if( channel->note.effect != 0x3 && channel->note.effect != 0x5 ) {
			channel->instrument = channel->assigned;
			channel->period = channel->porta_period;
			channel->sample_idx = ( channel->sample_offset << FP_SHIFT );
			if( channel->vibrato_type < 4 ) channel->vibrato_phase = 0;
			if( channel->tremolo_type < 4 ) channel->tremolo_phase = 0;
		}
	}
}

static void channel_row( struct micromod_obj* obj, struct micromod_channel *chan ) {
	long effect, param, volume, period;
	effect = chan->note.effect;
	param = chan->note.param;
	chan->vibrato_add = chan->tremolo_add = chan->arpeggio_add = chan->fx_count = 0;
	if( !( effect == 0x1D && param > 0 ) ) {
		/* Not note delay. */
		trigger( obj, chan );
	}
	switch( effect ) {
		case 0x3: /* Tone Portamento.*/
			if( param > 0 ) chan->porta_speed = param;
			break;
		case 0x4: /* Vibrato.*/
			if( ( param & 0xF0 ) > 0 ) chan->vibrato_speed = param >> 4;
			if( ( param & 0x0F ) > 0 ) chan->vibrato_depth = param & 0xF;
			vibrato( obj, chan );
			break;
		case 0x6: /* Vibrato + Volume Slide.*/
			vibrato( obj, chan );
			break;
		case 0x7: /* Tremolo.*/
			if( ( param & 0xF0 ) > 0 ) chan->tremolo_speed = param >> 4;
			if( ( param & 0x0F ) > 0 ) chan->tremolo_depth = param & 0xF;
			tremolo( obj, chan );
			break;
		case 0x8: /* Set Panning (0-127). Not for 4-channel Protracker. */
			if( obj->num_channels != 4 ) {
				chan->panning = ( param < 128 ) ? param : 127;
			}
			break;
		case 0xB: /* Pattern Jump.*/
			if( obj->pl_count < 0 ) {
				obj->break_pattern = param;
				obj->next_row = 0;
			}
			break;
		case 0xC: /* Set Volume.*/
			chan->volume = param > 64 ? 64 : param;
			break;
		case 0xD: /* Pattern Break.*/
			if( obj->pl_count < 0 ) {
				if( obj->break_pattern < 0 ) obj->break_pattern = obj->pattern + 1;
				obj->next_row = ( param >> 4 ) * 10 + ( param & 0xF );
				if( obj->next_row >= 64 ) obj->next_row = 0;
			}
			break;
		case 0xF: /* Set Speed.*/
			if( param > 0 ) {
				if( param < 32 ) obj->tick = obj->speed = param;
				else set_tempo( obj, param );
			}
			break;
		case 0x11: /* Fine Portamento Up.*/
			period = chan->period - param;
			chan->period = period < 0 ? 0 : period;
			break;
		case 0x12: /* Fine Portamento Down.*/
			period = chan->period + param;
			chan->period = period > 65535 ? 65535 : period;
			break;
		case 0x14: /* Set Vibrato Waveform.*/
			if( param < 8 ) chan->vibrato_type = param;
			break;
		case 0x16: /* Pattern Loop.*/
			if( param == 0 ) /* Set loop marker on this channel. */
				chan->pl_row = obj->row;
			if( chan->pl_row < obj->row && obj->break_pattern < 0 ) { /* Marker valid. */
				if( obj->pl_count < 0 ) { /* Not already looping, begin. */
					obj->pl_count = param;
					obj->pl_channel = chan->id;
				}
				if( obj->pl_channel == chan->id ) { /* Next Loop.*/
					if( obj->pl_count == 0 ) { /* Loop finished. */
						/* Invalidate current marker. */
						chan->pl_row = obj->row + 1;
					} else { /* Loop. */
						obj->next_row = chan->pl_row;
					}
					--(obj->pl_count);
				}
			}
			break;
		case 0x17: /* Set Tremolo Waveform.*/
			if( param < 8 ) chan->tremolo_type = param;
			break;
		case 0x1A: /* Fine Volume Up.*/
			volume = chan->volume + param;
			chan->volume = volume > 64 ? 64 : volume;
			break;
		case 0x1B: /* Fine Volume Down.*/
			volume = chan->volume - param;
			chan->volume = volume < 0 ? 0 : volume;
			break;
		case 0x1C: /* Note Cut.*/
			if( param <= 0 ) chan->volume = 0;
			break;
		case 0x1E: /* Pattern Delay.*/
			obj->tick = obj->speed * (param + 1L);
			break;
	}
	update_frequency( obj, chan );
}

static void channel_tick( struct micromod_obj* obj, struct micromod_channel *chan ) {
	long effect, param, period;
	effect = chan->note.effect;
	param = chan->note.param;
	chan->fx_count++;
	switch( effect ) {
		case 0x1: /* Portamento Up.*/
			period = chan->period - param;
			chan->period = period < 0 ? 0 : period;
			break;
		case 0x2: /* Portamento Down.*/
			period = chan->period + param;
			chan->period = period > 65535 ? 65535 : period;
			break;
		case 0x3: /* Tone Portamento.*/
			tone_portamento( chan );
			break;
		case 0x4: /* Vibrato.*/
			chan->vibrato_phase += chan->vibrato_speed;
			vibrato( obj, chan );
			break;
		case 0x5: /* Tone Porta + Volume Slide.*/
			tone_portamento( chan );
			volume_slide( chan, param );
			break;
		case 0x6: /* Vibrato + Volume Slide.*/
			chan->vibrato_phase += chan->vibrato_speed;
			vibrato( obj, chan );
			volume_slide( chan, param );
			break;
		case 0x7: /* Tremolo.*/
			chan->tremolo_phase += chan->tremolo_speed;
			tremolo( obj, chan );
			break;
		case 0xA: /* Volume Slide.*/
			volume_slide( chan, param );
			break;
		case 0xE: /* Arpeggio.*/
			if( chan->fx_count > 2 ) chan->fx_count = 0;
			if( chan->fx_count == 0 ) chan->arpeggio_add = 0;
			if( chan->fx_count == 1 ) chan->arpeggio_add = param >> 4;
			if( chan->fx_count == 2 ) chan->arpeggio_add = param & 0xF;
			break;
		case 0x19: /* Retrig.*/
			if( chan->fx_count >= param ) {
				chan->fx_count = 0;
				chan->sample_idx = 0;
			}
			break;
		case 0x1C: /* Note Cut.*/
			if( param == chan->fx_count ) chan->volume = 0;
			break;
		case 0x1D: /* Note Delay.*/
			if( param == chan->fx_count ) trigger( obj, chan );
			break;
	}
	if( effect > 0 ) update_frequency( obj, chan );
}

static long sequence_row( struct micromod_obj* obj ) {
	long song_end, chan_idx, pat_offset;
	long effect, param;
	struct micromod_note *note;
	song_end = 0;
	if( obj->next_row < 0 ) {
		obj->break_pattern = obj->pattern + 1;
		obj->next_row = 0;
	}
	if( obj->break_pattern >= 0 ) {
		if( obj->break_pattern >= obj->song_length ) obj->break_pattern = obj->next_row = 0;
		if( obj->break_pattern <= obj->pattern ) song_end = 1;
		obj->pattern = obj->break_pattern;
		for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ ) obj->channels[ chan_idx ].pl_row = 0;
		obj->break_pattern = -1;
	}
	obj->row = obj->next_row;
	obj->next_row = obj->row + 1;
	if( obj->next_row >= 64 ) obj->next_row = -1;
	pat_offset = ( obj->sequence[ obj->pattern ] * 64 + obj->row ) * obj->num_channels * 4;
	for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ ) {
		note = &obj->channels[ chan_idx ].note;
		note->key  = ( obj->pattern_data[ pat_offset ] & 0xF ) << 8;
		note->key |=   obj->pattern_data[ pat_offset + 1 ];
		note->instrument  = obj->pattern_data[ pat_offset + 2 ] >> 4;
		note->instrument |= obj->pattern_data[ pat_offset ] & 0x10;
		effect = obj->pattern_data[ pat_offset + 2 ] & 0xF;
		param = obj->pattern_data[ pat_offset + 3 ];
		pat_offset += 4;
		if( effect == 0xE ) {
			effect = 0x10 | ( param >> 4 );
			param &= 0xF;
		}
		if( effect == 0 && param > 0 ) effect = 0xE;
		note->effect = effect;
		note->param = param;
		channel_row( obj, &obj->channels[ chan_idx ] );
	}
	return song_end;
}

static long sequence_tick( struct micromod_obj* obj ) {
	long song_end, chan_idx;
	song_end = 0;
	if( --(obj->tick) <= 0 ) {
		obj->tick = obj->speed;
		song_end = sequence_row(obj);
	} else {
		for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ )
			channel_tick( obj, &obj->channels[ chan_idx ] );
	}
	return song_end;
}

static void resample( struct micromod_obj* obj, struct micromod_channel *chan, short *buf, long offset, long count ) {
	unsigned long epos;
	unsigned long buf_idx = offset << 1;
	unsigned long buf_end = ( offset + count ) << 1;
	unsigned long sidx = chan->sample_idx;
	unsigned long step = chan->step;
	unsigned long llen = obj->instruments[ chan->instrument ].loop_length;
	unsigned long lep1 = obj->instruments[ chan->instrument ].loop_start + llen;
	signed char *sdat = obj->instruments[ chan->instrument ].sample_data;
	short ampl = buf && !chan->mute ? chan->ampl : 0;
	short lamp = ampl * ( 127 - chan->panning ) >> 5;
	short ramp = ampl * chan->panning >> 5;
	while( buf_idx < buf_end ) {
		if( sidx >= lep1 ) {
			/* Handle loop. */
			if( llen <= FP_ONE ) {
				/* One-shot sample. */
				sidx = lep1;
				break;
			}
			/* Subtract loop-length until within loop points. */
			while( sidx >= lep1 ) sidx -= llen;
		}
		/* Calculate sample position at end. */
		epos = sidx + ( ( buf_end - buf_idx ) >> 1 ) * step;
		/* Most of the cpu time is spent here. */
		if( lamp || ramp ) {
			/* Only mix to end of current loop. */
			if( epos > lep1 ) epos = lep1;
			if( lamp && ramp ) {
				/* Mix both channels. */
				while( sidx < epos ) {
					ampl = sdat[ sidx >> FP_SHIFT ];
					buf[ buf_idx++ ] += ampl * lamp >> 2;
					buf[ buf_idx++ ] += ampl * ramp >> 2;
					sidx += step;
				}
			} else {
				/* Only mix one channel. */
				if( ramp ) buf_idx++;
				while( sidx < epos ) {
					buf[ buf_idx ] += sdat[ sidx >> FP_SHIFT ] * ampl;
					buf_idx += 2;
					sidx += step;
				}
				buf_idx &= -2;
			}
		} else {
			/* No need to mix.*/
			buf_idx = buf_end;
			sidx = epos;
		}
	}
	chan->sample_idx = sidx;
}

/*
	Returns a string containing version information.
*/
const char* micromod_get_version( void ) {
	return MICROMOD_VERSION;
}

/*
	Calculate the length in bytes of a module file given the 1084-byte header.
	Returns -1 if the data is not recognised as a module.
*/
long micromod_calculate_mod_file_len( signed char *module_header ) {
	long length, numchan, inst_idx;
	numchan = calculate_num_channels( module_header );
	if( numchan <= 0 ) return -1;
	length = 1084 + 4 * numchan * 64 * calculate_num_patterns( module_header );
	for( inst_idx = 1; inst_idx < 32; inst_idx++ )
		length += unsigned_short_big_endian( module_header, inst_idx * 30 + 12 ) * 2;
	return length;
}

/*
	Set the player to play the specified module data.
	Returns -1 if the data is not recognised as a module.
	Returns -2 if the sampling rate is less than 8000hz.
*/
long micromod_initialise_obj( struct micromod_obj* obj, signed char *data, long sampling_rate ) {
	struct micromod_instrument *inst;
	long sample_data_offset, inst_idx;
	long sample_length, volume, fine_tune, loop_start, loop_length;
	obj->num_channels = calculate_num_channels( data );
	if( obj->num_channels <= 0 ) {
		obj->num_channels = 0;
		return -1;
	}
	if( sampling_rate < 8000 ) return -2;
	obj->module_data = data;
	obj->sample_rate = sampling_rate;
	obj->song_length = obj->module_data[ 950 ] & 0x7F;
	obj->restart = obj->module_data[ 951 ] & 0x7F;
	if( obj->restart >= obj->song_length ) obj->restart = 0;
	obj->sequence = (unsigned char *) obj->module_data + 952;
	obj->pattern_data = (unsigned char *) obj->module_data + 1084;
	obj->num_patterns = calculate_num_patterns( obj->module_data );
	sample_data_offset = 1084 + obj->num_patterns * 64 * obj->num_channels * 4;
	for( inst_idx = 1; inst_idx < 32; inst_idx++ ) {
		inst = &obj->instruments[ inst_idx ];
		sample_length = unsigned_short_big_endian( obj->module_data, inst_idx * 30 + 12 ) * 2;
		fine_tune = obj->module_data[ inst_idx * 30 + 14 ] & 0xF;
		inst->fine_tune = ( fine_tune & 0x7 ) - ( fine_tune & 0x8 ) + 8;
		volume = obj->module_data[ inst_idx * 30 + 15 ] & 0x7F;
		inst->volume = volume > 64 ? 64 : volume;
		loop_start = unsigned_short_big_endian( obj->module_data, inst_idx * 30 + 16 ) * 2;
		loop_length = unsigned_short_big_endian( obj->module_data, inst_idx * 30 + 18 ) * 2;
		if( loop_start + loop_length > sample_length ) {
			if( loop_start / 2 + loop_length <= sample_length ) {
				/* Some old modules have loop start in bytes. */
				loop_start = loop_start / 2;
			} else {
				loop_length = sample_length - loop_start;
			}
		}
		if( loop_length < 4 ) {
			loop_start = sample_length;
			loop_length = 0;
		}
		inst->loop_start = loop_start << FP_SHIFT;
		inst->loop_length = loop_length << FP_SHIFT;
		inst->sample_data = obj->module_data + sample_data_offset;
		sample_data_offset += sample_length;
	}
	obj->c2_rate = ( obj->num_channels > 4 ) ? 8363 : 8287;
	obj->gain = ( obj->num_channels > 4 ) ? 32 : 64;
	micromod_mute_channel_obj( obj, -1 );
	micromod_set_position_obj( obj, 0 );
	return 0;
}

/*
	Obtains song and instrument names from the module.
	The song name is returned as instrument 0.
	The name is copied into the location pointed to by string,
	and is at most 23 characters long, including the trailing null.
*/
void micromod_get_string_obj( struct micromod_obj* obj, long instrument, char *string ) {
	long index, offset, length, character;
	if( obj->num_channels <= 0 ) {
		string[ 0 ] = 0;
		return;
	}
	offset = 0;
	length = 20;
	if( instrument > 0 && instrument < 32 ) {
		offset = ( instrument - 1 ) * 30 + 20;
		length = 22;
	}
	for( index = 0; index < length; index++ ) {
		character = obj->module_data[ offset + index ];
		if( character < 32 || character > 126 ) character = ' ';
		string[ index ] = character;
	}
	string[ length ] = 0;
}

/*
	Returns the total song duration in samples at the current sampling rate.
*/
long micromod_calculate_song_duration_obj( struct micromod_obj* obj ) {
	long duration, song_end;
	duration = 0;
	if( obj->num_channels > 0 ) {
		micromod_set_position_obj( obj, 0 );
		song_end = 0;
		while( !song_end ) {
			duration += obj->tick_len;
			song_end = sequence_tick(obj);
		}
		micromod_set_position_obj( obj, 0 );
	}
	return duration;
}

/*
	Jump directly to a specific pattern in the sequence.
*/
void micromod_set_position_obj( struct micromod_obj* obj, long pos ) {
	long chan_idx;
	struct micromod_channel *chan;
	if( obj->num_channels <= 0 ) return; 
	if( pos >= obj->song_length ) pos = 0;
	obj->break_pattern = pos;
	obj->next_row = 0;
	obj->tick = 1;
	obj->speed = 6;
	set_tempo( obj, 125 );
	obj->pl_count = obj->pl_channel = -1;
	obj->random_seed = 0xABCDEF;
	for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ ) {
		chan = &obj->channels[ chan_idx ];
		chan->id = chan_idx;
		chan->instrument = chan->assigned = 0;
		chan->volume = 0;
		switch( chan_idx & 0x3 ) {
			case 0: case 3: chan->panning = 0; break;
			case 1: case 2: chan->panning = 127; break;
		}
	}
	sequence_tick(obj);
	obj->tick_offset = 0;
}

/*
	Mute the specified channel.
	If channel is negative, un-mute all channels.
	Returns the number of channels.
*/
long micromod_mute_channel_obj( struct micromod_obj* obj, long channel ) {
	long chan_idx;
	if( channel < 0 ) {
		for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ ) {
			obj->channels[ chan_idx ].mute = 0;
		}
	} else if( channel < obj->num_channels ) {
		obj->channels[ channel ].mute = 1;
	}
	return obj->num_channels;
}

/*
	Set the playback gain.
	For 4-channel modules, a value of 64 can be used without distortion.
	For 8-channel modules, a value of 32 or less is recommended.
*/
void micromod_set_gain_obj( struct micromod_obj* obj, long value ) {
	obj->gain = value;
}

/*
	Calculate the specified number of samples of audio.
	If output pointer is zero, the replay will quickly skip count samples.
	The output buffer should be cleared with zeroes.
*/
void micromod_get_audio_obj( struct micromod_obj* obj, short *output_buffer, long count ) {
	long offset, remain, chan_idx;
	if( obj->num_channels <= 0 ) return;
	offset = 0;
	while( count > 0 ) {
		remain = obj->tick_len - obj->tick_offset;
		if( remain > count ) remain = count;
		for( chan_idx = 0; chan_idx < obj->num_channels; chan_idx++ ) {
			resample( obj, &obj->channels[ chan_idx ], output_buffer, offset, remain );
		}
		obj->tick_offset += remain;
		if( obj->tick_offset == obj->tick_len ) {
			sequence_tick(obj);
			obj->tick_offset = 0;
		}
		offset += remain;
		count -= remain;
	}
}

/*
	Implement global-state compatiblity functions
*/

static struct micromod_obj micromod_global;

long micromod_initialise( signed char *data, long sampling_rate ) {
	return micromod_initialise_obj(&micromod_global, data, sampling_rate);
}

void micromod_get_string( long instrument, char *string ) {
	micromod_get_string_obj(&micromod_global, instrument, string);
}

long micromod_calculate_song_duration( void ) {
	return micromod_calculate_song_duration_obj(&micromod_global);
}

void micromod_set_position( long pos ) {
	micromod_set_position_obj(&micromod_global, pos);
}

long micromod_mute_channel( long channel ) {
	return micromod_mute_channel_obj(&micromod_global, channel);
}

void micromod_set_gain( long value ) {
	micromod_set_gain_obj(&micromod_global, value);
}

void micromod_get_audio( short *output_buffer, long count ) {
	micromod_get_audio_obj(&micromod_global, output_buffer, count);
}
