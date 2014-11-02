
package micromod.compiler;

/* Compiles textual MT files to Protracker MOD files. */
public class Compiler {
	public static void main( String[] args ) throws java.io.IOException {
		String mtFile = null, modFile = null, outDir = null;
		boolean interpolation = false;
		int[] sequence = null;
		int argsIdx = 0;
		while( argsIdx < args.length ) {
			String arg = args[ argsIdx++ ];
			if( "-dir".equals( arg ) ) {
				outDir = args[ argsIdx++ ];
			} else if( "-int".equals( arg ) ) {
				interpolation = true;
			} else if( "-dec".equals( arg ) || "-out".equals( arg ) ) {
				modFile = args[ argsIdx++ ];
			} else if( "-seq".equals( arg ) ) {
				sequence = Parser.parseIntegerArray( args[ argsIdx++ ] );
			} else {
				mtFile = arg;
			}
		}
		if( mtFile != null ) {
			if( modFile != null ) {
				convert( new java.io.File( mtFile ), new java.io.File( modFile ) );
			} else {
				play( new java.io.File( mtFile ), sequence, interpolation );
			}
		} else if( modFile != null ) {
			if( outDir == null ) {
				outDir = modFile;
				if( outDir.length() > 4 && ".mod".equals( outDir.substring( outDir.length() - 4 ).toLowerCase() ) ) {
					outDir = outDir.substring( 0, outDir.length() - 4 );
				}
				outDir = outDir + ".ins";
			}
			decompile( new micromod.Module( new java.io.FileInputStream( modFile ) ), new java.io.File( outDir ) );
		} else {
			System.err.println( "Micromod Compiler! (c)2014 mumart@gmail.com" );
			System.err.println( "       Play: java " + Compiler.class.getName() + " input.mt [-int] [-seq 1,2,3]" );
			System.err.println( "    Compile: java " + Compiler.class.getName() + " input.mt [-out output.mod]" );
			System.err.println( "  Decompile: java " + Compiler.class.getName() + " -dec input.mod [-dir outputdir]" );
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
