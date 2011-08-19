
package micromod;

public class Module {
	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	public String songName;
	public int numChannels, numInstruments, numPatterns;
	public int sequenceLength, restartPos, c2Rate, gain;
	public byte[] patterns, sequence;
	public Instrument[] instruments;

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
		System.arraycopy( module, 1084, patterns, 0, numNotes * 4 );
		numInstruments = 31;
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = new Instrument();
		int modIdx = 1084 + numNotes * 4;
		for( int instIdx = 1; instIdx <= numInstruments; instIdx++ ) {
			Instrument inst = new Instrument();
			inst.name = ascii( module, instIdx * 30 - 10, 22 );
			int sampleLength = ushortbe( module, instIdx * 30 + 12 ) * 2;
			inst.fineTune = module[ instIdx * 30 + 14 ] & 0xF;
			inst.volume = module[ instIdx * 30 + 15 ] & 0x7F;
			if( inst.volume > 64 ) inst.volume = 64;
			int loopStart = ushortbe( module, instIdx * 30 + 16 ) * 2;
			int loopLength = ushortbe( module, instIdx * 30 + 18 ) * 2;
			byte[] sampleData = new byte[ sampleLength + 1 ];
			if( modIdx + sampleLength > module.length )
				sampleLength = module.length - modIdx;
			System.arraycopy( module, modIdx, sampleData, 0, sampleLength );
			modIdx += sampleLength;
			if( loopStart + loopLength > sampleLength )
				loopLength = sampleLength - loopStart;
			if( loopLength < 4 ) {
				loopStart = sampleLength;
				loopLength = 0;
			}
			sampleData[ loopStart + loopLength ] = sampleData[ loopStart ];
			inst.loopStart = loopStart;
			inst.loopLength = loopLength;
			inst.sampleData = sampleData;
			instruments[ instIdx ] = inst;
		}
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
}
