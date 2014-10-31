
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
			} else if( "-ext".equals( arg ) || "-out".equals( arg ) ) {
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
			extract( new java.io.File( modFile ), new java.io.File( outDir ) );
		} else {
			System.err.println( "Micromod Compiler! (c)2014 mumart@gmail.com" );
			System.err.println( "       Play: java " + Compiler.class.getName() + " input.mt [-int] [-seq 1,2,3]" );
			System.err.println( "    Compile: java " + Compiler.class.getName() + " input.mt [-out output.mod]" );
			System.err.println( "    Extract: java " + Compiler.class.getName() + " -ext input.mod [-dir outputdir]" );
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

	private static void extract( java.io.File modFile, java.io.File outDir ) throws java.io.IOException {
		if( outDir.exists() ) {
			throw new IllegalArgumentException( "Output directory already exists!" );
		}
		outDir.mkdir();
		micromod.Module module = new micromod.Module( new java.io.FileInputStream( modFile ) );
		for( int instIdx = 1; instIdx < 32; instIdx++ ) {
			micromod.Instrument instrument = module.getInstrument( instIdx );
			AudioData audioData = new AudioData( instrument.getSampleData(), module.getC2Rate() );
			String fileName = ( instIdx < 10 ? "0" : "" ) + instIdx + ".wav";
			if( audioData.getLength() > 1 ) {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( new java.io.File( outDir, fileName ) );
				try {
					audioData.writeWav( outputStream, true );
				} finally {
					outputStream.close();
				}
			}
		}
	}
}
