
package micromod;

public class Note {
	public int key, instrument, effect, parameter;

	private static final String  hexToString = "0123456789ABCDEF";
	private static final String  keyToString = "A-A#B-C-C#D-D#E-F-F#G-G#";
	private static final byte[]  stringToKey = { -2, 0, 1, 3, 5, 6, 8, 10 };
	private static final short[] keyToPeriod = { 1814, /*
		 C-0   C#0   D-0   D#0   E-0   F-0   F#0   G-0   G#0   A-1  A#1  B-1 */
		1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
		 856,  808,  762,  720,  678,  640,  604,  570,  538,  508, 480, 453,
		 428,  404,  381,  360,  339,  320,  302,  285,  269,  254, 240, 226,
		 214,  202,  190,  180,  170,  160,  151,  143,  135,  127, 120, 113,
		 107,  101,   95,   90,   85,   80,   75,   71,   67,   63,  60,  56,
		  53,   50,   47,   45,   42,   40,   37,   35,   33,   31,  30,  28,
		  26
	};
	private static final short[] fineTuning = {
		4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126,
		4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894
	};

	public int load( byte[] input, int offset ) {
		key = periodToKey( ( ( input[ offset ] & 0xF ) << 8 ) | ( input[ offset + 1 ] & 0xFF ) );
		instrument = ( input[ offset ] & 0x10 ) | ( ( input[ offset + 2 ] & 0xF0 ) >> 4 );
		effect = input[ offset + 2 ] & 0xF;
		parameter = input[ offset + 3 ] & 0xFF;
		return offset + 4;
	}
	
	public int save( byte[] output, int offset ) {
		if( output != null ) {
			int per = keyToPeriod( key, 0 );
			int ins = ( instrument > 0 && instrument < 32 ) ? instrument : 0;
			int fxc = ( effect >= 0 && effect < 16 ) ? effect : 0;
			int fxp = ( effect >= 0 && effect < 16 ) ? parameter : 0;
			output[ offset ] = ( byte ) ( ( ins & 0x10 ) | ( per >> 8 ) );
			output[ offset + 1 ] = ( byte ) per;
			output[ offset + 2 ] = ( byte ) ( ( ins << 4 ) | fxc );
			output[ offset + 3 ] = ( byte ) fxp;
		}
		return offset + 4;
	}

	public void fromString( String note ) {
		if( note.length() != 8 ) {
			throw new IllegalArgumentException( "Malformed note (incorrect length): " + note );
		}
		key = parseKey( note );
		instrument = numChar( note.charAt( 3 ), 10 ) * 10 + numChar( note.charAt( 4 ), 10 );
		effect = numChar( note.charAt( 5 ), 16 );
		parameter = ( numChar( note.charAt( 6 ), 16 ) << 4 ) + numChar( note.charAt( 7 ), 16 );
	}

	public String toString() {
		char[] note = new char[ 8 ];
		keyToChars( key, note );
		note[ 3 ] = ( instrument > 9 && instrument < 100 ) ? ( char ) ( '0' + instrument / 10 ) : '-';
		note[ 4 ] = ( instrument > 0 && instrument < 100 ) ? ( char ) ( '0' + instrument % 10 ) : '-';
		note[ 5 ] = ( effect > 0 && effect < 16 ) ? hexToString.charAt( effect ) : '-';
		note[ 6 ] = ( effect > 0 || parameter > 0 ) ? hexToString.charAt( ( parameter >> 4 ) & 0xF ) : '-';
		note[ 7 ] = ( effect > 0 || parameter > 0 ) ? hexToString.charAt( parameter & 0xF ) : '-';
		return new String( note );
	}

	/* Convert key to period, returns zero if key out of range. */
	public static int keyToPeriod( int key, int fineTune ) {
		int period = 0;
		if( key > 0 && key < 73 ) {
			period = ( keyToPeriod[ key ] * fineTuning[ ( fineTune + 8 ) & 0xF ] ) >> 11;
		}
		return ( period >> 1 ) + ( period & 1 );
	}

	public static int periodToKey( int period ) {
		int key = 0;
		if( period >= keyToPeriod[ 72 ] && period <= keyToPeriod[ 1 ] ) {
			while( keyToPeriod[ key + 12 ] > period ) key += 12;
			while( keyToPeriod[ key + 1 ] >= period ) key++;
			if( ( keyToPeriod[ key ] - period ) >= ( period - keyToPeriod[ key + 1 ] ) ) key++;
		}
		return key;
	}

	/* Adjust period from -13 to 60 semitones. */
	public static int transpose( int period, int semitones ) {
		period = period * keyToPeriod[ semitones + 13 ] * 2 / 856;
		return ( period >> 1 ) + ( period & 1 );
	}

	/* Key of the form "C-2", or 3-char note number such as "025". */
	public static int parseKey( String note ) {
		int key = 0;
		char chr = note.charAt( 0 );
		if( numChar( chr, 17 ) < 10 ) {
			/* Decimal note number. */
			key = numChar( chr, 10 ) * 100 + numChar( note.charAt( 1 ), 10 ) * 10 + numChar( note.charAt( 2 ), 10 );
		} else {
			/* Key string, "C-2", "C#2", etc. */
			key = stringToKey[ chr - 'A' ];
			chr = note.charAt( 1 );
			if( chr == '#' ) key++;
			if( keyToString.charAt( key * 2 + 5 ) != chr ) throw new IllegalArgumentException( "Invalid key: " + note );
			key += numChar( note.charAt( 2 ), 10 ) * 12;
		}
		return key;
	}

	public static String keyToString( int key ) {
		char[] chars = new char[ 3 ];
		keyToChars( key, chars );
		return new String( chars );
	}

	private static void keyToChars( int key, char[] out ) {
		out[ 0 ] = ( key > 0 && key < 118 ) ? keyToString.charAt( ( ( key + 2 ) % 12 ) * 2 ) : '-';
		out[ 1 ] = ( key > 0 && key < 118 ) ? keyToString.charAt( ( ( key + 2 ) % 12 ) * 2 + 1 ) : '-';
		out[ 2 ] = ( key > 0 && key < 118 ) ? ( char ) ( '0' + ( key + 2 ) / 12 ) : '-';
	}

	/* Digit of the form [0-9A-Z] or hyphen (0).*/
	private static int numChar( char chr, int radix ) {
		int value = 0;
		if( chr >= '0' && chr <= '9' ) {
			value = chr - '0';
		} else if( chr >= 'A' && chr <= 'Z' ) {
			value = chr + 10 - 'A';
		} else if( chr != '-' ) {
			throw new IllegalArgumentException( "Invalid character: " + chr );
		}
		if( value >= radix ) {
			throw new IllegalArgumentException( "Invalid character: " + chr );
		}
		return value;
	}
}
