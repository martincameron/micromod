
package micromod.tracker;

/* A simple command-line "tracker" for Protracker MOD files. */
public class Tracker {
	public static void main( String[] args ) throws java.io.IOException {
		if( args.length > 0 ) {
			java.io.File inputFile = new java.io.File( args[ 0 ] );
			if( args.length > 1 ) {
				java.io.File outputFile = new java.io.File( args[ 1 ] );
				if( outputFile.exists() ) {
					System.err.println( "Output file already exists!" );
				} else {
					java.io.OutputStream outputStream = new java.io.FileOutputStream( outputFile );
					try{
						outputStream.write( convert( inputFile ).getModule().save() );
					} finally {
						outputStream.close();
					}
				}
			} else {
				micromod.Player player = new micromod.Player( convert( inputFile ).getModule(), false, false );
				System.out.println( player.getModuleInfo() );
				Thread thread = new Thread( player );
				thread.start();
				try {
					thread.join();
				} catch( InterruptedException e ) {
				}
			}
		} else {
			System.err.println( "Usage java " + Tracker.class.getName() + " input.mt output.mod" );
		}
	}

	private static Module convert( java.io.File inputFile ) throws java.io.IOException {
		Module module = new Module( inputFile.getParentFile() );
		Parser.parse( new java.io.InputStreamReader( new java.io.FileInputStream( inputFile ) ), module );
		return module;
	}
}
