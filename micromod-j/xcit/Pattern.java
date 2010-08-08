
package xcit;

public class Pattern {
	public int rows;
	public byte[] data;
	
	public void toStringBuffer( StringBuffer out ) {
		char[] hex = {
			'0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		int channels = data.length / ( rows * 5 );
		int data_offset = 0;
		for( int row = 0; row < rows; row++ ) {
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
