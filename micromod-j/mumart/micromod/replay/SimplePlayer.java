
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
		
		// Initialise Player.
		Player player = new Player( module_data, SAMPLING_RATE, 0 );

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
		int duration = player.get_song_duration();
		System.out.println( "Song length: " + duration / SAMPLING_RATE + " seconds." );
		
		// Application will exit when song finishes.
		short[] mix_buf = new short[ MIX_BUF_LEN * 2 ];
		byte[] out_buf = new byte[ MIX_BUF_LEN * 4 ];
		AudioFormat audio_format = new AudioFormat( SAMPLING_RATE, 16, 2, true, false );
		SourceDataLine audio_line = AudioSystem.getSourceDataLine( audio_format );
		audio_line.open();
		audio_line.start();
		int sample_pos = 0;
		boolean song_end = false;
		while( !song_end ) {
			// Calculate number of samples to write.
			int mix_len = duration - sample_pos;
			if( mix_len > MIX_BUF_LEN ) mix_len = MIX_BUF_LEN;
			// Get audio and write to output device.
			player.get_audio( mix_buf, 0, mix_len );
			int out_idx = 0;
			int mix_idx = 0, mix_end = mix_len * 2;
			while( mix_idx < mix_end ) {
				int out = mix_buf[ mix_idx++ ];
				out_buf[ out_idx++ ] = ( byte ) ( out & 0xFF );
				out_buf[ out_idx++ ] = ( byte ) ( out >> 8 );
			}
			audio_line.write( out_buf, 0, out_idx );
			// Exit if song finished.
			sample_pos += mix_len;
			if( sample_pos >= duration ) song_end = true;
		}
		audio_line.drain();
		audio_line.close();
	}
}
