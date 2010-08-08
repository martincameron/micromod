
package micromod;

/*
	java protracker replay (c)2010 mumart@gmail.com
*/
public class Micromod implements replay.Replay {
	public static final String VERSION = "20100627";

	private Module module;
	private int[] ramp_buf;
	private Channel[] channels;
	private int sampling_rate, tick_len, ramp_len, ramp_rate;
	private int seq_pos, break_seq_pos, row, next_row, tick;
	private int speed, pl_count, pl_channel;
	private boolean interpolate;

	public Micromod( Module module, int sampling_rate, boolean interpolate ) {
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
		set_sequence_pos( 0 );
	}

	public int get_sampling_rate() {
		return sampling_rate;
	}

	public int get_mix_buffer_length() {
		return ( sampling_rate * 5 / 32 ) + ( ramp_len * 2 );
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
		speed = 6;
		set_tempo( 125 );
		pl_count = pl_channel = -1;
		for( int idx = 0; idx < module.num_channels; idx++ )
			channels[ idx ] = new Channel( module, idx, sampling_rate );
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
			if( break_seq_pos <= seq_pos ) song_end = true;
			seq_pos = break_seq_pos;
			for( int idx = 0; idx < module.num_channels; idx++ ) channels[ idx ].pl_row = 0;
			break_seq_pos = -1;
		}
		row = next_row;
		next_row = row + 1;
		if( next_row >= 64 ) {
			break_seq_pos = seq_pos + 1;
			next_row = 0;
		}
		int pat_offset = ( module.sequence[ seq_pos ] * 64 + row ) * module.num_channels * 4;
		for( int chan_idx = 0; chan_idx < module.num_channels; chan_idx++ ) {
			Channel channel = channels[ chan_idx ];
			int key = ( module.patterns[ pat_offset ] & 0xF ) << 8;
			key = key | module.patterns[ pat_offset + 1 ] & 0xFF;
			int ins = ( module.patterns[ pat_offset + 2 ] & 0xF0 ) >> 4;
			ins = ins | module.patterns[ pat_offset ] & 0x10;
			int effect = module.patterns[ pat_offset + 2 ] & 0x0F;
			int param  = module.patterns[ pat_offset + 3 ] & 0xFF;
			pat_offset += 4;
			if( effect == 0xE ) {
				effect = 0x10 | ( param >> 4 );
				param &= 0xF;
			}
			if( effect == 0 && param > 0 ) effect = 0xE;
			channel.row( key, ins, effect, param );
			switch( effect ) {
				case 0xB: /* Pattern Jump.*/
					if( pl_count < 0 ) {
						break_seq_pos = param;
						next_row = 0;
					}
					break;
				case 0xD: /* Pattern Break.*/
					if( pl_count < 0 ) {
						break_seq_pos = seq_pos + 1;
						next_row = ( param >> 4 ) * 10 + ( param & 0xF );
						if( next_row >= 64 ) next_row = 0;
					}
					break;
				case 0xF: /* Set Speed.*/
					if( param > 0 ) {
						if( param < 32 ) tick = speed = param;
						else set_tempo( param );
					}
					break;
				case 0x16: /* Pattern Loop.*/
					if( param == 0 ) /* Set loop marker on this channel. */
						channel.pl_row = row;
					if( channel.pl_row < row ) { /* Marker valid. Begin looping. */
						if( pl_count < 0 ) { /* Not already looping, begin. */
							pl_count = param;
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
				case 0x1E: /* Pattern Delay.*/
					tick = speed + speed * param;
					break;
			}
		}
		return song_end;
	}
}
