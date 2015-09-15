
package micromod;

public class Pattern {
	public static final int NUM_ROWS = 64;
	private int numChannels;
	private byte[] patternData;
	
	public Pattern( int numChannels ) {
		this.numChannels = numChannels;
		patternData = new byte[ NUM_ROWS * numChannels * 4 ];
	}

	public Pattern( int numChannels, Pattern pattern ) {
		this( numChannels );
		int numChan = ( numChannels < pattern.numChannels ) ? numChannels : pattern.numChannels;
		Note note = new Note();
		for( int rowIdx = 0; rowIdx < NUM_ROWS; rowIdx++ ) {
			for( int chanIdx = 0; chanIdx < numChan; chanIdx++ ) {
				/* Copy from other pattern. */
				pattern.getNote( rowIdx, chanIdx, note );
				setNote( rowIdx, chanIdx, note );
			}
		}
	}
	
	public int getNumChannels() {
		return numChannels;
	}

	public void getNote( int row, int channel, Note note ) {
		if( row < 0 || row >= NUM_ROWS ) {
			throw new IllegalArgumentException( "Row out of range (0 to " + ( NUM_ROWS - 1 ) + "): " + row );
		}
		if( channel < 0 || channel >= numChannels ) {
			throw new IllegalArgumentException( "Channel out of range (0 to " + ( numChannels - 1 ) + "): " + channel );
		}
		int patternDataIdx = ( row * numChannels + channel ) * 4;
		note.key = patternData[ patternDataIdx ] & 0xFF;
		note.instrument = patternData[ patternDataIdx + 1 ] & 0xFF;
		note.effect = patternData[ patternDataIdx + 2 ] & 0xFF;
		note.parameter = patternData[ patternDataIdx + 3 ] & 0xFF;
	}
	
	public void setNote( int row, int channel, Note note ) {
		if( row < 0 || row >= NUM_ROWS ) {
			throw new IllegalArgumentException( "Row out of range (0 to " + ( NUM_ROWS - 1 ) + "): " + row );
		}
		if( channel < 0 || channel >= numChannels ) {
			throw new IllegalArgumentException( "Channel out of range (0 to " + ( numChannels - 1 ) + "): " + channel );
		}
		int patternDataIdx = ( row * numChannels + channel ) * 4;
		patternData[ patternDataIdx ] = ( byte ) note.key;
		patternData[ patternDataIdx + 1 ] = ( byte ) note.instrument;
		patternData[ patternDataIdx + 2 ] = ( byte ) note.effect;
		patternData[ patternDataIdx + 3 ] = ( byte ) note.parameter;
	}
	
	public int load( byte[] input, int offset ) {
		Note note = new Note();
		for( int rowIdx = 0; rowIdx < NUM_ROWS; rowIdx++ ) {
			for( int chanIdx = 0; chanIdx < numChannels; chanIdx++ ) {
				offset = note.load( input, offset );
				setNote( rowIdx, chanIdx, note );
			}
		}
		return offset;
	}
	
	public int save( byte[] output, int offset ) {
		Note note = new Note();
		for( int rowIdx = 0; rowIdx < NUM_ROWS; rowIdx++ ) {
			for( int chanIdx = 0; chanIdx < numChannels; chanIdx++ ) {
				getNote( rowIdx, chanIdx, note );
				offset = note.save( output, offset );
			}
		}
		return offset;
	}
}
