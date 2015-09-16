package micromod;

public abstract class AbstractModule<PATTERN extends AbstractPattern, INSTRUMENT> {
	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	private String songName;
	private int c2Rate, gain;
	private int[] sequence;

	public String getSongName() {
		return songName;
	}

	public void setSongName( String name ) {
		if( name == null ) {
			songName = "";
		} else if( name.length() > 20 ) {
			songName = name.substring( 0, 20 );
		} else {
			songName = name;
		}
	}

	public abstract int getNumChannels();

	public int getC2Rate() {
		return c2Rate;
	}

	public void setC2Rate(int c2Rate) {
		if( c2Rate < 1 || c2Rate > 65536 ) {
			throw new IllegalArgumentException( "C2Rate out of range (1 to 65536): " + c2Rate );
		}
		this.c2Rate = c2Rate;
	}

	public int getGain() {
		return gain;
	}

	public void setGain(int gain) {
		if( gain < 0 || gain > 512 ) {
			throw new IllegalArgumentException( "Gain out of range (0 to 512): " + gain );
		}
		this.gain = gain;
	}

	public void initSequence( byte maxIdx ) {
		if( maxIdx < 0 ) {
			throw new IllegalArgumentException( "Song max index out of range (0 to " + Byte.MAX_VALUE + " ): " + maxIdx );
		}
		sequence = new int[maxIdx + 1];
	}

	public void initSequence( short maxIdx ) {
		if( maxIdx < 0 ) {
			throw new IllegalArgumentException( "Song max index out of range (0 to " + Short.MAX_VALUE + "): " + maxIdx );
		}
		sequence = new int[maxIdx + 1];
	}

	public int getSequenceLength() {
		return sequence.length;
	}

	public int getSequenceEntry(int seqIdx) {
		if( seqIdx < 0 || seqIdx > sequence.length ) {
			throw new IllegalArgumentException( "Sequence index out of range (0 to " + sequence.length + "): " + seqIdx );
		}
		return sequence[ seqIdx ];
	}

	public void setSequenceEntry(int seqIdx, int patIdx) {
		getSequenceEntry( seqIdx );
		if( patIdx < 0 || patIdx > sequence.length ) {
			throw new IllegalArgumentException( "Pattern index out of range (0 to " + sequence.length + "): " + patIdx );
		}
		sequence[ seqIdx ] = ( byte ) patIdx;
	}

	public abstract int getNumInstruments();

	public abstract int getNumPatterns();

	public abstract void initPatterns( int size );

	public abstract PATTERN getPattern( int patIdx );

	public abstract void setPattern( int patIdx, PATTERN pattern );

	public abstract void initInstruments( int size );

	public abstract INSTRUMENT getInstrument( int instIdx );

	public abstract void setInstrument( int instIdx, INSTRUMENT instrument );
}
