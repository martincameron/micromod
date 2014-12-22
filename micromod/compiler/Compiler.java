
package micromod.compiler;

/* Compiles textual MT files to Protracker MOD files. */
public class Compiler {
	private static final String NOTE_INFO =
		"\nAn 8-character ProTracker note takes the form 'KKOIIFPP', where:\n\n" +
		"    KK = Key (One of 'C-', 'C#', 'D-', 'D#', 'E-', 'F-', 'F#',\n" +
		"                     'G-', 'G#', 'A-', 'A#', 'B-', or '--').\n" +
		"    O  = Octave (0 to 6, or '-').\n" +
		"    II = Instrument or macro index (Decimal, 01 to 99, or '--').\n" +
		"    F  = Effect Command (Hexadecimal, 0 to F, or '-').\n" +
		"    PP = Effect Parameter (Hexadecimal, 00 to FF, or '--').\n\n" +
		"For example, an empty note would be '--------', and instrument 1\n" +
		"played at middle-c, with no effect would be 'C-201---'.\n" +
		"The channel volume and fine-tune are set from the instrument.\n\n" +
		"When a macro is played instead of an instrument, it will play until\n" +
		"a note with an instrument is encountered. The transpose and volume\n" +
		"of a macro can be adjusted by using a key or set volume (effect C)\n" +
		"in the pattern. Macros can play over multiple patterns or loop within\n" +
		"the same pattern using double patterns, for example 'Pattern 0,1'.\n\n" +
		"The available effect/parameter combinations are:\n\n" +
		"    0xy = Arpeggio, rapidly cycle through key, key + x, key + y.\n" +
		"    1xx = Portamento up with speed xx.\n" +
		"    1Fx = Portamento up with speed x semitones per row (Macro only).\n" +
		"    2xx = Portamento down with speed xx.\n" +
		"    2Fx = Portamento down with speed x semitones per row (Macro only).\n" +
		"    3xx = Tone portamento, slide towards the current key at speed xx.\n" +
		"    3Fx = Tone portamento with speed x semitones per row (Macro only).\n" +
		"    4xy = Vibrato with speed x, depth y.\n" +
		"    5xy = Continue tone portamento with volume-slide speed of (x - y).\n" +
		"    6xy = Continue vibrato with volume slide speed of (x - y).\n" +
		"    7xx = Tremolo with speed x, depth y.\n" +
		"    8xx = Set panning 00 to 7F. Not for 4-channel modules.\n" +
		"    9xx = Set sample offset to xx * 256 samples.\n" +
		"    9Fx = Increase sample offset by x * length / 64 (Macro only).\n" +
		"    Axy = Volume slide with speed of (x - y).\n" +
		"    AxF = Linear volume-slide up with speed x (Macro only).\n" +
		"    AFx = Linear volume-slide down with speed x (Macro only).\n" +
		"    Bxx = Pattern jump to pattern xx.\n" +
		"    Cxx = Set volume of instrument or macro to xx (00 to 40).\n" +
		"    Dnn = Pattern break, to row nn (decimal 00 to 63) of next pattern.\n" +
		"    E1x = Fine portamento up with speed x.\n" +
		"    E2x = Fine portamento down with speed x.\n" +
		"    E3x = Glissando. (Not supported in Micromod).\n" +
		"    E4x = Set vibrato waveform x (Sine 0, Saw 1, Square 2, Random 3).\n" +
		"    E5x = Set channel fine-tune x (1 to 7 up, F to 8 down).\n" +
		"    E60 = Set pattern loop marker.\n" +
		"    E6x = Pattern loop (replay from the marker x times).\n" +
		"    E7x = Set tremolo waveform x (Sine 0, Saw 1, Square 2, Random 3).\n" +
		"    E9x = Retrigger sample every x ticks.\n" +
		"    EAx = Fine volume-slide up with speed x.\n" +
		"    EBx = Fine volume-slide down with speed x.\n" +
		"    ECx = Note cut. Set volume to zero after x ticks.\n" +
		"    EDx = Note delay. Wait x ticks before triggering key.\n" +
		"    EEx = Pattern delay. Add x ticks to current row.\n" +
		"    EFx = Invert loop. (Not supported in Micromod).\n" +
		"    Fxx = Set speed 00 to 1F (ticks per row, default 6).\n" +
		"    Fxx = Set tempo 20 to FF (tick length 2.5/xx seconds, default 7D).";

	public static void main( String[] args ) throws java.io.IOException {
		String mtFile = null, modFile = null, outDir = null, wavFile = null;
		boolean printNotes = false, printSyntax = false, interpolation = false;
		int[] sequence = null;
		int argsIdx = 0, key = 0;
		while( argsIdx < args.length ) {
			String arg = args[ argsIdx++ ];
			if( "-notes".equals( arg ) ) {
				printNotes = true;
			} else if( "-syntax".equals( arg ) ) {
				printSyntax = true;
			} else if( "-dir".equals( arg ) ) {
				outDir = args[ argsIdx++ ];
			} else if( "-hq".equals( arg ) ) {
				interpolation = true;
			} else if( "-key".equals( arg ) ) {
				key = micromod.Note.parseKey( args[ argsIdx++ ] );
			} else if( "-mod".equals( arg ) || "-out".equals( arg ) ) {
				modFile = args[ argsIdx++ ];
			} else if( "-wav".equals( arg ) ) {
				wavFile = args[ argsIdx++ ];
			} else if( "-seq".equals( arg ) || "-pat".equals( arg ) ) {
				sequence = Parser.parseIntegerArray( args[ argsIdx++ ] );
			} else {
				mtFile = arg;
			}
		}
		if( printNotes ) {
			System.out.println( NOTE_INFO );
		} else if( printSyntax ) {
			System.out.println( Parser.syntax( new Module( null ) ) );
		} else if( mtFile != null ) {
			if( modFile != null ) {
				System.out.println( "Compiling '" + mtFile + "' to module '" + modFile + "'." );
				convert( new java.io.File( mtFile ), new java.io.File( modFile ) );
			} else {
				System.out.println( "Compiling and playing '" + mtFile + "'." );
				play( new java.io.File( mtFile ), sequence, interpolation );
			}
		} else if( modFile != null && ( outDir != null || wavFile != null ) ) {
			if( outDir != null ) {
				System.out.println( "Extracting module '" + modFile + "' to directory '" + outDir + "'." );
				decompile( new micromod.Module( new java.io.FileInputStream( modFile ) ), new java.io.File( outDir ) );
			} else {
				System.out.println( "Converting module '" + modFile + "' to sample '" + wavFile + "'." );
				if( sequence == null || sequence.length < 1 ) {
					sequence = new int[ 1 ];
				}
				if( key < 1 ) {
					key = micromod.Note.parseKey( "C-2" );
				}
				patternToSample( new java.io.File( modFile ), new java.io.File( wavFile ), sequence[ 0 ], key, interpolation );
			}
		} else {
			System.err.println( "Micromod Compiler! (c)2014 mumart@gmail.com" );
			System.err.println( "             Play: input.mt [-hq] [-seq 1,2,3]" );
			System.err.println( "          Compile: input.mt [-out output.mod]" );
			System.err.println( "        Decompile: -mod input.mod -dir outputdir" );
			System.err.println( "    Mod To Sample: -mod input.mod -wav output.wav [-pat 0] [-key C-2] [-hq]" );
			System.err.println( "  Print MT Syntax: -syntax" );
			System.err.println( "  Print Note Info: -notes" );
		}
	}

	private static void play( java.io.File mtFile, int[] sequence, boolean interpolation ) throws java.io.IOException {
		Module module = compile( mtFile );
		if( sequence != null ) {
			module.setSequenceLength( sequence.length );
			for( int idx = 0; idx < sequence.length; idx++ ) {
				module.setSequenceEntry( idx, sequence[ idx ] );
			}
		}
		micromod.Player player = new micromod.Player( module.getModule(), interpolation, false );
		System.out.println( player.getModuleInfo() );
		Thread thread = new Thread( player );
		thread.start();
		try {
			thread.join();
		} catch( InterruptedException e ) {
			System.err.println( "Interrupted!" );
		}
	}

	private static void convert( java.io.File mtFile, java.io.File modFile ) throws java.io.IOException {
		if( modFile.exists() ) {
			throw new IllegalArgumentException( "Output file already exists!" );
		}
		java.io.OutputStream outputStream = new java.io.FileOutputStream( modFile );
		try {
			outputStream.write( compile( mtFile ).getModule().save() );
		} finally {
			outputStream.close();
		}
	}

	private static Module compile( java.io.File mtFile ) throws java.io.IOException {
		Module module = new Module( mtFile.getParentFile() );
		Parser.parse( new java.io.InputStreamReader( new java.io.FileInputStream( mtFile ) ), module );
		return module;
	}

	private static void decompile( micromod.Module module, java.io.File outDir ) throws java.io.IOException {
		if( outDir.exists() ) {
			throw new IllegalArgumentException( "Output directory already exists!" );
		}
		outDir.mkdir();
		java.io.Writer writer = new java.io.OutputStreamWriter( new java.io.FileOutputStream( new java.io.File( outDir, "module.mt" ) ) );
		try {
			writer.write( "Module \"" + nameString( module.getSongName() ) + "\"\n" );
			int numChannels = module.getNumChannels();
			writer.write( "Channels " + numChannels + "\n" );
			writer.write( "Sequence \"" + module.getSequenceEntry( 0 ) );
			int numPatterns = 1;
			for( int idx = 1, len = module.getSequenceLength(); idx < len; idx++ ) {
				int seqEntry = module.getSequenceEntry( idx );
				if( seqEntry >= numPatterns ) {
					numPatterns = seqEntry + 1;
				}
				writer.write( "," + seqEntry );
			}
			writer.write( "\"\n" );
			for( int instIdx = 1; instIdx < 32; instIdx++ ) {
				micromod.Instrument instrument = module.getInstrument( instIdx );
				String name = nameString( instrument.getName() );
				int volume = instrument.getVolume();
				int fineTune = instrument.getFineTune();
				int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
				if( name.length() > 0 || volume > 0 || fineTune > 0 || sampleLength > 2 ) {
					writer.write( "\tInstrument " + instIdx + " Name \"" + name + "\"\n" );
					if( volume > 0 || fineTune > 0 ) {
						writer.write( "\t\tVolume " + instrument.getVolume() + " FineTune " + instrument.getFineTune() + "\n" );
					}
					if( sampleLength > 2 ) {
						String fileName = ( instIdx < 10 ? "0" : "" ) + instIdx + ".wav";
						writer.write( "\t\tWaveFile \"" + fileName + "\"\n" );
						AudioData audioData = new AudioData( instrument.getSampleData(), module.getC2Rate() );
						java.io.OutputStream outputStream = new java.io.FileOutputStream( new java.io.File( outDir, fileName ) );
						try {
							audioData.writeWav( outputStream, true );
						} finally {
							outputStream.close();
						}
						if( instrument.getLoopLength() > 2 ) {
							writer.write( "\t\tLoopStart " + instrument.getLoopStart() + " LoopLength " + instrument.getLoopLength() + "\n" );
						}
					}
				}
			}
			micromod.Note note = new micromod.Note();
			for( int patIdx = 0; patIdx < numPatterns; patIdx++ ) {
				micromod.Pattern pattern = module.getPattern( patIdx );
				writer.write( "\tPattern " + patIdx + "\n" );
				for( int rowIdx = 0; rowIdx < 64; rowIdx++ ) {
					writer.write( "\t\tRow \"" + ( rowIdx > 9 ? "" : "0" ) + rowIdx );
					for( int chanIdx = 0; chanIdx < numChannels; chanIdx++ ) {
						pattern.getNote( rowIdx, chanIdx, note );
						writer.write( " " + note.toString() );
					}
					writer.write( "\"\n" );
				}
			}
			writer.write( "(End.)\n" );
		} finally {
			writer.close();
		}
	}

	private static void patternToSample( java.io.File modFile, java.io.File wavFile, int pattern, int key, boolean interpolation ) throws java.io.IOException {
		micromod.Module module = new micromod.Module( new java.io.FileInputStream( modFile ) );
		module.setSequenceLength( 1 );
		module.setSequenceEntry( 0, pattern );
		int samplingRate = module.getC2Rate() * 428 / micromod.Note.keyToPeriod( key, 0 );
		micromod.Micromod replay = new micromod.Micromod( module, samplingRate );
		replay.setInterpolation( interpolation );
		int outLen = replay.calculateSongDuration();
		int[] mixBuf = new int[ replay.getMixBufferLength() ];
		short[] outBuf = new short[ outLen ];
		int outIdx = 0;
		while( outIdx < outLen ) {
			int mixLen = replay.getAudio( mixBuf );
			for( int mixIdx = 0; mixIdx < mixLen; mixIdx++ ) {
				int amp = ( mixBuf[ mixIdx * 2 ] + mixBuf[ mixIdx * 2 + 1 ] ) >> 1;
				if( amp > 32767 ) {
					amp = 32767;
				}
				if( amp < -32768 ) {
					amp = -32768;
				}
				outBuf[ outIdx++ ] = ( short ) amp;
			}
		}
		if( wavFile.exists() ) {
			throw new IllegalArgumentException( "Output file already exists!" );
		}
		java.io.OutputStream outputStream = new java.io.FileOutputStream( wavFile );
		try {
			new AudioData( outBuf, samplingRate ).writeWav( outputStream, false );
		} finally {
			outputStream.close();
		}
	}

	private static String nameString( String str ) {
		int length = 0;
		char[] out = str.toCharArray();
		for( int idx = 0; idx < out.length; idx++ ) {
			char chr = out[ idx ];
			if( chr == '"' ) {
				chr = '\'';
			} else if( chr < 32 || ( chr > 126 && chr < 160 ) ) {
				chr = 32;
			}
			if( chr > 32 ) {
				length = idx + 1;
			}
			out[ idx ] = chr;
		}
		return new String( out, 0, length );
	}
}
