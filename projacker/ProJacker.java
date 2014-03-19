
package projacker;

/* A simple text format for Protracker MOD files. */
public class ProJacker {
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
			Module module = new Module();
			Parser.parse( new java.io.InputStreamReader( new java.io.FileInputStream( args[ 0 ] ) ), module );
			if( args.length > 1 ) {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] );
				try{
					outputStream.write( module.getModule().save() );
				} finally {
					outputStream.close();
				}
			} else {
				play( module.getModule() );
			}
		} else {
			System.err.println( "Usage java " + ProJacker.class.getName() + " input.pj output.mod" );
		}
	}
}
