
package mumart.micromod.s3m;

public class Module {
	public String song_name;
	public int num_channels, num_instruments;
	public int num_patterns, sequence_length;
	public int default_speed, default_tempo, default_g_vol, gain;
	public boolean fast_vol_slides;
	public int[] default_panning, sequence;
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
		default_g_vol = 64;
		gain = 32;
		default_panning = new int[ num_channels ];
		for( int idx = 0; idx < num_channels; idx++ ) default_panning[ idx ] = 128;
		sequence = new int[ 1 ];
		patterns = new Pattern[ 1 ];
		Pattern pattern = patterns[ 0 ] = new Pattern();
		pattern.data = new byte[ Pattern.NUM_ROWS * num_channels * 5 ];
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = instruments[ 1 ] = new Instrument();
	}

	/*
		S3M is an *appalling* file format.
	*/
	public Module( byte[] module ) {
		song_name = ascii( module, 0, 28 );
		sequence_length = ushortle( module, 32 );
		num_instruments = ushortle( module, 34 );
		num_patterns = ushortle( module, 36 );
		int flags = ushortle( module, 38 );
		int version = ushortle( module, 40 );
		fast_vol_slides = ( ( flags & 0x40 ) == 0x40 ) || version == 0x1300;
		boolean signed_samples = ushortle( module, 42 ) == 1;
		if( ushortle( module, 44 ) != 0x4353 )
			throw new IllegalArgumentException( "Not an S3M file!" );
		default_g_vol = module[ 48 ] & 0xFF;
		default_speed = module[ 49 ] & 0xFF;
		default_tempo = module[ 50 ] & 0xFF;
		gain = module[ 51 ] & 0x7F;
		boolean stereo_mode = ( module[ 51 ] & 0x80 ) == 0x80;
		boolean default_pan = ( module[ 53 ] & 0xFF ) == 0xFC;
		int[] channel_map = new int[ 32 ];
		for( int chan_idx = 0; chan_idx < 32; chan_idx++ ) {
			channel_map[ chan_idx ] = -1;
			if( ( module[ 64 + chan_idx ] & 0xFF ) < 16 )
				channel_map[ chan_idx ] = num_channels++;
		}
		sequence = new int[ sequence_length ];
		for( int seq_idx = 0; seq_idx < sequence_length; seq_idx++ )
			sequence[ seq_idx ] = module[ 96 + seq_idx ] & 0xFF;
		int module_idx = 96 + sequence_length;
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = new Instrument();
		for( int inst_idx = 1; inst_idx <= num_instruments; inst_idx++ ) {
			Instrument inst = new Instrument();
			instruments[ inst_idx ] = inst;
			int inst_offset = ushortle( module, module_idx ) << 4;
			module_idx += 2;
			inst.name = ascii( module, inst_offset + 48, 28 );
			if( module[ inst_offset ] != 1 ) continue;
			if( ushortle( module, inst_offset + 76 ) != 0x4353 ) continue;
			int sample_offset = ( module[ inst_offset + 13 ] & 0xFF ) << 20;
			sample_offset += ushortle( module, inst_offset + 14 ) << 4;
			int sample_length = intle( module, inst_offset + 16 );
			int loop_start = intle( module, inst_offset + 20 );
			int loop_length = intle( module, inst_offset + 24 ) - loop_start;
			inst.volume = module[ inst_offset + 28 ] & 0xFF;
			boolean packed = module[ inst_offset + 30 ] != 0;
			boolean loop_on = ( module[ inst_offset + 31 ] & 0x1 ) == 0x1;
			if( loop_start + loop_length > sample_length )
				loop_length = sample_length - loop_start;
			if( loop_length < 1 || !loop_on ) {
				loop_start = sample_length;
				loop_length = 0;
			}
			inst.loop_start = loop_start;
			inst.loop_length = loop_length;
			int loop_end = loop_start + loop_length;
			boolean stereo = ( module[ inst_offset + 31 ] & 0x2 ) == 0x2;
			boolean sixteen_bit = ( module[ inst_offset + 31 ] & 0x4 ) == 0x4;
			if( packed ) throw new IllegalArgumentException( "Packed samples not supported!" );
			inst.c2_rate = intle( module, inst_offset + 32 );
			short[] sample_data = new short[ loop_end + 1 ];
			convert_samples( module, sample_offset, sample_data, loop_end, signed_samples, sixteen_bit );
			sample_data[ loop_end ] = sample_data[ loop_start ];
			inst.sample_data_left = sample_data;
			if( stereo ) {
				sample_offset += sample_length + ( sixteen_bit ? sample_length : 0 );
				sample_data = new short[ loop_end + 1 ];
				convert_samples( module, sample_offset, sample_data, loop_end, signed_samples, sixteen_bit );
				sample_data[ loop_end ] = sample_data[ loop_start ];
			}
			inst.sample_data_right = sample_data;
		}
		patterns = new Pattern[ num_patterns ];
		for( int pat_idx = 0; pat_idx < num_patterns; pat_idx++ ) {
			Pattern pattern = patterns[ pat_idx ] = new Pattern();
			pattern.data = new byte[ num_channels * Pattern.NUM_ROWS * 5 ];
			int in_offset = ( ushortle( module, module_idx ) << 4 ) + 2;
			int row_idx = 0;
			while( row_idx < 64 ) {
				int token = module[ in_offset++ ] & 0xFF;
				if( token == 0 ) {
					row_idx++;
					continue;
				}
				int note_key = 0;
				int note_ins = 0;
				if( ( token & 0x20 ) == 0x20 ) { /* Key + Instrument.*/
					note_key = module[ in_offset++ ] & 0xFF;
					note_ins = module[ in_offset++ ] & 0xFF;
					if( note_key < 0xFE )
						note_key = ( note_key >> 4 ) * 12 + ( note_key & 0xF ) + 1;
					if( note_key == 0xFF ) note_key = 0;
				}
				int note_vol = 0;
				if( ( token & 0x40 ) == 0x40 ) /* Volume Column.*/
					note_vol = ( module[ in_offset++ ] & 0x7F ) + 0x10;
				int note_effect = 0;
				int note_param = 0;
				if( ( token & 0x80 ) == 0x80 ) { /* Effect + Param.*/
					note_effect = module[ in_offset++ ] & 0xFF;
					note_param = module[ in_offset++ ] & 0xFF;
				}
				int chan_idx = channel_map[ token & 0x1F ];
				if( chan_idx >= 0 ) {
					int note_offset = ( row_idx * num_channels + chan_idx ) * 5;
					pattern.data[ note_offset     ] = ( byte ) note_key;
					pattern.data[ note_offset + 1 ] = ( byte ) note_ins;
					pattern.data[ note_offset + 2 ] = ( byte ) note_vol;
					pattern.data[ note_offset + 3 ] = ( byte ) note_effect;
					pattern.data[ note_offset + 4 ] = ( byte ) note_param;
				}
			}
			module_idx += 2;
		}
		default_panning = new int[ num_channels ];
		for( int chan_idx = 0; chan_idx < 32; chan_idx++ ) {
			if( channel_map[ chan_idx ] < 0 ) continue;
			int panning = 7;
			if( stereo_mode ) {
				panning = 12;
				if( ( module[ 64 + chan_idx ] & 0xFF ) < 8 ) panning = 3;
			}
			if( default_pan ) {
				int pan_flags = module[ module_idx + chan_idx ] & 0xFF;
				if( ( pan_flags & 0x20 ) == 0x20 ) panning = pan_flags & 0xF;
			}
			default_panning[ channel_map[ chan_idx ] ] = panning * 17;
		}
	}

	public static void convert_samples( byte[] input, int offset, short[] output, int count, boolean signed, boolean sixteen_bit ) {
		if( sixteen_bit ) {
			if( signed ) {
				for( int idx = 0; idx < count; idx++ ) {
					output[ idx ] = ( short ) ( ( input[ offset ] & 0xFF ) | ( input[ offset + 1 ] << 8 ) );
					offset += 2;
				}
			} else {
				for( int idx = 0; idx < count; idx++ ) {
					int sam = ( input[ offset ] & 0xFF ) | ( ( input[ offset + 1 ] & 0xFF ) << 8 );
					output[ idx ] = ( short ) ( sam - 32768 );
					offset += 2;
				}
			}
		} else {
			if( signed ) {
				for( int idx = 0; idx < count; idx++ )
					output[ idx ] = ( short ) ( input[ offset++ ] << 8 );
			} else {
				for( int idx = 0; idx < count; idx++ )
					output[ idx ] = ( short ) ( ( ( input[ offset++ ] & 0xFF ) - 128 ) << 8 );
			}
		}
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
}
