
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
					module.setSongName( value.toString() );
				}
			} else {
				Schema parent = schema.getParent();
				if( "Module".equals( parent.getName() ) ) {
					if( "Channels".equals( schema.getName() ) ) {
						module.setNumChannels( value.toInteger() );
					} else if( "Sequence".equals( schema.getName() ) ) {
						System.out.println( "Sequence: " + value );
						int[] sequence = new int[ 128 ];
						int sequenceLength = value.toIntegerArray( sequence );
						module.setSequenceLength( sequenceLength );
						System.out.println( "Sequence Length: " + module.getSequenceLength() );
						for( int idx = 0; idx < sequenceLength; idx++ ) {
							module.setSequenceEntry( idx, sequence[ idx ] );
						}
					} else if( "Instrument".equals( schema.getName() ) ) {
						instIdx = value.toInteger();
					} else if( "Pattern".equals( schema.getName() ) ) {
						rowIdx = 0;
						patternIdx = value.toInteger();
					}
				} else if( "Instrument".equals( parent.getName() ) ) {
					micromod.Instrument instrument = module.getInstrument( instIdx );
					if( "Name".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Name: " + value );
						instrument.setName( value.toString() );
					} else if( "Volume".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Volume: " + value );
						instrument.setVolume( value.toInteger() );
					} else if( "FineTune".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " FineTune: " + value );
						instrument.setFineTune( value.toInteger() );
					} else if( "Waveform".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " Waveform: " + value );
						byte[] waveform = new byte[ 32 ];
						if( "Sawtooth".equals( value.toString() ) ) {
							for( int idx = 0; idx < 32; idx++ ) {
								waveform[ idx ] = ( byte ) ( ( idx << 3 ) - 128 );
							}
						} else if( "Square".equals( value.toString() ) ) {
							for( int idx = 0; idx < 32; idx++ ) {
								waveform[ idx ] = ( byte ) ( ( ( idx & 0x10 ) >> 4 ) * 255 - 128 );
							}
						} else {
							throw new IllegalArgumentException( "Invalid waveform type: " + value );
						}
						instrument.setSampleData( waveform, 0, waveform.length );
					} else if( "WaveFile".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " WaveFile: " + value );
						try {
							// Get the left/mono channel from the wav file.
							audioData = new AudioData( new java.io.FileInputStream( value.toString() ), 0 );
							//instrument.setSampleData( audioData.quantize(), 0, 0 );
						} catch( java.io.IOException e ) {
							throw new IllegalArgumentException( "Instrument " + instIdx +" unable to load wave file.", e );
						}
					} else if( "LoopStart".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " LoopStart: " + value );
						byte[] sampleData = audioData.quantize();
						int loopStart = value.toInteger();
						instrument.setSampleData( sampleData, loopStart, sampleData.length - loopStart );
					} else if( "LoopLength".equals( schema.getName() ) ) {
						System.out.println( "Instrument " + instIdx + " LoopLength: " + value );
						byte[] sampleData = audioData.quantize();
						int loopStart = instrument.getLoopStart();
						int loopLength = value.toInteger();
						instrument.setSampleData( sampleData, loopStart, loopLength );
					}
				} else if( "WaveFile".equals( parent.getName() ) ) {
					micromod.Instrument instrument = module.getInstrument( instIdx );
					if( "Gain".equals( schema.getName() ) ) {
						audioData = audioData.scale( value.toInteger() );
						//instrument.setSampleData( audioData.quantize(), 0, 0 );
					} else if( "Pitch".equals( schema.getName() ) ) {
						double rate = audioData.getSamplingRate() * Math.pow( 2, value.toInteger() / -96.0 );
						audioData = audioData.resample( ( int ) rate );
						instrument.setSampleData( audioData.quantize(), 0, 0 );
					}
				} else if( "Pattern".equals( parent.getName() ) ) {
					micromod.Pattern pattern = module.getPattern( patternIdx );
					if( "Row".equals( schema.getName() ) ) {
						String row = value.toString();
						int channelIdx = 0;
						char[] input = new char[ 8 ];
						micromod.Note output = new micromod.Note();
						int idx = 0, len = row.length();
						while( idx < len ) {
							int noteIdx = 0;
							while( idx < len && noteIdx < 8 ) {
								input[ noteIdx++ ] = row.charAt( idx++ );
							}
							if( noteIdx == 8 ) {
								parseNote( input, output );
								pattern.setNote( rowIdx, channelIdx, output );
							} else {
								throw new IllegalArgumentException( "Pattern " + patternIdx + " Row " + rowIdx + " Channel " + channelIdx + ". Malformed key: " + new String( input, 0, noteIdx ) );
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
		
		private void parseNote( char[] input, micromod.Note output ) {
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
				output.key = key;
				int ins = numChar( input[ 3 ], 10 ) * 10 + numChar( input[ 4 ], 10 );
				if( ins < 0 || ins > 31 ) {
					throw new IllegalArgumentException( "Pattern " + patternIdx + " Row " + rowIdx + " instrument out of range (0 to 31): " + ins );
				}
				output.instrument = ins;
				output.effect = numChar( input[ 5 ], 16 );
				output.parameter = ( numChar( input[ 6 ], 16 ) << 4 ) + numChar( input[ 7 ], 16 );
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
		//micromod.setInterpolation( true );
		// Print some info.
		System.out.println( "Micromod " + micromod.VERSION );
		System.out.println( "Song name: " + module.getSongName() );
		for( int idx = 1; idx < 32; idx++ ) {
			String name = module.getInstrument( idx ).getName();
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
		if( args.length > 0 ) {
			micromod.Module module = parse( new java.io.InputStreamReader( new java.io.FileInputStream( args[ 0 ] ) ) );
			if( args.length > 1 ) {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] );
				try{
					outputStream.write( module.save() );
				} finally {
					outputStream.close();
				}
			} else {
				play( module );
			}
		} else {
			System.err.println( "Usage java " + ProJacker.class.getName() + " input.pj output.mod" );
		}
	}
}
