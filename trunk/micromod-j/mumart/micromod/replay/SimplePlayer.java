
package mumart.micromod.replay;

import java.io.*;
import javax.sound.sampled.*;

public class SimplePlayer implements Runnable {
	private static final int OVERSAMPLE = 2;

	private Replay replay;
	private boolean playing, loop;
	private int sampling_rate, duration, sample_pos;
	private int filt_l, filt_r;

	public SimplePlayer( byte[] module_data, int sampling_rate, boolean loop ) {
		this.sampling_rate = sampling_rate;
		this.loop = loop;
		replay = init_replay( module_data, sampling_rate * OVERSAMPLE );
		duration = replay.calculate_song_duration();
	}

	public String get_version() {
		return replay.get_version();
	}

	public String get_string( int idx ) {
		return replay.get_string( idx );
	}

	public int get_duration() {
		return duration / OVERSAMPLE;
	}

	public void stop() {
		playing = false;
	}

	public void run() {
		try {
			int[] mix_buf = new int[ replay.get_mix_buffer_length() ];
			byte[] out_buf = new byte[ mix_buf.length * 2 ];
			AudioFormat audio_format = new AudioFormat( sampling_rate, 16, 2, true, false );
			SourceDataLine audio_line = AudioSystem.getSourceDataLine( audio_format );
			audio_line.open();
			audio_line.start();
			playing = true;
			while( playing && sample_pos < duration ) {
				// Get audio from replay.
				int count = replay.get_audio( mix_buf );
				downsample( mix_buf, count );
				// Write to output.
				int out_len = count * 4 / OVERSAMPLE;
				for( int out_idx = 0; out_idx < out_len; out_idx += 2 ) {
					int out = mix_buf[ out_idx >> 1 ];
					if( out >  32767 ) out =  32767;
					if( out < -32768 ) out = -32768;
					out_buf[ out_idx     ] = ( byte ) ( out & 0xFF );
					out_buf[ out_idx + 1 ] = ( byte ) ( out >> 8 );
				}
				audio_line.write( out_buf, 0, out_len );
				// Update playback position and decide whether to loop.
				sample_pos += count;
				if( sample_pos >= duration && loop ) sample_pos = 0;
			}
			audio_line.drain();
			audio_line.close();
		} catch( Exception e ) {
			System.err.println( e );
		}
	}

	/*
		2:1 downsampling with simple but effective anti-aliasing.
		Count is the number of stereo samples to process, and must be even.
	*/
	private void downsample( int[] buf, int count ) {
		int in_idx = 0, out_idx = 0;
		while( out_idx < count ) {	
			int out_l = filt_l + ( buf[ in_idx++ ] >> 1 );
			int out_r = filt_r + ( buf[ in_idx++ ] >> 1 );
			filt_l = buf[ in_idx++ ] >> 2;
			filt_r = buf[ in_idx++ ] >> 2;
			buf[ out_idx++ ] = out_l + filt_l;
			buf[ out_idx++ ] = out_r + filt_r;
		}
	}

	private Replay init_replay( byte[] module_data, int sampling_rate ) {
		try {
			// Try loading as an XM.
			mumart.micromod.xm.Module module = new mumart.micromod.xm.Module( module_data );
			// XMs generally sound better with interpolation, Mods and S3Ms generally without.
			return new mumart.micromod.xm.IBXM( module, sampling_rate, true );
		} catch( IllegalArgumentException e ) {}
		try {
			// Not an XM, try as an S3M.
			mumart.micromod.s3m.Module module = new mumart.micromod.s3m.Module( module_data );
			return new mumart.micromod.s3m.Micros3m( module, sampling_rate, false );
		} catch( IllegalArgumentException e ) {}
		// Must be a MOD ...
		mumart.micromod.mod.Module module = new mumart.micromod.mod.Module( module_data );
		return new mumart.micromod.mod.Micromod( module, sampling_rate, false );
	}

	public static void main( String[] args ) throws Exception {
		final int SAMPLING_RATE = 48000;
	
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
		
		// Initialise SimplePlayer to play once.
		SimplePlayer player = new SimplePlayer( module_data, SAMPLING_RATE, false );

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
		System.out.println( "Song length: " + player.get_duration() / SAMPLING_RATE + " seconds." );
		
		// Application will exit when song finishes.
		new Thread( player ).start();
	}
}
