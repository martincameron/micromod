
package micromod;

public class Module {
	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	private String songName;
	private int sequenceLength, restartPos, c2Rate, gain;
	private byte[] sequence;
	private Pattern[] patterns;
	private Instrument[] instruments;

	public Module() {
		setSongName( "Untitled" );
		sequence = new byte[ 128 ];
		patterns = new Pattern[ 128 ];
		setSequenceLength( 1 );
		setNumChannels( 4 );
		instruments = new Instrument[ 32 ];
		for( int instIdx = 1; instIdx < instruments.length; instIdx++ ) {
			instruments[ instIdx ] = new Instrument();
		}
	}

	public Module( java.io.InputStream inputStream ) throws java.io.IOException {
		this();
		load( inputStream );
	}

	public Module( byte[] module ) {
		this();
		load( module );
	}

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

	public int getNumChannels() {
		return patterns[ 0 ].getNumChannels();
	}

	public void setNumChannels( int numChannels ) {
		if( numChannels < 1 || numChannels > 32 ) {
			throw new IllegalArgumentException( "Number of channels out of range (1 to 32): " + numChannels );
		}
		setC2Rate( numChannels == 4 ? C2_PAL : C2_NTSC );
		setGain( numChannels == 4 ? 64 : 32 );
		for( int patIdx = 0; patIdx < patterns.length; patIdx++ ) {
			Pattern pattern = patterns[ patIdx ];
			if( pattern != null ) {
				patterns[ patIdx ] = new Pattern( numChannels, pattern );
			}
		}
		if( patterns[ 0 ] == null ) {
			patterns[ 0 ] = new Pattern( numChannels );
		}
	}

	public int getSequenceLength() {
		return sequenceLength;
	}
	
	public void setSequenceLength( int sequenceLength ) {
		if( sequenceLength < 1 || sequenceLength > 128 ) {
			throw new IllegalArgumentException( "Song length out of range (1 to 128): " + sequenceLength );
		}
		this.sequenceLength = sequenceLength;
	}
	
	public int getC2Rate() {
		return c2Rate;
	}
	
	public void setC2Rate( int c2Rate ) {
		if( c2Rate < 1 || c2Rate > 65536 ) {
			throw new IllegalArgumentException( "C2Rate out of range (1 to 65536): " + c2Rate );
		}
		this.c2Rate = c2Rate;
	}
	
	public int getGain() {
		return gain;
	}
	
	public void setGain( int gain ) {
		if( gain < 0 || gain > 512 ) {
			throw new IllegalArgumentException( "Gain out of range (0 to 512): " + gain );
		}
		this.gain = gain;
	}

	public int getSequenceEntry( int seqIdx ) {
		if( seqIdx < 0 || seqIdx > 127 ) {
			throw new IllegalArgumentException( "Sequence index out of range (0 to 127): " + seqIdx );
		}
		return sequence[ seqIdx ];
	}
	
	public void setSequenceEntry( int seqIdx, int patIdx ) {
		getSequenceEntry( seqIdx );
		if( patIdx < 0 || patIdx > 127 ) {
			throw new IllegalArgumentException( "Pattern index out of range (0 to 127): " + patIdx );
		}
		sequence[ seqIdx ] = ( byte ) patIdx;
	}
	
	public Pattern getPattern( int patIdx ) {
		if( patIdx < 0 || patIdx > 127 ) {
			throw new IllegalArgumentException( "Pattern index out of range (0 to 127): " + patIdx );
		}
		if( patterns[ patIdx ] == null ) {
			patterns[ patIdx ] = new Pattern( getNumChannels() );
		}
		return patterns[ patIdx ];
	}
	
	public Instrument getInstrument( int instIdx ) {
		if( instIdx < 1 || instIdx > 31 ) {
			throw new IllegalArgumentException( "Instrument index out of range (1 to 31): " + instIdx );
		}
		return instruments[ instIdx ];
	}

	public static int calculateModuleLength( byte[] moduleHeader ) {
		int moduleLength = 1084 + 4 * calculateNumChannels( moduleHeader ) * 64 * calculateNumPatterns( moduleHeader );
		for( int instIdx = 1; instIdx < 32; instIdx++ ) {
			moduleLength += Instrument.calculateSampleDataLength( moduleHeader, instIdx );
		}
		return moduleLength;
	}

	public int load( byte[] module ) {
		char[] name = new char[ 20 ];
		for( int idx = 0; idx < name.length; idx++ ) {
			int chr = module[ idx ] & 0xFF;
			name[ idx ] = chr > 32 ? ( char ) chr : 32;
		}
		setSongName( new String( name ) );
		setSequenceLength( module[ 950 ] & 0x7F );
		restartPos = module[ 951 ] & 0x7F;
		if( restartPos >= sequenceLength ) {
			restartPos = 0;
		}
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			setSequenceEntry( seqIdx, module[ 952 + seqIdx ] & 0x7F );
		}
		setNumChannels( calculateNumChannels( module ) );
		int numPatterns = calculateNumPatterns( module );
		int moduleOffset = 1084;
		for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
			moduleOffset = getPattern( patIdx ).load( module, moduleOffset );
		}
		for( int instIdx = 1; instIdx <= 31; instIdx++ ) {
			moduleOffset = getInstrument( instIdx ).load( module, instIdx, moduleOffset );
		}
		return moduleOffset;
	}

	public int load( java.io.InputStream inputStream ) throws java.io.IOException {
		byte[] header = new byte[ 1084 ];
		readFully( inputStream, header, 0, header.length );
		byte[] module = new byte[ calculateModuleLength( header ) ];
		System.arraycopy( header, 0, module, 0, header.length );
		readFully( inputStream, module, header.length, module.length - header.length );
		return load( module );
	}

	public byte[] save() {
		byte[] module = new byte[ save( null ) ];
		save( module );
		return module;
	}
	
	public int save( byte[] outBuf ) {
		if( outBuf != null ) {
			writeAscii( songName, outBuf, 0, 20 );
			outBuf[ 950 ] = ( byte ) sequenceLength;
			outBuf[ 951 ] = ( byte ) ( restartPos & 0x7F );
			int numChannels = getNumChannels();
			if( numChannels == 4 ) {
				writeAscii( "M.K.", outBuf, 1080, 4 );
			} else if( numChannels < 10 ) {
				outBuf[ 1080 ] = ( byte ) ( '0' + numChannels );
				writeAscii( "CHN", outBuf, 1081, 3 );
			} else {
				outBuf[ 1080 ] = ( byte ) ( '0' + numChannels / 10 );
				outBuf[ 1081 ] = ( byte ) ( '0' + numChannels % 10 );
				writeAscii( "CH", outBuf, 1082, 2 );
			}
		}
		int numPatterns = 0;
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			int patIdx = seqIdx < sequenceLength ? getSequenceEntry( seqIdx ) : 0;
			if( outBuf != null ) {
				outBuf[ 952 + seqIdx ] = ( byte ) patIdx;
			}
			if( patIdx >= numPatterns ) {
				numPatterns = patIdx + 1;
			}
		}
		int outIdx = 1084;
		for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
			outIdx = patterns[ patIdx ].save( outBuf, outIdx );
		}
		for( int instIdx = 1; instIdx <= 31; instIdx++ ) {
			outIdx = instruments[ instIdx ].save( outBuf, instIdx, outIdx );
		}
		return outIdx;
	}

	private static void writeAscii( String text, byte[] outBuf, int offset, int len ) {
		if( text == null ) {
			text = "";
		}
		for( int idx = 0; idx < len; idx++ ) {
			outBuf[ offset + idx ] = ( byte ) ( idx < text.length() ? text.charAt( idx ) : 32 );
		}
	}

	private static int calculateNumChannels( byte[] header ) {
		switch( ( ( header[ 1082 ] & 0xFF ) << 8  ) | ( header[ 1083 ] & 0xFF ) ) {
			case 0x4b2e: /* M.K. */
			case 0x4b21: /* M!K! */
			case 0x5434: /* FLT4 */
				return 4;
			case 0x484e: /* xCHN */
				return header[ 1080 ] - '0';
			case 0x4348: /* xxCH */
				return ( header[ 1080 ] - '0' ) * 10 + header[ 1081 ] - '0';
			default:
				throw new IllegalArgumentException( "MOD Format not recognised!" );
		}
	}

	private static int calculateNumPatterns( byte[] header ) {
		int numPatterns = 0;
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			int patIdx = header[ 952 + seqIdx ] & 0x7F;
			if( patIdx >= numPatterns ) {
				numPatterns = patIdx + 1;
			}
		}
		return numPatterns;
	}

	private static void readFully( java.io.InputStream inputStream, byte[] buffer, int offset, int length ) throws java.io.IOException {
		int read = 1, end = offset + length;
		while( read > 0 ) {
			read = inputStream.read( buffer, offset, end - offset );
			offset += read;
		}
	}
}
