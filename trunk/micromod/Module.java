
package micromod;

public class Module {
	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	public String songName;
	public int numChannels, numInstruments, numPatterns;
	public int sequenceLength, restartPos, c2Rate, gain;
	public byte[] patterns, sequence;
	public Instrument[] instruments;

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

	public Module() {
		songName = "Blank";
		numChannels = 4;
		numInstruments = 1;
		numPatterns = 1;
		sequenceLength = 1;
		c2Rate = C2_PAL;
		gain = 64;
		patterns = new byte[ 64 * 4 * numChannels ];
		sequence = new byte[ 1 ];
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = instruments[ 1 ] = new Instrument();
	}

	public Module( byte[] module ) {
		songName = ascii( module, 0, 20 );
		sequenceLength = module[ 950 ] & 0x7F;
		restartPos = module[ 951 ] & 0x7F;
		if( restartPos >= sequenceLength ) restartPos = 0;
		sequence = new byte[ 128 ];
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			int patIdx = module[ 952 + seqIdx ] & 0x7F;
			sequence[ seqIdx ] = ( byte ) patIdx;
			if( patIdx >= numPatterns ) numPatterns = patIdx + 1;
		}
		switch( ushortbe( module, 1082 ) ) {
			case 0x4b2e: /* M.K. */
			case 0x4b21: /* M!K! */
			case 0x5434: /* FLT4 */
				numChannels = 4;
				c2Rate = C2_PAL;
				gain = 64;
				break;
			case 0x484e: /* xCHN */
				numChannels = module[ 1080 ] - 48;
				c2Rate = C2_NTSC;
				gain = 32;
				break;
			case 0x4348: /* xxCH */
				numChannels  = ( module[ 1080 ] - 48 ) * 10;
				numChannels += module[ 1081 ] - 48;
				c2Rate = C2_NTSC;
				gain = 32;
				break;
			default:
				throw new IllegalArgumentException( "MOD Format not recognised!" );
		}
		int numNotes = numPatterns * 64 * numChannels;
		patterns = new byte[ numNotes * 4 ];
		for( int patIdx = 0; patIdx < patterns.length; patIdx += 4 ) {
			int period = ( module[ 1084 + patIdx ] & 0xF ) << 8;
			period = period | ( module[ 1084 + patIdx + 1 ] & 0xFF );
			if( period < 28 || period > 1712 ) {
				patterns[ patIdx ] = 0;
			} else {
				/* Convert period to key. */
				int key = 1;
				while( keyToPeriod[ key + 12 ] >= period ) key += 12;
				while( keyToPeriod[ key + 1 ] >= period ) key++;
				if( ( keyToPeriod[ key ] - period ) >= ( period - keyToPeriod[ key + 1 ] ) ) key++;
				patterns[ patIdx ] = ( byte ) key;
			}
			int ins = ( module[ 1084 + patIdx + 2 ] & 0xF0 ) >> 4;
			patterns[ patIdx + 1 ] = ( byte ) ( ins | ( module[ 1084 + patIdx ] & 0x10 ) );
			patterns[ patIdx + 2 ] = ( byte ) ( module[ 1084 + patIdx + 2 ] & 0xF );
			patterns[ patIdx + 3 ] = module[ 1084 + patIdx + 3 ];
		}
		numInstruments = 31;
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = new Instrument();
		int modIdx = 1084 + numNotes * 4;
		for( int instIdx = 1; instIdx <= numInstruments; instIdx++ ) {
			Instrument inst = new Instrument();
			modIdx = inst.load( module, instIdx, modIdx );
			instruments[ instIdx ] = inst;
		}
	}

	public byte[] save() {
		byte[] module = new byte[ save( null ) ];
		save( module );
		return module;
	}
	
	public int save( byte[] outBuf ) {
		if( outBuf != null ) {
			writeAscii( songName, outBuf, 0, 20 );
			if( sequenceLength < 1 || sequenceLength > 127 ) {
				throw new IndexOutOfBoundsException( "Sequence length out of range (1-127): " + sequenceLength );
			}
			outBuf[ 950 ] = ( byte ) sequenceLength;
			if( restartPos < 0 || restartPos >= sequenceLength ) {
				throw new IndexOutOfBoundsException( "Restart pos out of range (0-" + sequenceLength + "): " + restartPos );
			}			
			outBuf[ 951 ] = ( byte ) ( restartPos & 0x7F );
			if( numChannels < 1 || numChannels > 99 ) {
				throw new IndexOutOfBoundsException( "Invalid number of channels (1-99): " + numChannels );
			} else if( numChannels == 4 ) {
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
		int numPat = 0;
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			int patIdx = seqIdx < sequenceLength ? sequence[ seqIdx ] & 0x7F : 0;
			if( outBuf != null ) {
				outBuf[ 952 + seqIdx ] = ( byte ) patIdx;
			}
			if( patIdx >= numPat ) {
				numPat = patIdx + 1;
				if( numPat > numPatterns ) {
					throw new IndexOutOfBoundsException( "Sequence entry out of range (0-" + ( numPatterns - 1 ) + "): " + patIdx );
				}
			}
		}
		if( outBuf != null ) {
			int maxKey = numChannels == 4 ? 48 : 72;
			for( int patIdx = 0; patIdx < numPat; patIdx++ ) {
				for( int rowIdx = 0; rowIdx < 64; rowIdx++ ) {
					for( int chanIdx = 0; chanIdx < numChannels; chanIdx++ ) {
						int offset = ( ( patIdx * 64 + rowIdx ) * numChannels + chanIdx ) * 4;
						int key = patterns[ offset ] & 0xFF;
						if( key > maxKey ) {
							throw new IndexOutOfBoundsException( "Pattern " + patIdx + ", Row " + rowIdx + ": Key out of range (0-" + maxKey + "): " + key );
						}
						int per = key > 0 ? keyToPeriod[ key ] : 0;
						int ins = patterns[ offset + 1 ] & 0xFF;
						if( ins > 31 ) {
							throw new IndexOutOfBoundsException( "Pattern " + patIdx + ", Row " + rowIdx + ": Instrument out of range (0-31): " + ins );
						}
						outBuf[ 1084 + offset ] = ( byte ) ( ( ins & 0x10 ) | ( per >> 8 ) );
						outBuf[ 1084 + offset + 1 ] = ( byte ) per;
						outBuf[ 1084 + offset + 2 ] = ( byte ) ( ( ins << 4 ) | ( patterns[ offset + 2 ] & 0xF ) );
						outBuf[ 1084 + offset + 3 ] = patterns[ offset + 3 ];
					}
				}
			}
		}
		int outIdx = 1084 + numPat * 64 * numChannels * 4;
		if( numInstruments > 31 ) {
			throw new IndexOutOfBoundsException( "Number of instruments out of range (0-31): " + numInstruments );
		}
		for( int instIdx = 1; instIdx <= numInstruments; instIdx++ ) {
			Instrument instrument = instruments[ instIdx ];
			outIdx = instrument.save( outBuf, instIdx, outIdx );
		}
		return outIdx;
	}

	private static int ushortbe( byte[] buf, int offset ) {
		return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
	}
	
	private static String ascii( byte[] buf, int offset, int len ) {
		char[] str = new char[ len ];
		for( int idx = 0; idx < len; idx++ ) {
			int c = buf[ offset + idx ] & 0xFF;
			str[ idx ] = c < 32 ? 32 : ( char ) c;
		}
		return new String( str );
	}
	
	private static void writeAscii( String text, byte[] outBuf, int offset, int len ) {
		if( text == null ) {
			text = "";
		}
		for( int idx = 0; idx < len; idx++ ) {
			outBuf[ offset + idx ] = ( byte ) ( idx < text.length() ? text.charAt( idx ) : 32 );
		}
	}
}
