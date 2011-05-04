
package mumart.micromod.replay;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Mod2Wav {
	public static final int SAMPLING_RATE = 48000;
	private static final int MIX_BUF_SAMPLES = 65536;

	public static void main( String[] args ) throws IOException {
		if( args.length == 2 ) {
			// Load data into array.
			File mod_file = new File( args[ 0 ] );
			byte[] module_data = new byte[ ( int ) mod_file.length() ];
			DataInputStream data_stream = new DataInputStream( new FileInputStream( mod_file ) );
			data_stream.readFully( module_data );
			data_stream.close();
			// Write WAV file to output.
			FileOutputStream out = new FileOutputStream( args[ 1 ] );
			Player player = new Player( module_data, SAMPLING_RATE, 0 );
			writeWav( player, out );
			out.close();
		} else {
			System.err.println( "Usage: Mod2Wav input.mod output.wav" );
		}
	}
	
	/*
		Write the entire song within the specified Player to a WAV-format audio stream.
	*/
	public static void writeWav( Player player, OutputStream out ) throws IOException {
		int duration = player.get_song_duration();
		// Write WAV header.
		writeASCII( out, "RIFF" );
		writeInt32( out, duration * 4 + 36 );
		writeASCII( out, "WAVE" );
		writeASCII( out, "fmt " );
		writeInt32( out, 16 );
		writeUInt16( out, 1 );
		writeUInt16( out, 2 );
		writeInt32( out, player.get_sampling_rate() );
		writeInt32( out, player.get_sampling_rate() * 4 );
		writeUInt16( out, 4 );
		writeUInt16( out, 16 );
		writeASCII( out, "data" );
		writeInt32( out, duration * 4 );
		// Write song.
		short[] mix_buf = new short[ MIX_BUF_SAMPLES * 2 ];
		byte[] out_buf = new byte[ MIX_BUF_SAMPLES * 4 ];
		player.seek( 0 );
		while( duration > 0 ) {
			int count = duration;
			if( count > MIX_BUF_SAMPLES ) count = MIX_BUF_SAMPLES;
			player.get_audio( mix_buf, 0, count );
			int out_idx = 0, mix_idx = 0, mix_end = count * 2;
			while( mix_idx < mix_end ) {
				int ampl = mix_buf[ mix_idx++ ];
				out_buf[ out_idx++ ] = ( byte ) ampl;
				out_buf[ out_idx++ ] = ( byte ) ( ampl >> 8 );
			}
			out.write( out_buf, 0, count * 4 );
			duration -= count;
		}
		out.flush();
	}
	
	private static void writeASCII( OutputStream out, String text ) throws IOException {
		byte[] buf = text.getBytes( "US-ASCII" );
		out.write( buf );
	}
	
	private static void writeUInt16( OutputStream out, int value ) throws IOException {
		out.write( ( byte ) value );
		out.write( ( byte ) ( value >> 8 ) );
	}
	
	private static void writeInt32( OutputStream out, int value ) throws IOException {
		out.write( ( byte ) value );
		out.write( ( byte ) ( value >> 8 ) );
		out.write( ( byte ) ( value >> 16 ) );
		out.write( ( byte ) ( value >> 24 ) );
	}
}
