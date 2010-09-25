
package mumart.micromod.replay;

import java.io.*;
import javax.sound.sampled.*;

/*
	A simple command-line player using the JavaSound API.
*/
public class SimplePlayer {
	public static void main( String[] args ) throws Exception {
		final int SAMPLING_RATE = 48000;
		final int MIX_BUF_LEN = SAMPLING_RATE / 4;
	
		if( args.length != 1 ) {
			System.out.println( "Please specify a module file to play." );
			System.exit( 0 );
		}
		
		// Load data into array.
		File mod_file = new File( args[ 0 ] );
		byte[] module_data = new byte[ ( int ) mod_file.length() ];
		DataInputStream data_stream = new DataInputStream( new FileInputStream( mod_file ) );
		data_stream.readFully( module_data );
		data_stream.close();
		
		// Initialise Player to play once.
		Player player = new Player( module_data, SAMPLING_RATE, false );

		// Print the player version.
		System.out.println( "Replay version: " + player.get_version() );

		// Print the song and instrument names.
		System.out.println( "Song name: " + player.get_string( 0 ) );
		int idx = 1;
		String text = player.get_string( idx );
		while( text != null ) {
			if( !"".equals( text.trim() ) )
				System.out.println( String.format( "%1$3d ", idx ) + text );
			text = player.get_string( ++idx );
		}
		
		// Print the duration.
		System.out.println( "Song length: " + player.get_song_duration() / SAMPLING_RATE + " seconds." );
		
		// Application will exit when song finishes.
		short[] mix_buf = new short[ MIX_BUF_LEN * 2 ];
		byte[] out_buf = new byte[ MIX_BUF_LEN * 4 ];
		AudioFormat audio_format = new AudioFormat( SAMPLING_RATE, 16, 2, true, false );
		SourceDataLine audio_line = AudioSystem.getSourceDataLine( audio_format );
		audio_line.open();
		audio_line.start();
		boolean playing = true;
		while( playing ) {
			playing = player.get_audio( mix_buf, 0, MIX_BUF_LEN );
			int out_idx = 0;
			int mix_idx = 0, mix_end = MIX_BUF_LEN * 2;
			while( mix_idx < mix_end ) {
				int out = mix_buf[ mix_idx++ ];
				out_buf[ out_idx++ ] = ( byte ) ( out & 0xFF );
				out_buf[ out_idx++ ] = ( byte ) ( out >> 8 );
			}
			audio_line.write( out_buf, 0, out_idx );
		}
		audio_line.drain();
		audio_line.close();
	}
}
