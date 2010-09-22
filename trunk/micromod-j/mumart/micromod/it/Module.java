
package mumart.micromod.it;

public class Module {
	public String song_name, message;
	public int num_channels, num_instruments;
	public int num_patterns, sequence_length, restart_pos;
	public int default_speed, default_tempo, default_global_vol, gain;
	public boolean linear_periods;
	public int[] sequence, default_volume, default_panning;
	public Pattern[] patterns;
	//public Instrument[] instruments;

	public Module( byte[] module_data ) {
		if( !ascii( module_data, 0, 4 ).equals( "IMPM" ) )
			throw new IllegalArgumentException( "Not an Impulse Tracker module!" );
		num_channels = 64;
		song_name = ascii( module_data, 4, 26 );
		int seq_entries = ushortle( module_data, 32 );
		num_instruments = ushortle( module_data, 34 );
		int num_samples = ushortle( module_data, 36 );
		num_patterns = ushortle( module_data, 38 );
/*
		if( ushortle( module_data, 42 ) != 0x214 )
			throw new IllegalArgumentException( "IT format version must be 0x0214!" );
*/
		int flags = ushortle( module_data, 44 );
		boolean stereo = ( flags & 0x01 ) > 0;
		boolean instrument_mode = ( flags & 0x04 ) > 0;
		if( !instrument_mode )
			throw new IllegalArgumentException( "Sample mode not supported!" );
		linear_periods = ( flags & 0x08 ) > 0;
		if( !linear_periods )
			throw new IllegalArgumentException( "Logarithmic period table not supported!" );
		boolean old_fx = ( flags & 0x10 ) > 0;
		boolean compatible_gxx = ( flags & 0x20 ) > 0;
		default_global_vol = module_data[ 48 ] & 0xFF;
		gain = module_data[ 49 ] & 0xFF;
		default_speed = module_data[ 50 ] & 0xFF;
		default_tempo = module_data[ 51 ] & 0xFF;
		int stereo_separation = module_data[ 52 ] & 0xFF;
		int message_length = ushortle( module_data, 54 );
		int message_offset = intle( module_data, 56 );
		message = ascii( module_data, message_offset, message_length );
		default_volume = new int[ 64 ];
		default_panning = new int[ 64 ];
		for( int idx = 0; idx < 64; idx++ ) {
			int pan = module_data[ 64 + idx ] & 0xFF;
			int vol = module_data[ 128 + idx ] & 0xFF;
			default_volume[ idx ] = ( pan < 128 ) ? vol : 0;
			default_panning[ idx ] = ( pan <= 64 ) ? pan : 32;
		}
		sequence = new int[ seq_entries ];
		for( int entry_idx = 0; entry_idx < seq_entries; entry_idx++ ) {
			int seq_idx = module_data[ 192 + entry_idx ] & 0xFF;
			if( seq_idx == 0xFF ) break;
			if( seq_idx >= num_patterns ) continue;
			sequence[ sequence_length++ ] = seq_idx;
		}
		int data_offset = 192 + seq_entries;
		for( int ins_idx = 0; ins_idx < num_instruments; ins_idx++ ) {
			//fixme
			data_offset += 4;
		}
		for( int sam_idx = 0; sam_idx < num_samples; sam_idx++ ) {
			//fixme
			data_offset += 4;
		}
		patterns = new Pattern[ num_patterns ];
		for( int pat_idx = 0; pat_idx < num_patterns; pat_idx++ ) {
			int pat_offset = intle( module_data, data_offset );
			if( pat_offset == 0 ) {
				Pattern pattern = new Pattern();
				pattern.rows = 64;
				pattern.data = new byte[ 64 * num_channels * 5 ];
				patterns[ pat_idx ] = pattern;
			} else {
				patterns[ pat_idx ] = unpack_it_pattern( module_data, pat_offset );
			}
			data_offset += 4;
		}
	}
	
	private static Pattern unpack_it_pattern( byte[] input, int offset ) {
		/* Euurgh. */
		Pattern pattern = new Pattern();
		/* int pattern_data_length = ushortle( input, offset ); */
		int num_rows = pattern.rows = ushortle( input, offset + 2 );
		byte[] data = pattern.data = new byte[ num_rows * 64 * 5 ];
		byte[] notes = new byte[ 64 * 6 ];
		offset += 8;
		int row_idx = 0;
		while( row_idx < num_rows ) {
			int token = input[ offset++ ];
			if( token == 0 ) {
				row_idx++;
				continue;
			}
			int chan_idx = ( token & 0x7F ) - 1;
			int notes_idx = chan_idx * 6;
			if( ( token & 0x80 ) > 0 ) notes[ notes_idx ] = input[ offset++ ];
			int data_idx = ( row_idx * 64 + chan_idx ) * 5;
			int mask = notes[ notes_idx ];
			if( ( mask & 0x01 ) > 0 ) /* Key */
				data[ data_idx ] = notes[ notes_idx + 1 ] = input[ offset++ ];
			if( ( mask & 0x02 ) > 0 ) /* Ins */
				data[ data_idx + 1 ] = notes[ notes_idx + 2 ] = input[ offset++ ];
			if( ( mask & 0x04 ) > 0 ) /* Vol */
				data[ data_idx + 2 ] = notes[ notes_idx + 3 ] = input[ offset++ ];
			if( ( mask & 0x08 ) > 0 ) { /* Effect + Param */
				data[ data_idx + 3 ] = notes[ notes_idx + 4 ] = input[ offset++ ];
				data[ data_idx + 4 ] = notes[ notes_idx + 5 ] = input[ offset++ ];
			}
			if( ( mask & 0x10 ) > 0 ) /* Key same as last note. */
				data[ data_idx ] = notes[ notes_idx + 1 ];
			if( ( mask & 0x20 ) > 0 ) /* Ins same as last note. */
				data[ data_idx + 1 ] = notes[ notes_idx + 2 ];
			if( ( mask & 0x40 ) > 0 ) /* Vol same as last note. */
				data[ data_idx + 2 ] = notes[ notes_idx + 3 ];
			if( ( mask & 0x80 ) > 0 ) { /* Effect + Param same as last note. */
				data[ data_idx + 3 ] = notes[ notes_idx + 4 ];
				data[ data_idx + 4 ] = notes[ notes_idx + 5 ];
			}
		}
		return pattern;
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
			+ "Default Global Vol: " + default_global_vol + '\n'
			+ "Gain: " + gain + '\n'
			+ "Linear Periods: " + linear_periods + '\n' );
			
		out.append( "Default Volume: " );
		for( int vol_idx = 0; vol_idx < default_volume.length; vol_idx++ )
			out.append( default_volume[ vol_idx ] + ", " );
		out.append( '\n' );
		
		out.append( "Default Panning: " );
		for( int pan_idx = 0; pan_idx < default_panning.length; pan_idx++ )
			out.append( default_panning[ pan_idx ] + ", " );
		out.append( '\n' );
			
		out.append( "Sequence: " );
		for( int seq_idx = 0; seq_idx < sequence_length; seq_idx++ )
			out.append( sequence[ seq_idx ] + ", " );
		out.append( '\n' );
		/*
		for( int pat_idx = 0; pat_idx < patterns.length; pat_idx++ ) {
			out.append( "Pattern " + pat_idx + ":\n" );
			patterns[ pat_idx ].toStringBuffer( out );
		}
		for( int ins_idx = 1; ins_idx < instruments.length; ins_idx++ ) {
			out.append( "Instrument " + ins_idx + ":\n" );
			instruments[ ins_idx ].toStringBuffer( out );
		}
		*/
		out.append( "Message:\n" );
		out.append( message );
		out.append( '\n' );
	}
	
	public static void main( String[] args ) throws Exception {
		// Load data into array.
		java.io.File mod_file = new java.io.File( args[ 0 ] );
		byte[] module_data = new byte[ ( int ) mod_file.length() ];
		java.io.DataInputStream data_stream = new java.io.DataInputStream( new java.io.FileInputStream( mod_file ) );
		data_stream.readFully( module_data );
		data_stream.close();
		
		Module module = new Module( module_data );
		
		StringBuffer sb = new StringBuffer();
		module.toStringBuffer( sb );
		System.out.println( sb );
	}
}
