
package mumart.micromod.s3m;

/*
	java scream tracker 3 replay (c)2010 mumart@gmail.com
*/
public class Micros3m implements mumart.micromod.replay.Replay {
	public static final String VERSION = "20100927 (c)2010 mumart@gmail.com";

	private static final int MIN_TEMPO = 10;

	private Module module;
	private int[] ramp_buf;
	private Channel[] channels;
	private boolean interpolate;
	private int sampling_rate, tick_len, ramp_len, ramp_rate;
	private int seq_pos, break_seq_pos, row, next_row, tick;
	private int speed, pl_count, pl_channel;
	private GlobalVol global_vol;
	private Note note;

	public Micros3m( Module module, int sampling_rate, boolean interpolate ) {
		this.module = module;
		this.sampling_rate = sampling_rate;
		this.interpolate = interpolate;
		if( sampling_rate < 16000 )
			throw new IllegalArgumentException( "Unsupported sampling rate!" );
		ramp_len = 256;
		while( ramp_len * 1024 > sampling_rate ) ramp_len /= 2;
		ramp_buf = new int[ ramp_len * 2 ];
		ramp_rate = 256 / ramp_len;
		channels = new Channel[ module.num_channels ];
		global_vol = new GlobalVol();
		note = new Note();
		set_sequence_pos( 0 );
	}

	public String get_version() {
		return VERSION;
	}

	public int get_sampling_rate() {
		return sampling_rate;
	}

	public int get_mix_buffer_length() {
		return ( sampling_rate * 5 / MIN_TEMPO ) + ( ramp_len * 2 );
	}

	public String get_string( int index ) {
		if( index == 0 ) return module.song_name;
		if( index < 0 || index > module.num_instruments ) return null;
		return module.instruments[ index ].name;
	}

	public void set_sequence_pos( int pos ) {
		if( pos >= module.sequence_length ) pos = 0;
		break_seq_pos = pos;
		next_row = 0;
		tick = 1;
		speed = module.default_speed > 0 ? module.default_speed : 6;
		set_tempo( module.default_tempo > 0 ? module.default_tempo : 125 );
		global_vol.volume = module.default_g_vol < 64 ? module.default_g_vol : 64;
		pl_count = pl_channel = -1;
		for( int idx = 0; idx < module.num_channels; idx++ )
			channels[ idx ] = new Channel( module, idx, sampling_rate, global_vol );
		for( int idx = 0, end = ramp_len * 2; idx < end; idx++ ) ramp_buf[ idx ] = 0;
		tick();
	}

	public int calculate_song_duration() {
		int duration = 0;
		set_sequence_pos( 0 );
		boolean song_end = false;
		while( !song_end ) {
			duration += tick_len;
			song_end = tick();
		}
		set_sequence_pos( 0 );
		return duration;	
	}

	/*
		Seek to approximately the specified sample position.
		The actual sample position reached is returned.
	*/
	public int seek( int sample_pos ) {
		set_sequence_pos( 0 );
		int current_pos = 0;
		while( ( sample_pos - current_pos ) >= tick_len ) {
			for( int idx = 0; idx < module.num_channels; idx++ )
				channels[ idx ].update_sample_idx( tick_len );
			current_pos += tick_len;
			tick();
		}
		return current_pos;
	}

	/*
		Generate audio.
		The number of samples placed into output_buf is returned.
		The output buffer length must be at least that returned by get_mix_buffer_length().
		A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels.
	*/
	public int get_audio( int[] output_buf ) {
		// Clear output buffer.
		int out_idx = 0;
		int out_ep1 = tick_len + ramp_len << 1;
		while( out_idx < out_ep1 ) output_buf[ out_idx++ ] = 0;
		// Resample.
		for( int chan_idx = 0; chan_idx < module.num_channels; chan_idx++ ) {
			Channel chan = channels[ chan_idx ];
			chan.resample( output_buf, 0, tick_len + ramp_len, interpolate );
			chan.update_sample_idx( tick_len );
		}
		volume_ramp( output_buf );
		tick();
		return tick_len;
	}

	private void set_tempo( int tempo ) {
		// Make sure tick length is even to simplify downsampling.
		tick_len = ( ( sampling_rate * 5 ) / ( tempo * 2 ) ) & -2;
	}

	private void volume_ramp( int[] mix_buf ) {
		int a1, a2, s1, s2, offset = 0;
		for( a1 = 0; a1 < 256; a1 += ramp_rate ) {
			a2 = 256 - a1;
			s1 =  mix_buf[ offset ] * a1;
			s2 = ramp_buf[ offset ] * a2;
			mix_buf[ offset++ ] = s1 + s2 >> 8;
			s1 =  mix_buf[ offset ] * a1;
			s2 = ramp_buf[ offset ] * a2;
			mix_buf[ offset++ ] = s1 + s2 >> 8;
		}
		System.arraycopy( mix_buf, tick_len << 1, ramp_buf, 0, offset );
	}

	private boolean tick() {
		boolean song_end = false;
		if( --tick <= 0 ) {
			tick = speed;
			song_end = row();
		} else {
			for( int idx = 0; idx < module.num_channels; idx++ ) channels[ idx ].tick();
		}
		return song_end;
	}

	private boolean row() {
		boolean song_end = false;
		if( break_seq_pos >= 0 ) {
			if( break_seq_pos >= module.sequence_length ) break_seq_pos = next_row = 0;
			while( module.sequence[ break_seq_pos ] >= module.num_patterns ) {
				break_seq_pos++;
				if( break_seq_pos >= module.sequence_length ) break_seq_pos = next_row = 0;
			}
			if( break_seq_pos <= seq_pos ) song_end = true;
			seq_pos = break_seq_pos;
			for( int idx = 0; idx < module.num_channels; idx++ ) channels[ idx ].pl_row = 0;
			break_seq_pos = -1;
		}
		Pattern pattern = module.patterns[ module.sequence[ seq_pos ] ];
		row = next_row;
		if( row >= Pattern.NUM_ROWS ) row = 0;
		next_row = row + 1;
		if( next_row >= Pattern.NUM_ROWS ) {
			break_seq_pos = seq_pos + 1;
			next_row = 0;
		}
		int note_idx = row * module.num_channels;
		for( int chan_idx = 0; chan_idx < module.num_channels; chan_idx++ ) {
			Channel channel = channels[ chan_idx ];
			pattern.get_note( note_idx + chan_idx, note );
			if( note.effect == 0x13 ) {
				note.effect = 0x100 | ( note.param >> 4 );
				note.param &= 0xF;
			}
			channel.row( note );
			switch( note.effect ) {
				case 0x01: /* Set Speed.*/
					if( note.param > 0 ) speed = tick = note.param;
					break;
				case 0x02: /* Pattern Jump.*/
					if( pl_count < 0 ) {
						break_seq_pos = note.param;
						next_row = 0;
					}
					break;
				case 0x03: /* Pattern Break.*/
					if( pl_count < 0 ) {
						break_seq_pos = seq_pos + 1;
						next_row = ( note.param >> 4 ) * 10 + ( note.param & 0xF );
					}
					break;
				case 0x14: /* Set Tempo.*/
					if( note.param >= MIN_TEMPO ) set_tempo( note.param );
					break;
				case 0x10B: /* Pattern Loop.*/
					if( note.param == 0 ) /* Set loop marker on this channel. */
						channel.pl_row = row;
					if( channel.pl_row < row ) { /* Marker valid. Begin looping. */
						if( pl_count < 0 ) { /* Not already looping, begin. */
							pl_count = note.param;
							pl_channel = chan_idx;
						}
						if( pl_channel == chan_idx ) { /* Next Loop.*/
							if( pl_count == 0 ) { /* Loop finished. */
								/* Invalidate current marker. */
								channel.pl_row = row + 1;
							} else { /* Loop and cancel any breaks on this row. */
								next_row = channel.pl_row;
								break_seq_pos = -1;
							}
							pl_count--;
						}
					}
					break;
				case 0x10E: /* Pattern Delay.*/
					tick = speed + speed * note.param;
					break;
			}
		}
		return song_end;
	}
}
