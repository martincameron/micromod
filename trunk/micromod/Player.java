
package micromod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class Player {
	public static final int SAMPLE_RATE = 48000;

	public static void main( String[] args ) throws Exception {
		// Parse arguments.
		File modFile = null;
		boolean interpolation = false;
		if( args.length == 2 && "-int".equals( args[ 0 ] ) ) {
			interpolation = true;
			modFile = new File( args[ 1 ] );
		} else if( args.length == 1 ) {
			modFile = new File( args[ 0 ] );
		} else {
			System.err.println( "Micromod Java ProTracker Replay " + Micromod.VERSION );
			System.err.println( "Usage: java " + Player.class.getName() + " [-int] modfile" );
			System.exit( 1 );
		}
		// Load module.
		byte[] moduleData = new byte[ ( int ) modFile.length() ];
		FileInputStream inputStream = new FileInputStream( modFile );
		int offset = 0;
		while( offset < moduleData.length ) {
			int len = inputStream.read( moduleData, offset, moduleData.length - offset );
			if( len < 0 ) throw new IOException( "Unexpected end of file." );
			offset += len;
		}
		inputStream.close();
		Module module = new Module( moduleData );
		moduleData = null;
		// Initialise Micromod.
		Micromod micromod = new Micromod( module, SAMPLE_RATE );
		micromod.setInterpolation( interpolation );
		// Print some info.
		System.out.println( "Micromod " + Micromod.VERSION );
		System.out.println( "Song name: " + module.songName );
		for( int idx = 1; idx < module.instruments.length; idx++ ) {
			String name = module.instruments[ idx ].name;
			if( name.trim().length() > 0 )
				System.out.println( String.format( "%1$3d ", idx ) + name );
		}
		int duration = micromod.calculateSongDuration();
		System.out.println( "Song length: " + duration / SAMPLE_RATE + " seconds." );
		// Application will exit when song finishes.
		int[] mixBuf = new int[ micromod.getMixBufferLength() ];
		byte[] outBuf = new byte[ mixBuf.length * 4 ];
		AudioFormat audioFormat = new AudioFormat( SAMPLE_RATE, 16, 2, true, true );
		SourceDataLine audioLine = AudioSystem.getSourceDataLine( audioFormat );
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
}
