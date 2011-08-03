
package mumart.micromod.xm;

public class Module {
	public String song_name;
	public int num_channels, num_instruments;
	public int num_patterns, sequence_length, restart_pos;
	public int default_speed, default_tempo;
	public boolean linear_periods;
	public int[] sequence;
	public Pattern[] patterns;
	public Instrument[] instruments;

	public Module() {
		song_name = "Blank";
		num_channels = 4;
		num_instruments = 1;
		num_patterns = 1;
		sequence_length = 1;
		default_speed = 6;
		default_tempo = 125;
		sequence = new int[ 1 ];
		patterns = new Pattern[ 1 ];
		Pattern pattern = patterns[ 0 ] = new Pattern();
		pattern.num_rows = 64;
		pattern.data = new byte[ pattern.num_rows * num_channels * 5 ];
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = instruments[ 1 ] = new Instrument();
	}
	
	public Module( byte[] module_data ) {
		if( !is_xm( module_data ) )
			throw new IllegalArgumentException( "Not an XM file!" );
		if( ushortle( module_data, 58 ) != 0x0104 )
			throw new IllegalArgumentException( "XM format version must be 0x0104!" );
		song_name = ascii( module_data, 17, 20 );
		boolean delta_env = ascii( module_data, 38, 20 ).startsWith( "DigiBooster Pro" );
		int data_offset = 60 + intle( module_data, 60 );
		sequence_length = ushortle( module_data, 64 );
		restart_pos = ushortle( module_data, 66 );
		num_channels = ushortle( module_data, 68 );
		num_patterns = ushortle( module_data, 70 );
		num_instruments = ushortle( module_data, 72 );
		linear_periods = ( ushortle( module_data, 74 ) & 0x1 ) > 0;
		default_speed = ushortle( module_data, 76 );
		default_tempo = ushortle( module_data, 78 );
		sequence = new int[ sequence_length ];
		for( int seq_idx = 0; seq_idx < sequence_length; seq_idx++ ) {
			int entry = module_data[ 80 + seq_idx ] & 0xFF;
			sequence[ seq_idx ] = entry < num_patterns ? entry : 0;
		}
		patterns = new Pattern[ num_patterns ];
		for( int pat_idx = 0; pat_idx < num_patterns; pat_idx++ ) {
			Pattern pattern = patterns[ pat_idx ] = new Pattern();
			if( module_data[ data_offset + 4 ] != 0 )
				throw new IllegalArgumentException( "Unknown pattern packing type!" );
			pattern.num_rows = ushortle( module_data, data_offset + 5 );
			int pattern_data_length = ushortle( module_data, data_offset + 7 );
			int num_notes = pattern.num_rows * num_channels;
			pattern.data = new byte[ num_notes * 5 ];
			data_offset += intle( module_data, data_offset );
			int next_offset = data_offset + pattern_data_length;
			if( pattern_data_length > 0 ) {
				int pattern_data_offset = 0;
				for( int note = 0; note < num_notes; note++ ) {
					int flags = module_data[ data_offset ];
					if( ( flags & 0x80 ) == 0 ) flags = 0x1F; else data_offset++;
					for( int n = 0; n < 5; n++ ) {
						byte b = ( flags & 1 ) > 0 ? module_data[ data_offset++ ] : 0;
						pattern.data[ pattern_data_offset++ ] = b;
						flags = flags >> 1;
					}
				}
			}
			data_offset = next_offset;
		}
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = new Instrument();
		for( int ins_idx = 1; ins_idx <= num_instruments; ins_idx++ ) {
			Instrument instrument = instruments[ ins_idx ] = new Instrument();
			instrument.name = ascii( module_data, data_offset + 4, 22 );
			int num_samples = instrument.num_samples = ushortle( module_data, data_offset + 27 );
			if( num_samples > 0 ) {
				instrument.samples = new Sample[ num_samples ];
				for( int key_idx = 0; key_idx < 96; key_idx++ )
					instrument.key_to_sample[ key_idx + 1 ] = module_data[ data_offset + 33 + key_idx ] & 0xFF;
				Envelope vol_env = instrument.volume_envelope = new Envelope();
				vol_env.points_tick = new int[ 12 ];
				vol_env.points_ampl = new int[ 12 ];
				int point_tick = 0;
				for( int point = 0; point < 12; point++ ) {
					int point_offset = data_offset + 129 + ( point * 4 );
					point_tick = ( delta_env ? point_tick : 0 ) + ushortle( module_data, point_offset );
					vol_env.points_tick[ point ] = point_tick;
					vol_env.points_ampl[ point ] = ushortle( module_data, point_offset + 2 );
				}
				Envelope pan_env = instrument.panning_envelope = new Envelope();
				pan_env.points_tick = new int[ 12 ];
				pan_env.points_ampl = new int[ 12 ];
				point_tick = 0;
				for( int point = 0; point < 12; point++ ) {
					int point_offset = data_offset + 177 + ( point * 4 );
					point_tick = ( delta_env ? point_tick : 0 ) + ushortle( module_data, point_offset );
					pan_env.points_tick[ point ] = point_tick;
					pan_env.points_ampl[ point ] = ushortle( module_data, point_offset + 2 );
				}
				vol_env.num_points = module_data[ data_offset + 225 ] & 0xFF;
				if( vol_env.num_points > 12 ) vol_env.num_points = 0;
				pan_env.num_points = module_data[ data_offset + 226 ] & 0xFF;
				if( pan_env.num_points > 12 ) pan_env.num_points = 0;
				vol_env.sustain_tick = vol_env.points_tick[ module_data[ data_offset + 227 ] ];
				vol_env.loop_start_tick = vol_env.points_tick[ module_data[ data_offset + 228 ] ];
				vol_env.loop_end_tick = vol_env.points_tick[ module_data[ data_offset + 229 ] ];
				pan_env.sustain_tick = pan_env.points_tick[ module_data[ data_offset + 230 ] ];
				pan_env.loop_start_tick = pan_env.points_tick[ module_data[ data_offset + 231 ] ];
				pan_env.loop_end_tick = pan_env.points_tick[ module_data[ data_offset + 232 ] ];
				vol_env.enabled = vol_env.num_points > 0 && ( module_data[ data_offset + 233 ] & 0x1 ) > 0;
				vol_env.sustain = ( module_data[ data_offset + 233 ] & 0x2 ) > 0;
				vol_env.looped = ( module_data[ data_offset + 233 ] & 0x4 ) > 0;
				pan_env.enabled = pan_env.num_points > 0 && ( module_data[ data_offset + 234 ] & 0x1 ) > 0;
				pan_env.sustain = ( module_data[ data_offset + 234 ] & 0x2 ) > 0;
				pan_env.looped = ( module_data[ data_offset + 234 ] & 0x4 ) > 0;
				instrument.vibrato_type = module_data[ data_offset + 235 ] & 0xFF;
				instrument.vibrato_sweep = module_data[ data_offset + 236 ] & 0xFF;
				instrument.vibrato_depth = module_data[ data_offset + 237 ] & 0xFF;
				instrument.vibrato_rate = module_data[ data_offset + 238 ] & 0xFF;
				instrument.volume_fade_out = ushortle( module_data, data_offset + 239 );
			}
			data_offset += intle( module_data, data_offset );
			int sample_header_offset = data_offset;
			data_offset += num_samples * 40;
			for( int sam_idx = 0; sam_idx < num_samples; sam_idx++ ) {
				Sample sample = instrument.samples[ sam_idx ] = new Sample();
				int sample_data_bytes = intle( module_data, sample_header_offset );
				int sample_loop_start = intle( module_data, sample_header_offset + 4 );
				int sample_loop_length = intle( module_data, sample_header_offset + 8 );
				sample.volume = module_data[ sample_header_offset + 12 ];
				sample.fine_tune = module_data[ sample_header_offset + 13 ];
				boolean looped = ( module_data[ sample_header_offset + 14 ] & 0x3 ) > 0;
				boolean ping_pong = ( module_data[ sample_header_offset + 14 ] & 0x2 ) > 0;
				boolean sixteen_bit = ( module_data[ sample_header_offset + 14 ] & 0x10 ) > 0;
				sample.panning = module_data[ sample_header_offset + 15 ] & 0xFF;
				sample.rel_note = module_data[ sample_header_offset + 16 ];
				sample.name = ascii( module_data, sample_header_offset + 18, 22 );
				sample_header_offset += 40;
				int sample_data_length = sample_data_bytes;
				if( sixteen_bit ) {
					sample_data_length /= 2;
					sample_loop_start /= 2;
					sample_loop_length /=2;
				}
				if( !looped || ( sample_loop_start + sample_loop_length ) > sample_data_length ) {
					sample_loop_start = sample_data_length;
					sample_loop_length = 0;
				}
				short[] sample_data = new short[ sample_data_length ];
				if( sixteen_bit ) {
					short ampl = 0;
					for( int out_idx = 0; out_idx < sample_data_length; out_idx++ ) {
						int in_idx = data_offset + out_idx * 2;
						ampl += module_data[ in_idx ] & 0xFF;
						ampl += ( module_data[ in_idx + 1 ] & 0xFF ) << 8;
						sample_data[ out_idx ] = ampl;
					}
				} else {
					byte ampl = 0;
					for( int out_idx = 0; out_idx < sample_data_length; out_idx++ ) {
						ampl += module_data[ data_offset + out_idx ] & 0xFF;
						sample_data[ out_idx ] = ( short ) ( ampl << 8 );
					}
				}
				sample.set_sample_data( sample_data, sample_loop_start, sample_loop_length, ping_pong );
				data_offset += sample_data_bytes;
			}
		}
	}
	
	/* Return true if the specified module data is in XM format. */
	public static boolean is_xm( byte[] module_data ) {
		return ascii( module_data, 0, 17 ).equals( "Extended Module: " );
	}

	private static int ushortle( byte[] buf, int offset ) {
		return ( buf[ offset ] & 0xFF ) | ( ( buf[ offset + 1 ] & 0xFF ) << 8 ) ;
	}
	
	private static int intle( byte[] buf, int offset ) {
		int value = buf[ offset ] & 0xFF;
		value  |= ( buf[ offset + 1 ] & 0xFF ) << 8;
		value  |= ( buf[ offset + 2 ] & 0xFF ) << 16;
		value  |= ( buf[ offset + 3 ] & 0x7F ) << 24;
		return value;
	}

	private static String ascii( byte[] buf, int offset, int len ) {
		char[] str = new char[ len ];
		for( int idx = 0; idx < len; idx++ ) {
			int c = buf[ offset + idx ] & 0xFF;
			str[ idx ] = c < 32 ? 32 : ( char ) c;
		}
		return new String( str );
	}
	
	public void toStringBuffer( StringBuffer out ) {
		out.append( "Song Name: " + song_name + '\n'
			+ "Num Channels: " + num_channels + '\n'
			+ "Num Instruments: " + num_instruments + '\n'
			+ "Num Patterns: " + num_patterns + '\n'
			+ "Sequence Length: " + sequence_length + '\n'
			+ "Restart Pos: " + restart_pos + '\n'
			+ "Default Speed: " + default_speed + '\n'
			+ "Default Tempo: " + default_tempo + '\n'
			+ "Linear Periods: " + linear_periods + '\n' );
		out.append( "Sequence: " );
		for( int seq_idx = 0; seq_idx < sequence.length; seq_idx++ )
			out.append( sequence[ seq_idx ] + ", " );
		out.append( '\n' );
		for( int pat_idx = 0; pat_idx < patterns.length; pat_idx++ ) {
			out.append( "Pattern " + pat_idx + ":\n" );
			patterns[ pat_idx ].toStringBuffer( out );
		}
		for( int ins_idx = 1; ins_idx < instruments.length; ins_idx++ ) {
			out.append( "Instrument " + ins_idx + ":\n" );
			instruments[ ins_idx ].toStringBuffer( out );
		}
	}
}
