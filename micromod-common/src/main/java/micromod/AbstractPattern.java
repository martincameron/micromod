package micromod;

public abstract class AbstractPattern {
	private final int numAttributes;
	protected final int numChannels;
	protected byte[] patternData;

	public AbstractPattern( int numChannels, int numRows, int numAttributes ) {
		patternData = new byte[ numChannels * numRows * numAttributes ];
		this.numChannels = numChannels;
		this.numAttributes = numAttributes;
	}

	public abstract int getNumRows();
	public abstract void getNote( int index, int channel, Note note );
	public abstract void setNote( int index, int channel, Note note );

	public int getNumAttributes() {
		return numAttributes;
	}

	public int getNumChannels() {
		return numChannels;
	}

	public void toStringBuffer( StringBuffer out ) {
		char[] hex = {
				'0', '1', '2', '3', '4', '5', '6', '7',
				'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		int channels = patternData.length / ( getNumRows() * getNumAttributes() );
		int data_offset = 0;
		for( int row = 0; row < getNumRows(); row++ ) {
			for( int channel = 0; channel < channels; channel++ ) {
				for( int n = 0; n < getNumAttributes(); n++ ) {
					int b = patternData[ data_offset++ ];
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
