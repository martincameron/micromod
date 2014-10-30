
package ibxm;

public class Module {
	public String songName = "Blank";
	public int numChannels = 4, numInstruments = 1;
	public int numPatterns = 1, sequenceLength = 1, restartPos = 0;
	public int defaultGVol = 64, defaultSpeed = 6, defaultTempo = 125, c2Rate = Sample.C2_PAL, gain = 64;
	public boolean linearPeriods = false, fastVolSlides = false;
	public int[] defaultPanning = { 51, 204, 204, 51 };
	public int[] sequence = { 0 };
	public Pattern[] patterns = { new Pattern( 4, 64 ) };
	public Instrument[] instruments = { new Instrument(), new Instrument() };

	private static final int[] keyToPeriod = {
		29020, 27392, 25855, 24403, 23034, 21741, 20521,
		19369, 18282, 17256, 16287, 15373, 14510, 13696
	};

	public Module() {}
	
	public Module( byte[] moduleData ) {
		if( isoLatin1( moduleData, 0, 17 ).equals( "Extended Module: " ) ) {
			loadXM( moduleData );
		} else if( isoLatin1( moduleData, 44, 4 ).equals( "SCRM" ) ) {
			loadS3M( moduleData );
		} else {
			loadMOD( moduleData );
		}
	}

	private void loadMOD( byte[] moduleData ) {
		songName = isoLatin1( moduleData, 0, 20 );
		sequenceLength = moduleData[ 950 ] & 0x7F;
		restartPos = moduleData[ 951 ] & 0x7F;
		if( restartPos >= sequenceLength ) restartPos = 0;
		sequence = new int[ 128 ];
		for( int seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			int patIdx = moduleData[ 952 + seqIdx ] & 0x7F;
			sequence[ seqIdx ] = patIdx;
			if( patIdx >= numPatterns ) numPatterns = patIdx + 1;
		}
		switch( ushortbe( moduleData, 1082 ) ) {
			case 0x4b2e: /* M.K. */
			case 0x4b21: /* M!K! */
			case 0x5434: /* FLT4 */
				numChannels = 4;
				c2Rate = Sample.C2_PAL;
				gain = 64;
				break;
			case 0x484e: /* xCHN */
				numChannels = moduleData[ 1080 ] - 48;
				c2Rate = Sample.C2_NTSC;
				gain = 32;
				break;
			case 0x4348: /* xxCH */
				numChannels  = ( moduleData[ 1080 ] - 48 ) * 10;
				numChannels += moduleData[ 1081 ] - 48;
				c2Rate = Sample.C2_NTSC;
				gain = 32;
				break;
			default:
				throw new IllegalArgumentException( "MOD Format not recognised!" );
		}
		defaultGVol = 64;
		defaultSpeed = 6;
		defaultTempo = 125;
		defaultPanning = new int[ numChannels ];
		for( int idx = 0; idx < numChannels; idx++ ) {
			defaultPanning[ idx ] = 51;
			if( ( idx & 3 ) == 1 || ( idx & 3 ) == 2 )
				defaultPanning[ idx ] = 204;
		}
		int moduleDataIdx = 1084;
		patterns = new Pattern[ numPatterns ];
		for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
			Pattern pattern = patterns[ patIdx ] = new Pattern( numChannels, 64 );
			for( int patDataIdx = 0; patDataIdx < pattern.data.length; patDataIdx += 5 ) {
				int period = ( moduleData[ moduleDataIdx ] & 0xF ) << 8;
				period = ( period | ( moduleData[ moduleDataIdx + 1 ] & 0xFF ) ) * 4;
				if( period > 112 ) {
					int key = 0, oct = 0;
					while( period < 14510 ) {
						period *= 2;
						oct++;
					}
					while( key < 12 ) {
						int d1 = keyToPeriod[ key ] - period;
						int d2 = period - keyToPeriod[ key + 1 ];
						if( d2 >= 0 ) {
							if( d2 < d1 ) key++;
							break;
						}
						key++;
					}
					pattern.data[ patDataIdx ] = ( byte ) ( oct * 12 + key );
				}
				int ins = ( moduleData[ moduleDataIdx + 2 ] & 0xF0 ) >> 4;
				ins = ins | moduleData[ moduleDataIdx ] & 0x10;
				pattern.data[ patDataIdx + 1 ] = ( byte ) ins;
				int effect = moduleData[ moduleDataIdx + 2 ] & 0x0F;
				int param  = moduleData[ moduleDataIdx + 3 ] & 0xFF;
				if( param == 0 && ( effect < 3 || effect == 0xA ) ) effect = 0;
				if( param == 0 && ( effect == 5 || effect == 6 ) ) effect -= 2;
				if( effect == 8 && numChannels == 4 ) effect = param = 0;
				pattern.data[ patDataIdx + 3 ] = ( byte ) effect;
				pattern.data[ patDataIdx + 4 ] = ( byte ) param;
				moduleDataIdx += 4;
			}
		}
		numInstruments = 31;
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = new Instrument();
		for( int instIdx = 1; instIdx <= numInstruments; instIdx++ ) {
			Instrument instrument = instruments[ instIdx ] = new Instrument();
			Sample sample = instrument.samples[ 0 ];
			instrument.name = isoLatin1( moduleData, instIdx * 30 - 10, 22 );
			int sampleLength = ushortbe( moduleData, instIdx * 30 + 12 ) * 2;
			int fineTune = ( moduleData[ instIdx * 30 + 14 ] & 0xF ) << 4;
			sample.fineTune = ( fineTune < 128 ) ? fineTune : fineTune - 256;
			int volume = moduleData[ instIdx * 30 + 15 ] & 0x7F;
			sample.volume = ( volume <= 64 ) ? volume : 64;
			sample.panning = -1;
			sample.c2Rate = c2Rate;
			int loopStart = ushortbe( moduleData, instIdx * 30 + 16 ) * 2;
			int loopLength = ushortbe( moduleData, instIdx * 30 + 18 ) * 2;
			if( loopStart + loopLength > sampleLength )
				loopLength = sampleLength - loopStart;
			if( loopLength < 4 ) {
				loopStart = sampleLength;
				loopLength = 0;
			}
			short[] sampleData = new short[ sampleLength ];
			if( moduleDataIdx + sampleLength > moduleData.length )
				sampleLength = moduleData.length - moduleDataIdx;
			for( int idx = 0, end = sampleLength; idx < end; idx++ )
				sampleData[ idx ] = ( short ) ( moduleData[ moduleDataIdx++ ] << 8 );
			sample.setSampleData( sampleData, loopStart, loopLength, false );
		}
	}

	private void loadS3M( byte[] moduleData ) {
		songName = codePage850( moduleData, 0, 28 );
		sequenceLength = ushortle( moduleData, 32 );
		numInstruments = ushortle( moduleData, 34 );
		numPatterns = ushortle( moduleData, 36 );
		int flags = ushortle( moduleData, 38 );
		int version = ushortle( moduleData, 40 );
		fastVolSlides = ( ( flags & 0x40 ) == 0x40 ) || version == 0x1300;
		boolean signedSamples = ushortle( moduleData, 42 ) == 1;
		if( intle( moduleData, 44 ) != 0x4d524353 )
			throw new IllegalArgumentException( "Not an S3M file!" );
		defaultGVol = moduleData[ 48 ] & 0xFF;
		defaultSpeed = moduleData[ 49 ] & 0xFF;
		defaultTempo = moduleData[ 50 ] & 0xFF;
		c2Rate = Sample.C2_NTSC;
		gain = moduleData[ 51 ] & 0x7F;
		boolean stereoMode = ( moduleData[ 51 ] & 0x80 ) == 0x80;
		boolean defaultPan = ( moduleData[ 53 ] & 0xFF ) == 0xFC;
		int[] channelMap = new int[ 32 ];
		for( int chanIdx = 0; chanIdx < 32; chanIdx++ ) {
			channelMap[ chanIdx ] = -1;
			if( ( moduleData[ 64 + chanIdx ] & 0xFF ) < 16 )
				channelMap[ chanIdx ] = numChannels++;
		}
		sequence = new int[ sequenceLength ];
		for( int seqIdx = 0; seqIdx < sequenceLength; seqIdx++ )
			sequence[ seqIdx ] = moduleData[ 96 + seqIdx ] & 0xFF;
		int moduleDataIdx = 96 + sequenceLength;
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = new Instrument();
		for( int instIdx = 1; instIdx <= numInstruments; instIdx++ ) {
			Instrument instrument = instruments[ instIdx ] = new Instrument();
			Sample sample = instrument.samples[ 0 ];
			int instOffset = ushortle( moduleData, moduleDataIdx ) << 4;
			moduleDataIdx += 2;
			instrument.name = codePage850( moduleData, instOffset + 48, 28 );
			if( moduleData[ instOffset ] != 1 ) continue;
			if( ushortle( moduleData, instOffset + 76 ) != 0x4353 ) continue;
			int sampleOffset = ( moduleData[ instOffset + 13 ] & 0xFF ) << 20;
			sampleOffset += ushortle( moduleData, instOffset + 14 ) << 4;
			int sampleLength = intle( moduleData, instOffset + 16 );
			int loopStart = intle( moduleData, instOffset + 20 );
			int loopLength = intle( moduleData, instOffset + 24 ) - loopStart;
			sample.volume = moduleData[ instOffset + 28 ] & 0xFF;
			sample.panning = -1;
			boolean packed = moduleData[ instOffset + 30 ] != 0;
			boolean loopOn = ( moduleData[ instOffset + 31 ] & 0x1 ) == 0x1;
			if( loopStart + loopLength > sampleLength )
				loopLength = sampleLength - loopStart;
			if( loopLength < 1 || !loopOn ) {
				loopStart = sampleLength;
				loopLength = 0;
			}
			boolean stereo = ( moduleData[ instOffset + 31 ] & 0x2 ) == 0x2;
			boolean sixteenBit = ( moduleData[ instOffset + 31 ] & 0x4 ) == 0x4;
			if( packed ) throw new IllegalArgumentException( "Packed samples not supported!" );
			sample.c2Rate = intle( moduleData, instOffset + 32 );
			short[] sampleData = new short[ loopStart + loopLength ];
			if( sixteenBit ) {
				if( signedSamples ) {
					for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
						sampleData[ idx ] = ( short ) ( ( moduleData[ sampleOffset ] & 0xFF ) | ( moduleData[ sampleOffset + 1 ] << 8 ) );
						sampleOffset += 2;
					}
				} else {
					for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
						int sam = ( moduleData[ sampleOffset ] & 0xFF ) | ( ( moduleData[ sampleOffset + 1 ] & 0xFF ) << 8 );
						sampleData[ idx ] = ( short ) ( sam - 32768 );
						sampleOffset += 2;
					}
				}
			} else {
				if( signedSamples ) {
					for( int idx = 0, end = sampleData.length; idx < end; idx++ )
						sampleData[ idx ] = ( short ) ( moduleData[ sampleOffset++ ] << 8 );
				} else {
					for( int idx = 0, end = sampleData.length; idx < end; idx++ )
						sampleData[ idx ] = ( short ) ( ( ( moduleData[ sampleOffset++ ] & 0xFF ) - 128 ) << 8 );
				}
			}
			sample.setSampleData( sampleData, loopStart, loopLength, false );
		}
		patterns = new Pattern[ numPatterns ];
		for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
			Pattern pattern = patterns[ patIdx ] = new Pattern( numChannels, 64 );
			int inOffset = ( ushortle( moduleData, moduleDataIdx ) << 4 ) + 2;
			int rowIdx = 0;
			while( rowIdx < 64 ) {
				int token = moduleData[ inOffset++ ] & 0xFF;
				if( token == 0 ) {
					rowIdx++;
					continue;
				}
				int noteKey = 0;
				int noteIns = 0;
				if( ( token & 0x20 ) == 0x20 ) { /* Key + Instrument.*/
					noteKey = moduleData[ inOffset++ ] & 0xFF;
					noteIns = moduleData[ inOffset++ ] & 0xFF;
					if( noteKey < 0xFE )
						noteKey = ( noteKey >> 4 ) * 12 + ( noteKey & 0xF ) + 1;
					if( noteKey == 0xFF ) noteKey = 0;
				}
				int noteVol = 0;
				if( ( token & 0x40 ) == 0x40 ) { /* Volume Column.*/
					noteVol = ( moduleData[ inOffset++ ] & 0x7F ) + 0x10;
					if( noteVol > 0x50 ) noteVol = 0;
				}
				int noteEffect = 0;
				int noteParam = 0;
				if( ( token & 0x80 ) == 0x80 ) { /* Effect + Param.*/
					noteEffect = moduleData[ inOffset++ ] & 0xFF;
					noteParam = moduleData[ inOffset++ ] & 0xFF;
					if( noteEffect < 1 || noteEffect >= 0x40 )
						noteEffect = noteParam = 0;
					if( noteEffect > 0 ) noteEffect += 0x80;
				}
				int chanIdx = channelMap[ token & 0x1F ];
				if( chanIdx >= 0 ) {
					int noteOffset = ( rowIdx * numChannels + chanIdx ) * 5;
					pattern.data[ noteOffset     ] = ( byte ) noteKey;
					pattern.data[ noteOffset + 1 ] = ( byte ) noteIns;
					pattern.data[ noteOffset + 2 ] = ( byte ) noteVol;
					pattern.data[ noteOffset + 3 ] = ( byte ) noteEffect;
					pattern.data[ noteOffset + 4 ] = ( byte ) noteParam;
				}
			}
			moduleDataIdx += 2;
		}
		defaultPanning = new int[ numChannels ];
		for( int chanIdx = 0; chanIdx < 32; chanIdx++ ) {
			if( channelMap[ chanIdx ] < 0 ) continue;
			int panning = 7;
			if( stereoMode ) {
				panning = 12;
				if( ( moduleData[ 64 + chanIdx ] & 0xFF ) < 8 ) panning = 3;
			}
			if( defaultPan ) {
				int panFlags = moduleData[ moduleDataIdx + chanIdx ] & 0xFF;
				if( ( panFlags & 0x20 ) == 0x20 ) panning = panFlags & 0xF;
			}
			defaultPanning[ channelMap[ chanIdx ] ] = panning * 17;
		}
	}

	private void loadXM( byte[] moduleData ) {
		if( ushortle( moduleData, 58 ) != 0x0104 )
			throw new IllegalArgumentException( "XM format version must be 0x0104!" );
		songName = codePage850( moduleData, 17, 20 );
		boolean deltaEnv = isoLatin1( moduleData, 38, 20 ).startsWith( "DigiBooster Pro" );
		int dataOffset = 60 + intle( moduleData, 60 );
		sequenceLength = ushortle( moduleData, 64 );
		restartPos = ushortle( moduleData, 66 );
		numChannels = ushortle( moduleData, 68 );
		numPatterns = ushortle( moduleData, 70 );
		numInstruments = ushortle( moduleData, 72 );
		linearPeriods = ( ushortle( moduleData, 74 ) & 0x1 ) > 0;
		defaultGVol = 64;
		defaultSpeed = ushortle( moduleData, 76 );
		defaultTempo = ushortle( moduleData, 78 );
		c2Rate = Sample.C2_NTSC;
		gain = 64;
		defaultPanning = new int[ numChannels ];
		for( int idx = 0; idx < numChannels; idx++ ) defaultPanning[ idx ] = 128;
		sequence = new int[ sequenceLength ];
		for( int seqIdx = 0; seqIdx < sequenceLength; seqIdx++ ) {
			int entry = moduleData[ 80 + seqIdx ] & 0xFF;
			sequence[ seqIdx ] = entry < numPatterns ? entry : 0;
		}
		patterns = new Pattern[ numPatterns ];
		for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
			if( moduleData[ dataOffset + 4 ] != 0 )
				throw new IllegalArgumentException( "Unknown pattern packing type!" );
			int numRows = ushortle( moduleData, dataOffset + 5 );
			int numNotes = numRows * numChannels;
			Pattern pattern = patterns[ patIdx ] = new Pattern( numChannels, numRows );
			int patternDataLength = ushortle( moduleData, dataOffset + 7 );
			dataOffset += intle( moduleData, dataOffset );
			int nextOffset = dataOffset + patternDataLength;
			if( patternDataLength > 0 ) {
				int patternDataOffset = 0;
				for( int note = 0; note < numNotes; note++ ) {
					int flags = moduleData[ dataOffset ];
					if( ( flags & 0x80 ) == 0 ) flags = 0x1F; else dataOffset++;
					byte key = ( flags & 0x01 ) > 0 ? moduleData[ dataOffset++ ] : 0;
					pattern.data[ patternDataOffset++ ] = key;
					byte ins = ( flags & 0x02 ) > 0 ? moduleData[ dataOffset++ ] : 0;
					pattern.data[ patternDataOffset++ ] = ins;
					byte vol = ( flags & 0x04 ) > 0 ? moduleData[ dataOffset++ ] : 0;
					pattern.data[ patternDataOffset++ ] = vol;
					byte fxc = ( flags & 0x08 ) > 0 ? moduleData[ dataOffset++ ] : 0;
					byte fxp = ( flags & 0x10 ) > 0 ? moduleData[ dataOffset++ ] : 0;
					if( fxc >= 0x40 ) fxc = fxp = 0;
					pattern.data[ patternDataOffset++ ] = fxc;
					pattern.data[ patternDataOffset++ ] = fxp;
				}
			}
			dataOffset = nextOffset;
		}
		instruments = new Instrument[ numInstruments + 1 ];
		instruments[ 0 ] = new Instrument();
		for( int insIdx = 1; insIdx <= numInstruments; insIdx++ ) {
			Instrument instrument = instruments[ insIdx ] = new Instrument();
			instrument.name = codePage850( moduleData, dataOffset + 4, 22 );
			int numSamples = instrument.numSamples = ushortle( moduleData, dataOffset + 27 );
			if( numSamples > 0 ) {
				instrument.samples = new Sample[ numSamples ];
				for( int keyIdx = 0; keyIdx < 96; keyIdx++ )
					instrument.keyToSample[ keyIdx + 1 ] = moduleData[ dataOffset + 33 + keyIdx ] & 0xFF;
				Envelope volEnv = instrument.volumeEnvelope = new Envelope();
				volEnv.pointsTick = new int[ 12 ];
				volEnv.pointsAmpl = new int[ 12 ];
				int pointTick = 0;
				for( int point = 0; point < 12; point++ ) {
					int pointOffset = dataOffset + 129 + ( point * 4 );
					pointTick = ( deltaEnv ? pointTick : 0 ) + ushortle( moduleData, pointOffset );
					volEnv.pointsTick[ point ] = pointTick;
					volEnv.pointsAmpl[ point ] = ushortle( moduleData, pointOffset + 2 );
				}
				Envelope panEnv = instrument.panningEnvelope = new Envelope();
				panEnv.pointsTick = new int[ 12 ];
				panEnv.pointsAmpl = new int[ 12 ];
				pointTick = 0;
				for( int point = 0; point < 12; point++ ) {
					int pointOffset = dataOffset + 177 + ( point * 4 );
					pointTick = ( deltaEnv ? pointTick : 0 ) + ushortle( moduleData, pointOffset );
					panEnv.pointsTick[ point ] = pointTick;
					panEnv.pointsAmpl[ point ] = ushortle( moduleData, pointOffset + 2 );
				}
				volEnv.numPoints = moduleData[ dataOffset + 225 ] & 0xFF;
				if( volEnv.numPoints > 12 ) volEnv.numPoints = 0;
				panEnv.numPoints = moduleData[ dataOffset + 226 ] & 0xFF;
				if( panEnv.numPoints > 12 ) panEnv.numPoints = 0;
				volEnv.sustainTick = volEnv.pointsTick[ moduleData[ dataOffset + 227 ] ];
				volEnv.loopStartTick = volEnv.pointsTick[ moduleData[ dataOffset + 228 ] ];
				volEnv.loopEndTick = volEnv.pointsTick[ moduleData[ dataOffset + 229 ] ];
				panEnv.sustainTick = panEnv.pointsTick[ moduleData[ dataOffset + 230 ] ];
				panEnv.loopStartTick = panEnv.pointsTick[ moduleData[ dataOffset + 231 ] ];
				panEnv.loopEndTick = panEnv.pointsTick[ moduleData[ dataOffset + 232 ] ];
				volEnv.enabled = volEnv.numPoints > 0 && ( moduleData[ dataOffset + 233 ] & 0x1 ) > 0;
				volEnv.sustain = ( moduleData[ dataOffset + 233 ] & 0x2 ) > 0;
				volEnv.looped = ( moduleData[ dataOffset + 233 ] & 0x4 ) > 0;
				panEnv.enabled = panEnv.numPoints > 0 && ( moduleData[ dataOffset + 234 ] & 0x1 ) > 0;
				panEnv.sustain = ( moduleData[ dataOffset + 234 ] & 0x2 ) > 0;
				panEnv.looped = ( moduleData[ dataOffset + 234 ] & 0x4 ) > 0;
				instrument.vibratoType = moduleData[ dataOffset + 235 ] & 0xFF;
				instrument.vibratoSweep = moduleData[ dataOffset + 236 ] & 0xFF;
				instrument.vibratoDepth = moduleData[ dataOffset + 237 ] & 0xFF;
				instrument.vibratoRate = moduleData[ dataOffset + 238 ] & 0xFF;
				instrument.volumeFadeOut = ushortle( moduleData, dataOffset + 239 );
			}
			dataOffset += intle( moduleData, dataOffset );
			int sampleHeaderOffset = dataOffset;
			dataOffset += numSamples * 40;
			for( int samIdx = 0; samIdx < numSamples; samIdx++ ) {
				Sample sample = instrument.samples[ samIdx ] = new Sample();
				int sampleDataBytes = intle( moduleData, sampleHeaderOffset );
				int sampleLoopStart = intle( moduleData, sampleHeaderOffset + 4 );
				int sampleLoopLength = intle( moduleData, sampleHeaderOffset + 8 );
				sample.volume = moduleData[ sampleHeaderOffset + 12 ];
				sample.fineTune = moduleData[ sampleHeaderOffset + 13 ];
				sample.c2Rate = Sample.C2_NTSC;
				boolean looped = ( moduleData[ sampleHeaderOffset + 14 ] & 0x3 ) > 0;
				boolean pingPong = ( moduleData[ sampleHeaderOffset + 14 ] & 0x2 ) > 0;
				boolean sixteenBit = ( moduleData[ sampleHeaderOffset + 14 ] & 0x10 ) > 0;
				sample.panning = moduleData[ sampleHeaderOffset + 15 ] & 0xFF;
				sample.relNote = moduleData[ sampleHeaderOffset + 16 ];
				sample.name = codePage850( moduleData, sampleHeaderOffset + 18, 22 );
				sampleHeaderOffset += 40;
				int sampleDataLength = sampleDataBytes;
				if( sixteenBit ) {
					sampleDataLength /= 2;
					sampleLoopStart /= 2;
					sampleLoopLength /= 2;
				}
				if( !looped || ( sampleLoopStart + sampleLoopLength ) > sampleDataLength ) {
					sampleLoopStart = sampleDataLength;
					sampleLoopLength = 0;
				}
				short[] sampleData = new short[ sampleDataLength ];
				if( sixteenBit ) {
					short ampl = 0;
					for( int outIdx = 0; outIdx < sampleDataLength; outIdx++ ) {
						int inIdx = dataOffset + outIdx * 2;
						ampl += moduleData[ inIdx ] & 0xFF;
						ampl += ( moduleData[ inIdx + 1 ] & 0xFF ) << 8;
						sampleData[ outIdx ] = ampl;
					}
				} else {
					byte ampl = 0;
					for( int outIdx = 0; outIdx < sampleDataLength; outIdx++ ) {
						ampl += moduleData[ dataOffset + outIdx ] & 0xFF;
						sampleData[ outIdx ] = ( short ) ( ampl << 8 );
					}
				}
				sample.setSampleData( sampleData, sampleLoopStart, sampleLoopLength, pingPong );
				dataOffset += sampleDataBytes;
			}
		}
	}

	private static int ushortbe( byte[] buf, int offset ) {
		return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
	}

	private static int ushortle( byte[] buf, int offset ) {
		return ( buf[ offset ] & 0xFF ) | ( ( buf[ offset + 1 ] & 0xFF ) << 8 );
	}

	private static int intle( byte[] buf, int offset ) {
		int value = buf[ offset ] & 0xFF;
		value  |= ( buf[ offset + 1 ] & 0xFF ) << 8;
		value  |= ( buf[ offset + 2 ] & 0xFF ) << 16;
		value  |= ( buf[ offset + 3 ] & 0x7F ) << 24;
		return value;
	}

	private static String isoLatin1( byte[] buf, int offset, int len ) {
		char[] str = new char[ len ];
		for( int idx = 0; idx < len; idx++ ) {
			int c = buf[ offset + idx ] & 0xFF;
			str[ idx ] = c < 32 ? 32 : ( char ) c;
		}
		return new String( str );
	}

	private static String codePage850( byte[] buf, int offset, int len ) {
		try {
			char[] str = new String( buf, offset, len, "Cp850" ).toCharArray();
			for( int idx = 0; idx < str.length; idx++ )
				str[ idx ] = str[ idx ] < 32 ? 32 : str[ idx ];
			return new String( str );
		} catch( java.io.UnsupportedEncodingException e ) {
			return isoLatin1( buf, offset, len );
		}
	}

	public void toStringBuffer( StringBuffer out ) {
		out.append( "Song Name: " + songName + '\n'
			+ "Num Channels: " + numChannels + '\n'
			+ "Num Instruments: " + numInstruments + '\n'
			+ "Num Patterns: " + numPatterns + '\n'
			+ "Sequence Length: " + sequenceLength + '\n'
			+ "Restart Pos: " + restartPos + '\n'
			+ "Default Speed: " + defaultSpeed + '\n'
			+ "Default Tempo: " + defaultTempo + '\n'
			+ "Linear Periods: " + linearPeriods + '\n' );
		out.append( "Sequence: " );
		for( int seqIdx = 0; seqIdx < sequence.length; seqIdx++ )
			out.append( sequence[ seqIdx ] + ", " );
		out.append( '\n' );
		for( int patIdx = 0; patIdx < patterns.length; patIdx++ ) {
			out.append( "Pattern " + patIdx + ":\n" );
			patterns[ patIdx ].toStringBuffer( out );
		}
		for( int insIdx = 1; insIdx < instruments.length; insIdx++ ) {
			out.append( "Instrument " + insIdx + ":\n" );
			instruments[ insIdx ].toStringBuffer( out );
		}
	}
}
