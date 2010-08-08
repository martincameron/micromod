
package micromod;

public class Module {
	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	public String song_name;
	public int num_channels, num_instruments, num_patterns;
	public int sequence_length, restart_pos, c2_rate, gain;
	public byte[] patterns, sequence;
	public Instrument[] instruments;

	public Module() {
		song_name = "Blank";
		num_channels = 4;
		num_instruments = 1;
		num_patterns = 1;
		sequence_length = 1;
		c2_rate = C2_PAL;
		gain = 64;
		patterns = new byte[ 64 * 4 * num_channels ];
		sequence = new byte[ 1 ];
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = instruments[ 1 ] = new Instrument();
	}

	public Module( byte[] module ) {
		song_name = ascii( module, 0, 20 );
		sequence_length = module[ 950 ] & 0x7F;
		restart_pos = module[ 951 ] & 0x7F;
		if( restart_pos >= sequence_length ) restart_pos = 0;
		sequence = new byte[ 128 ];
		for( int seq_idx = 0; seq_idx < 128; seq_idx++ ) {
			int pat_idx = module[ 952 + seq_idx ] & 0x7F;
			sequence[ seq_idx ] = ( byte ) pat_idx;
			if( pat_idx >= num_patterns ) num_patterns = pat_idx + 1;
		}
		switch( ushortbe( module, 1082 ) ) {
			case 0x4b2e: /* M.K. */
			case 0x4b21: /* M!K! */
			case 0x5434: /* FLT4 */
				num_channels = 4;
				c2_rate = C2_PAL;
				gain = 64;
				break;
			case 0x484e: /* xCHN */
				num_channels = module[ 1080 ] - 48;
				c2_rate = C2_NTSC;
				gain = 32;
				break;
			case 0x4348: /* xxCH */
				num_channels  = ( module[ 1080 ] - 48 ) * 10;
				num_channels += module[ 1081 ] - 48;
				c2_rate = C2_NTSC;
				gain = 32;
				break;
			default:
				throw new IllegalArgumentException( "MOD Format not recognised!" );
		}
		int num_notes = num_patterns * 64 * num_channels;
		patterns = new byte[ num_notes * 4 ];
		System.arraycopy( module, 1084, patterns, 0, num_notes * 4 );
		num_instruments = 31;
		instruments = new Instrument[ num_instruments + 1 ];
		instruments[ 0 ] = new Instrument();
		int mod_idx = 1084 + num_notes * 4;
		for( int inst_idx = 1; inst_idx <= num_instruments; inst_idx++ ) {
			Instrument inst = new Instrument();
			inst.name = ascii( module, inst_idx * 30 - 10, 22 );
			int sample_length = ushortbe( module, inst_idx * 30 + 12 ) * 2;
			inst.fine_tune = module[ inst_idx * 30 + 14 ] & 0xF;
			inst.volume = module[ inst_idx * 30 + 15 ] & 0x7F;
			if( inst.volume > 64 ) inst.volume = 64;
			int loop_start = ushortbe( module, inst_idx * 30 + 16 ) * 2;
			int loop_length = ushortbe( module, inst_idx * 30 + 18 ) * 2;
			byte[] sample_data = new byte[ sample_length + 1 ];
			if( mod_idx + sample_length > module.length )
				sample_length = module.length - mod_idx;
			System.arraycopy( module, mod_idx, sample_data, 0, sample_length );
			mod_idx += sample_length;
			if( loop_start + loop_length > sample_length )
				loop_length = sample_length - loop_start;
			if( loop_length < 4 ) {
				loop_start = sample_length;
				loop_length = 0;
			}
			sample_data[ loop_start + loop_length ] = sample_data[ loop_start ];
			inst.loop_start = loop_start;
			inst.loop_length = loop_length;
			inst.sample_data = sample_data;
			instruments[ inst_idx ] = inst;
		}
	}

	private static int ushortbe( byte[] buf, int offset ) {
		return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
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
