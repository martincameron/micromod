
package micromod;

public class Note {
	public int key, instrument, effect, parameter;

	private static final short[] keyToPeriod = { 1814,
	/*   C-0   C#0   D-0   D#0   E-0   F-0   F#0   G-0   G#0   A-0  A#0  B-0 */
		1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
		 856,  808,  762,  720,  678,  640,  604,  570,  538,  508, 480, 453,
		 428,  404,  381,  360,  339,  320,  302,  285,  269,  254, 240, 226,
		 214,  202,  190,  180,  170,  160,  151,  143,  135,  127, 120, 113,
		 107,  101,   95,   90,   85,   80,   75,   71,   67,   63,  60,  56,
		  53,   50,   47,   45,   42,   40,   37,   35,   33,   31,  30,  28,
		  26
	};

	public int getPeriod() {
		int period = 0;
		if( key >= 1 && key <= 72 ) {
			period = keyToPeriod[ key ];
		}
		return period;
	}

	public int load( byte[] input, int offset ) {
		int period = ( ( input[ offset ] & 0xF ) << 8 ) | ( input[ offset + 1 ] & 0xFF );
		key = 0;
		if( period >= keyToPeriod[ 72 ] && period <= keyToPeriod[ 1 ] ) {
			/* Convert period to key.*/
			while( keyToPeriod[ key + 12 ] >= period ) key += 12;
			while( keyToPeriod[ key + 1 ] >= period ) key++;
			if( ( keyToPeriod[ key ] - period ) >= ( period - keyToPeriod[ key + 1 ] ) ) key++;
		}
		instrument = ( input[ offset ] & 0x10 ) | ( ( input[ offset + 2 ] & 0xF0 ) >> 4 );
		effect = input[ offset + 2 ] & 0xF;
		parameter = input[ offset + 3 ] & 0xFF;
		return offset + 4;
	}
	
	public int save( byte[] output, int offset ) {
		if( output != null ) {
			int per = getPeriod();
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
}
