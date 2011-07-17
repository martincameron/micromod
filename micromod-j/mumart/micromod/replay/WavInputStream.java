
package mumart.micromod.replay;

import java.io.InputStream;

/*
	An InputStream that produces 16-bit WAV audio data from a Replay.
	J2ME: java.microedition.media.Manager.createPlayer( wavInputStream, "audio/x-wav" ).start();
*/
public class WavInputStream extends InputStream {
	private static final byte[] header = {
		0x52, 0x49, 0x46, 0x46, /* "RIFF" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size + 36 */
		0x57, 0x41, 0x56, 0x45, /* "WAVE" */
		0x66, 0x6D, 0x74, 0x20, /* "fmt " */
		0x10, 0x00, 0x00, 0x00, /* 16 (bytes to follow) */
		0x01, 0x00, 0x02, 0x00, /* 1 (pcm), 2 (stereo) */
		0x00, 0x00, 0x00, 0x00, /* Sample rate */
		0x00, 0x00, 0x00, 0x00, /* Sample rate * 4 */
		0x04, 0x00, 0x10, 0x00, /* 4 (bytes/frame), 16 (bits) */
		0x64, 0x61, 0x74, 0x61, /* "data" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size */
	};

	private Replay replay;
	private int[] mixBuf;
	private byte[] outBuf;
	private int outIdx, outLen;

	public WavInputStream( Replay replay ) {
		this.replay = replay;
		mixBuf = new int[ replay.get_mix_buffer_length() ];
		outBuf = new byte[ mixBuf.length * 4 ];
		int duration = replay.calculate_song_duration();
		int samplingRate = replay.get_sampling_rate();
		System.arraycopy( header, 0, outBuf, 0, header.length );
		writeInt32( outBuf, 4, duration * 4 + 36 );
		writeInt32( outBuf, 24, samplingRate );
		writeInt32( outBuf, 28, samplingRate * 4 );
		writeInt32( outBuf, 40, duration * 4 );
		outIdx = 0;
		outLen = 44;
	}

	public int read() {
		int out = outBuf[ outIdx++ ];
		if( outIdx >= outLen ) {
			getAudio();
		}
		return out;
	}

	public int read( byte[] buf, int off, int len ) {
		int remain = outLen - outIdx;
		if( len > remain ) {
			len = remain;
		}
		System.arraycopy( outBuf, outIdx, buf, off, len );
		outIdx += len;
		if( outIdx >= outLen ) {
			getAudio();
		}
		return len;
	}

	private void getAudio() {
		int mEnd = replay.get_audio( mixBuf ) * 2;
		for( int mIdx = 0, oIdx = 0; mIdx < mEnd; mIdx++ ) {
			int ampl = mixBuf[ mIdx ];
			outBuf[ oIdx++ ] = ( byte ) ampl;
			outBuf[ oIdx++ ] = ( byte ) ( ampl >> 8 );
		}
		outIdx = 0;
		outLen = mEnd * 2;
	}

	private static void writeInt32( byte[] buf, int idx, int value ) {
		buf[ idx ] = ( byte ) value;
		buf[ idx + 1 ] = ( byte ) ( value >> 8 );
		buf[ idx + 2 ] = ( byte ) ( value >> 16 );
		buf[ idx + 3 ] = ( byte ) ( value >> 24 );
	}

	/* Simple Mod to Wav converter for testing. */
	public static void main( String[] args ) throws java.io.IOException {
		if( args.length == 2 ) {
			// Load data into array.
			java.io.File modFile = new java.io.File( args[ 0 ] );
			byte[] buf = new byte[ ( int ) modFile.length() ];
			InputStream in = new java.io.FileInputStream( modFile );
			int idx = 0;
			while( idx < buf.length ) {
				idx += in.read( buf, idx, buf.length - idx );
			}
			in.close();
			// Write WAV file to output.
			java.io.OutputStream out = new java.io.FileOutputStream( args[ 1 ] );
			Replay replay = Player.init_replay( buf, 48000, 2 );
			in = new WavInputStream( replay );
			buf = new byte[ replay.get_mix_buffer_length() * 4 ];
			int remain = replay.calculate_song_duration() * 4 + header.length;
			while( remain > 0 ) {
				int count = remain > buf.length ? buf.length : remain;
				count = in.read( buf, 0, count );
				out.write( buf, 0, count );
				remain -= count;
			}		
			out.close();
		} else {
			System.err.println( "Usage: input.mod output.wav" );
		}
	}
}
