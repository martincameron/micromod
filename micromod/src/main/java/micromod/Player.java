
package micromod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class Player implements Runnable {
	public static final int SAMPLE_RATE = 48000;
	private Module module;
	private Micromod micromod;
	private boolean playing, loop;
	private int duration;

	public Player( Module module, boolean interpolation, boolean loop ) {
		this.module = module;
		micromod = new Micromod( module, SAMPLE_RATE );
		micromod.setInterpolation( interpolation );
		duration = micromod.calculateSongDuration();
		this.loop = loop;
	}

	public int getDuration() {
		return duration;
	}

	public String getModuleInfo() {
		StringBuffer output = new StringBuffer();
		output.append( "Micromod " + Micromod.VERSION + '\n' );
		output.append( "Song name: " + module.getSongName() + '\n' );
		for( int idx = 1; idx <= 31; idx++ ) {
			String name = module.getInstrument( idx ).getName();
			if( name.trim().length() > 0 ) {
				output.append( String.format( "%1$3d ", idx ) + name + '\n' );
			}
		}
		output.append( "Song length: " + ( duration / SAMPLE_RATE ) + " seconds." );
		return output.toString();
	}

	public void stop() {
		playing = false;
	}
	
	public void run() {
		try {
			int[] mixBuf = new int[ micromod.getMixBufferLength() ];
			byte[] outBuf = new byte[ mixBuf.length * 4 ];
			AudioFormat audioFormat = new AudioFormat( SAMPLE_RATE, 16, 2, true, true );
			SourceDataLine audioLine = AudioSystem.getSourceDataLine( audioFormat );
			try {
				audioLine.open();
				audioLine.start();
				int samplePos = 0;
				playing = true;
				while( playing && ( samplePos < duration || loop ) ) {
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
			} finally {
				audioLine.close();
			}
		} catch( Exception e ) {
		}
	}

	public static void main( String[] args ) throws java.io.IOException {
		// Parse arguments.
		String modFile = "";
		boolean interpolation = false;
		if( args.length == 2 && "-int".equals( args[ 0 ] ) ) {
			interpolation = true;
			modFile = args[ 1 ];
		} else if( args.length == 1 ) {
			modFile = args[ 0 ];
		} else {
			System.err.println( "Micromod Java ProTracker Replay " + Micromod.VERSION );
			System.err.println( "Usage: java " + Player.class.getName() + " [-int] modfile" );
			System.exit( 1 );
		}
		// Load and play module.
		Module module = new Module( new java.io.FileInputStream( modFile ) );
		Player player = new Player( module, interpolation, false );
		System.out.println( player.getModuleInfo() );
		Thread thread = new Thread( player );
		thread.start();
		try {
			thread.join();
		} catch( InterruptedException e ) {
			System.err.println( "Interrupted!" );
		}
	}
}
