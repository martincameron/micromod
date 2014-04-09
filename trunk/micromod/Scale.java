
package micromod;

public class Scale {
	public static final String
		CHROMATIC = "C#D#EF#G#A#B",
		C_MAJOR   = "C-D-EF-G-A-B",
		C_MINOR   = "C-D#-F-G#-#-";
	
	private byte[] keys = new byte[ 12 * 10 ];

	public Scale() {
		this( CHROMATIC );
	}

	public Scale( String scale ) {
		if( scale.length() != 12 ) {
			throw new IllegalArgumentException( "Scale has incorrect length: " + scale );
		}
		int noteIdx = 1;
		for( int idx = 1; idx < keys.length; idx++ ) {
			int chrIdx = ( idx - 1 ) % 12;
			char chr = scale.charAt( chrIdx );
			if( chr == CHROMATIC.charAt( chrIdx ) ) {
				keys[ idx ] = ( byte ) noteIdx++;
			} else if( chr != '-' ) {
				throw new IllegalArgumentException( "Invalid character in scale: " + scale );
			}
		}
	}
	
	public int getDistance( int srcKey, int destKey ) {
		return getNoteIdx( destKey ) - getNoteIdx( srcKey );
	}
	
	public int transpose( int key, int distance ) {
		int noteIdx = getNoteIdx( key );
		while( key > 0 && keys[ key ] != noteIdx + distance ) {
			key = ( key + ( distance > 0 ? 1 : -1 ) ) % keys.length;
		}
		return key;
	}
	
	private int getNoteIdx( int key ) {
		int noteIdx = keys[ key ];
		if( noteIdx == 0 ) {
			throw new IllegalArgumentException( "Key not in scale: " + Note.keyToString( key ) );
		}
		return noteIdx;
	}
}
