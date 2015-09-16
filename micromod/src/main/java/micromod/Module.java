package micromod;

public class Module extends AbstractModule<Pattern, Instrument> {
	private int restartPos;
	private Pattern[] patterns;
	private Instrument[] instruments;

	public Module() {
		setSongName( "Untitled" );
		initSequence( ( byte ) 127 );
		initPatterns( 128 );
		setNumChannels( 4 );
		initInstruments( 32 );
		for( int instIdx = 1; instIdx < getNumInstruments(); instIdx++ ) {
			setInstrument( instIdx, new Instrument() );
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

	public void setNumChannels( int numChannels ) {
		if( numChannels < 1 || numChannels > 32 ) {
			throw new IllegalArgumentException( "Number of channels out of range (1 to 32): " + numChannels );
		}
		setC2Rate( numChannels == 4 ? C2_PAL : C2_NTSC );
		setGain( numChannels == 4 ? 64 : 32 );
		for( int patIdx = 0; patIdx < getNumPatterns(); patIdx++ ) {
			Pattern pattern = getPattern( patIdx );
			if( pattern != null ) {
				patterns[ patIdx ] = new Pattern( numChannels, pattern );
			}
		}
		if( patterns[ 0 ] == null ) {
			patterns[ 0 ] = new Pattern( numChannels );
		}
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
		int sequenceLength = module[ 950 ] & 0x7F;
		initSequence( ( byte ) ( sequenceLength - 1 ) );
		restartPos = module[ 951 ] & 0x7F;
		if( restartPos >= getSequenceLength() ) {
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
			writeAscii( getSongName(), outBuf, 0, 20 );
			outBuf[ 950 ] = ( byte ) getSequenceLength();
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
			int patIdx = seqIdx < getSequenceLength() ? getSequenceEntry( seqIdx ) : 0;
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

	@Override
	public int getNumChannels() {
		return patterns[ 0 ].getNumChannels();
	}

	@Override
	public int getNumInstruments() {
		return instruments.length;
	}

	@Override
	public int getNumPatterns() {
		return patterns.length;
	}

	@Override
	public void initPatterns( int size ) {
		patterns = new Pattern[ size ];
	}

	@Override
	public Pattern getPattern( int patIdx ) {
		if( patIdx < 0 || patIdx > patterns.length ) {
			throw new IllegalArgumentException( "Pattern index out of range (0 to " + patterns.length + "): " + patIdx );
		}
		if( patterns[ patIdx ] == null ) {
			patterns[ patIdx ] = new Pattern( getNumChannels() );
		}
		return patterns[ patIdx ];
	}

	@Override
	public void setPattern( int patIdx, Pattern pattern ) {
		this.patterns[ patIdx ] = pattern;
	}

	@Override
	public void initInstruments( int size ) {
		this.instruments = new Instrument[ size ];
	}

	@Override
	public Instrument getInstrument( int instIdx ) {
		if( instIdx < 1 || instIdx > instruments.length ) {
			throw new IllegalArgumentException( "Instrument index out of range (1 to " + (instruments.length - 1) + "): " + instIdx );
		}
		return instruments[ instIdx ];
	}

	@Override
	public void setInstrument( int instIdx, Instrument instrument ) {
		this.instruments[ instIdx ] = instrument;
	}
}
