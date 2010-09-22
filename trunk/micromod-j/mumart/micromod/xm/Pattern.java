
package mumart.micromod.xm;

public class Pattern {
	public int num_rows;
	public byte[] data;

	public void get_note( int index, Note note ) {
		int offset = index * 5;
		note.key = data[ offset ] & 0xFF;
		note.instrument = data[ offset + 1 ] & 0xFF;
		note.volume = data[ offset + 2 ] & 0xFF;
		note.effect = data[ offset + 3 ] & 0xFF;
		note.param = data[ offset + 4 ] & 0xFF;
	}

	public void toStringBuffer( StringBuffer out ) {
		char[] hex = {
			'0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		int channels = data.length / ( num_rows * 5 );
		int data_offset = 0;
		for( int row = 0; row < num_rows; row++ ) {
			for( int channel = 0; channel < channels; channel++ ) {
				for( int n = 0; n < 5; n++ ) {
					int b = data[ data_offset++ ];
					if( b == 0 ) {
						out.append( "--" );
					} else {
						out.append( hex[ ( b >> 4 ) & 0xF ] );
						out.append( hex[ b & 0xF ] );
					}
				}
				out.append( ' ' );
			}
			out.append( '\n' );
		}
	}
}
