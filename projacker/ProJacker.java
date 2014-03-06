
package projacker;

/* A simple text format for Protracker MOD files. */
public class ProJacker {
	private static final String INSTRUMENT_SCHEMA = "Instrument(Name,Volume,FineTune,Waveform,WaveFile(Gain,Pitch),LoopStart,LoopLength)";
	private static final String PROJACKER_SCHEMA = "Module(Channels,Sequence," + INSTRUMENT_SCHEMA + ",Pattern(Row))";
	
	public static micromod.Module parse( java.io.Reader input ) throws java.io.IOException {
		Handler handler = new Handler();
		new Schema( new java.io.StringReader( PROJACKER_SCHEMA ) ).parse( input, handler );
		return handler.module;
	}

	public static class Handler implements Schema.Handler {
		public micromod.Module module;
		public int instIdx, patternIdx, rowIdx;
		public AudioData audioData;
		
		public void value( Schema.Value value ) {			
			Schema schema = value.getSchema();
			if( schema.getParent() == null ) {
				if( "Module".equals( schema.getName() ) ) {
					System.out.println( "Title: " + value );
					module = new micromod.Module();
					module.songName = value.toString();
					module.numInstruments = 31;
					module.instruments = new micromod.Instrument[ module.numInstruments + 1 ];
					for( int idx = 0; idx <= module.numInstruments; idx++ ) {
						module.instruments[ idx ] = new micromod.Instrument();
					}
				}
			} else {
				Schema parent = schema.getParent();
				if( "Module".equals( parent.getName() ) ) {
					if( "Channels".equals( schema.getName() ) ) {
						module.numChannels = value.toInteger();
						if( module.numChannels < 4 || module.numChannels > 16 ) {
							throw new IllegalArgumentException( "Number of channels out of range (4 to 16): " + module.numChannels );
						}
					} else if( "Sequence".equals( schema.getName() ) ) {
						System.out.println( "Sequence: " + value );
						module.numPatterns = 1;
						int[] sequence = new int[ 127 ];
						module.sequenceLength = value.toIntegerArray( sequence );
						if( module.sequenceLength < 1 || module.sequenceLength > 127 ) {
							throw new IllegalArgumentException( "Sequence length out of range (1 to 127): " + module.sequenceLength );
						}
						System.out.println( "Sequence Length: " + module.sequenceLength );
						module.sequence = new byte[ module.sequenceLength ];
						for( int idx = 0; idx < module.sequenceLength; idx++ ) {
							int pat = sequence[ idx ];
							if( pat < 0 || pat > 127 ) {
								throw new IllegalArgumentException( "Sequence entry out of range (0 to 127): " + pat );
							}
							if( pat >= module.numPatterns ) {
								module.numPatterns = pat + 1;
							}
							module.sequence[ idx ] = ( byte ) pat;
						}
						System.out.println( "Num Patterns: " + module.numPatterns );
						module.patterns = new byte[ module.numPatterns * 64 * 4 * module.numChannels ];
					} else if( "Instrument".equals( schema.getName() ) ) {
						instIdx = value.toInteger();
						if( instIdx < 1 || instIdx > module.numInstruments ) {
							throw new IllegalArgumentException( "Instrument index out of range (1 to " + module.numInstruments + "): " + instIdx );
						}
					} else if( "Pattern".equals( schema.getName() ) ) {
						rowIdx = 0;
						patternIdx = value.toInteger();
						if( patternIdx < 0 || patternIdx >= module.numPatterns ) {
							throw new IllegalArgumentException( "Pattern index out of range (0 to " + module.numPatterns + "): " + patternIdx );
						}
					}
				} else if( "Instrument".equals( parent.getName() ) ) {
					micromod.Instrument instrument = module.instruments[ instIdx ];
					if( "Name".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Name: " + value );
						instrument.name = value.toString();
					} else if( "Volume".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Volume: " + value );
						int vol = value.toInteger();
						if( vol < 0 || vol > 64 ) {
							throw new IllegalArgumentException( "Instrument " + instIdx + " volume out of range (0 to 64): " + vol );
						}
						instrument.volume = vol;
					} else if( "FineTune".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " FineTune: " + value );
						int fine = value.toInteger();
						if( fine < -8 || fine > 7 ) {
							throw new IllegalArgumentException( "Instrument " + instIdx + " finetune out of range (-8 to 7): " + fine );
						}
						instrument.fineTune = fine > 0 ? fine : fine + 16;
					} else if( "Waveform".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Waveform: " + value );
						instrument.sampleData = new byte[ 33 ];
						if( "Sawtooth".equals( value.toString() ) ) {
							for( int idx = 0; idx < 32; idx++ ) {
								instrument.sampleData[ idx ] = ( byte ) ( ( idx << 3 ) - 128 );
							}
						} else if( "Square".equals( value.toString() ) ) {
							for( int idx = 0; idx < 32; idx++ ) {
								instrument.sampleData[ idx ] = ( byte ) ( ( ( idx & 0x10 ) >> 4 ) * 255 - 128 );
							}
						} else {
							throw new IllegalArgumentException( "Invalid waveform type: " + value );
						}
						instrument.loopStart = 0;
						instrument.loopLength = 32;
					} else if( "WaveFile".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " WaveFile: " + value );
						try {
							// Get the left/mono channel from the wav file.
							audioData = new AudioData( new java.io.FileInputStream( value.toString() ), 0 );
							instrument.sampleData = audioData.quantize();
							instrument.loopStart = instrument.sampleData.length - 1;
							instrument.loopLength = 0;
						} catch( java.io.IOException e ) {
							throw new IllegalArgumentException( "Instrument " + instIdx +" unable to load wave file.", e );
						}
					} else if( "LoopStart".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " LoopStart: " + value );
						int loop = value.toInteger();
						int max = instrument.sampleData.length - 1;
						if( loop < 0 || loop > max ) {
							throw new IllegalArgumentException( "Instrument " + instIdx + " loop start out of range (0 to " + max + "): " + loop );
						}
						instrument.loopStart = loop;
					} else if( "LoopLength".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " LoopLength: " + value );
						int loop = value.toInteger();
						int max = instrument.sampleData.length - instrument.loopStart - 1;
						if( loop < 0 || loop > max ) {
							throw new IllegalArgumentException( "Instrument " + instIdx + " loop length out of range (0 to " + max + "): " + loop );
						}
						module.instruments[ instIdx ].loopLength = loop;
					}
				} else if( "WaveFile".equals( parent.getName() ) ) {
					micromod.Instrument instrument = module.instruments[ instIdx ];
					if( "Gain".equals( schema.getName() ) ) {
						audioData = audioData.scale( value.toInteger() );
						instrument.sampleData = audioData.quantize();
					} else if( "Pitch".equals( schema.getName() ) ) {
						double rate = audioData.getSamplingRate() * Math.pow( 2, value.toInteger() / -96.0 );
						audioData = audioData.resample( ( int ) rate );
						instrument.sampleData = audioData.quantize();
						instrument.loopStart = instrument.sampleData.length - 1;
						instrument.loopLength = 0;
					}
				} else if( "Pattern".equals( parent.getName() ) ) {
					if( "Row".equals( schema.getName() ) ) {
						String row = value.toString();
						int channelIdx = 0;
						char[] note = new char[ 8 ];
						int idx = 0, len = row.length();
						while( idx < len ) {
							int noteIdx = 0;
							while( idx < len && noteIdx < 8 ) {
								note[ noteIdx++ ] = row.charAt( idx++ );
							}
							if( noteIdx == 8 ) {
								parseNote( note, module.patterns, ( ( patternIdx * 64 + rowIdx ) * module.numChannels + channelIdx ) * 4 );
							} else {
								throw new IllegalArgumentException( "Pattern " + patternIdx + " Row " + rowIdx + " Channel " + channelIdx + ". Malformed key: " + new String( note, 0, noteIdx ) );
							}
							while( idx < len && row.charAt( idx ) <= 32 ) {
								idx++;
							}
							channelIdx++;							
						}
						rowIdx++;
					}
				}
			}
			//System.out.println( "Token " + context.getName() + ", Value " + value );
		}
		
		private void parseNote( char[] input, byte[] output, int outputIdx ) {
			if( input.length >= 8 ) {
				int key = numChar( input[ 0 ], 11 );
				if( key > 10 ) {
					/* A-G etc.*/
					throw new UnsupportedOperationException( "Fixme." );
				} else {
					/* Decimal note number(1-72).*/
					key = numChar( input[ 0 ], 10 ) * 100 + numChar( input[ 1 ], 10 ) * 10 + numChar( input[ 2 ], 10 );
					if( key < 0 || key > 72 ) {
						throw new IllegalArgumentException( "Pattern " + patternIdx + " Row " + rowIdx + " key out of range (0 to 72): " + key );
					}
				}
				output[ outputIdx ] = ( byte ) key;
				int ins = numChar( input[ 3 ], 10 ) * 10 + numChar( input[ 4 ], 10 );
				if( ins < 0 || ins > module.numInstruments ) {
					throw new IllegalArgumentException( "Pattern " + patternIdx + " Row " + rowIdx + " instrument out of range (0 to " + module.numInstruments + "): " + ins );
				}
				output[ outputIdx + 1 ] = ( byte ) ins;
				output[ outputIdx + 2 ] = ( byte ) numChar( input[ 5 ], 16 );
				output[ outputIdx + 3 ] = ( byte ) ( ( numChar( input[ 6 ], 16 ) << 4 ) + numChar( input[ 7 ], 16 ) );
			} else {
				throw new IllegalArgumentException( "Note too short: " + new String( input, 0, input.length ) );
			}
		}
		
		/* Digit of the form [0-9A-Z] or hyphen(0).*/
		private static int numChar( char chr, int radix ) {
			int value = 0;
			if( chr >= '0' && chr <= '9' ) {
				value = chr - '0';
			} else if( chr >= 'A' && chr <= 'Z' ) {
				value = chr + 10 - 'A';
			} else if( chr != '-' ) {
				throw new IllegalArgumentException( "Invalid character: " + chr );
			}
			if( value >= radix ) {
				throw new IllegalArgumentException( "Invalid character: " + chr );
			}
			return value;
		}
	}

	public static void play( micromod.Module module ) throws javax.sound.sampled.LineUnavailableException {
		final int SAMPLE_RATE = 48000;
		// Initialise Micromod.
		micromod.Micromod micromod = new micromod.Micromod( module, SAMPLE_RATE );
		micromod.setInterpolation( true );
		// Print some info.
		System.out.println( "Micromod " + micromod.VERSION );
		System.out.println( "Song name: " + module.songName );
		for( int idx = 1; idx < module.instruments.length; idx++ ) {
			String name = module.instruments[ idx ].name;
			if( name != null && name.trim().length() > 0 )
				System.out.println( String.format( "%1$3d ", idx ) + name );
		}
		int duration = micromod.calculateSongDuration();
		System.out.println( "Song length: " + duration / SAMPLE_RATE + " seconds." );
		// Application will exit when song finishes.
		int[] mixBuf = new int[ micromod.getMixBufferLength() ];
		byte[] outBuf = new byte[ mixBuf.length * 4 ];
		javax.sound.sampled.AudioFormat audioFormat = new javax.sound.sampled.AudioFormat( SAMPLE_RATE, 16, 2, true, true );
		javax.sound.sampled.SourceDataLine audioLine = javax.sound.sampled.AudioSystem.getSourceDataLine( audioFormat );
		audioLine.open();
		audioLine.start();
		int samplePos = 0;
		while( samplePos < duration ) {
			int count = micromod.getAudio( mixBuf );
			int outIdx = 0;
			for( int mixIdx = 0, mixEnd = count * 2; mixIdx < mixEnd; mixIdx++ ) {
				int sam = mixBuf[ mixIdx ];
				if( sam > 32767 ) sam = 32767;
				if( sam < -32768 ) sam = -32768;
				outBuf[ outIdx++ ] = ( byte ) ( sam >> 8 );
				outBuf[ outIdx++ ] = ( byte )  sam;
			}
			audioLine.write( outBuf, 0, outIdx );
			samplePos += count;
		}
		audioLine.drain();
		audioLine.close();
	}

	public static void main( String[] args ) throws Exception {
		if( args.length == 1 ) {
			play( parse( new java.io.InputStreamReader( new java.io.FileInputStream( args[ 0 ] ) ) ) );
		} else {
			System.err.println( "Usage java " + ProJacker.class.getName() + " input.pj" );
		}
	}
}
