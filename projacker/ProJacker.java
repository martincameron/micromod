
package projacker;

/* A simple text format for Protracker MOD files. */
public class ProJacker {
	public static void main( String[] args ) throws Exception {
		if( args.length > 0 ) {
			java.io.File inputFile = new java.io.File( args[ 0 ] );
			Module module = new Module( inputFile.getParentFile() );
			Parser.parse( new java.io.InputStreamReader( new java.io.FileInputStream( inputFile ) ), module );
			if( args.length > 1 ) {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] );
				try{
					outputStream.write( module.getModule().save() );
				} finally {
					outputStream.close();
				}
			} else {
				micromod.Player player = new micromod.Player( module.getModule(), false, false );
				System.out.println( player.getModuleInfo() );
				Thread thread = new Thread( player );
				thread.start();
				thread.join();
			}
		} else {
			System.err.println( "Usage java " + ProJacker.class.getName() + " input.pj output.mod" );
		}
	}
}
